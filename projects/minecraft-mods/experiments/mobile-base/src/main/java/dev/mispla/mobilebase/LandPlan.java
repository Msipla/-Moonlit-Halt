package dev.mispla.mobilebase;

import dev.mispla.mobilebase.BoatMarkerItem.Marks;
import dev.mispla.mobilebase.BoatPlan.Rejected;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import org.valkyrienskies.eureka.block.ShipHelmBlock;

record LandPlan(Map<BlockPos, BlockState> hull, Map<BlockPos, BlockState> parts, Direction heading, int rpm) {
    private static final ResourceLocation WHEEL = new ResourceLocation("trackwork", "med_simple_wheel");
    private static final ResourceLocation MOTOR = new ResourceLocation("create", "creative_motor");

    static boolean available() {
        return FabricLoader.getInstance().isModLoaded("create") && FabricLoader.getInstance().isModLoaded("trackwork");
    }

    static BlockState wheelPreview(Direction heading) {
        var rim = BuiltInRegistries.BLOCK.get(new ResourceLocation("trackwork", "med_simple_wheel_part")).defaultBlockState();
        if (!rim.hasProperty(BlockStateProperties.AXIS)) return net.minecraft.world.level.block.Blocks.BLACK_CONCRETE.defaultBlockState();
        return rim.setValue(BlockStateProperties.AXIS, heading.getClockWise().getAxis());
    }

    static boolean isWheel(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(WHEEL);
    }

    static boolean isMotor(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(MOTOR);
    }

    static boolean supported(BlockState state) {
        return state.is(MobileBase.LAND_CORE) || isWheel(state) || isMotor(state) || BoatPlan.supported(state);
    }

    static LandPlan scan(ServerLevel level, BlockPos core, Direction facing, Marks marks) throws Rejected {
        if (!available()) throw new Rejected("land_deps");
        if (marks.engine() != null || marks.floater() != null || marks.balloon() != null) throw new Rejected("land_marks");
        var hull = BoatPlan.scanHull(level, core, LandPlan::supported);
        var parts = new LinkedHashMap<BlockPos, BlockState>();
        var helm = BoatPlan.part("oak_ship_helm");
        var anchor = BoatPlan.part("anchor");
        var existing = hull.entrySet().stream()
            .filter(entry -> entry.getValue().getBlock() instanceof ShipHelmBlock).toList();
        if (existing.size() > 1) throw new Rejected("multiple_helms");
        var heading = marks.heading() == null ? facing : marks.heading();
        var anchorPosition = core.relative(facing.getClockWise());
        if (!existing.isEmpty()) {
            var placement = existing.get(0);
            var installedHeading = placement.getValue().getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
            if (marks.heading() != null && marks.heading() != installedHeading
                    || marks.helm() != null && !marks.helm().equals(placement.getKey())) {
                throw new Rejected("marked_installed");
            }
            heading = installedHeading;
        }
        var seatDirection = heading.getOpposite();
        var seatFor = existing.isEmpty() ? marks.helm() : existing.get(0).getKey();
        if (seatFor == null) {
            seatFor = hull.keySet().stream().map(BlockPos::above)
                .filter(pos -> clear(level, core, pos) && seatBay(level, core, hull, pos, seatDirection, anchorPosition))
                .min(java.util.Comparator.<BlockPos>comparingDouble(core::distSqr).thenComparingLong(BlockPos::asLong))
                .orElseThrow(() -> new Rejected("helm_space"));
        }
        if (!seatBay(level, core, hull, seatFor, seatDirection, anchorPosition)) throw new Rejected("marked_helm");
        if (existing.isEmpty()) {
            put(level, core, hull, parts, seatFor, helm.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, seatDirection));
        }
        if (!hull.containsKey(anchorPosition.below()) || level.hasNeighborSignal(anchorPosition)
                || hull.entrySet().stream().anyMatch(e -> e.getValue().is(anchor) && !e.getKey().equals(anchorPosition))) {
            throw new Rejected("anchor");
        }
        put(level, core, hull, parts, anchorPosition, anchor.defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, facing.getClockWise())
            .setValue(BlockStateProperties.POWERED, false));

        var wheel = BuiltInRegistries.BLOCK.get(WHEEL);
        var motor = BuiltInRegistries.BLOCK.get(MOTOR);
        var axles = chooseAxles(level, core, heading, hull, marks, wheel, motor);
        for (var axle : axles) {
            for (var component : axle.entrySet()) {
                put(level, core, hull, parts, component.getKey(), component.getValue());
            }
        }
        var chassis = new java.util.HashSet<BlockPos>();
        axles.forEach(axle -> chassis.addAll(axle.keySet()));
        for (var entry : hull.entrySet()) {
            if ((isWheel(entry.getValue()) || isMotor(entry.getValue())) && !chassis.contains(entry.getKey())) {
                throw new Rejected("land_extra", entry.getKey().toShortString());
            }
        }
        if (parts.size() + hull.size() > BoatPlan.MAX_BLOCKS) throw new Rejected("boundary");
        double mass = hull.values().stream().mapToDouble(BoatPlan::massOf).sum()
            + parts.values().stream().mapToDouble(BoatPlan::massOf).sum();
        if (!Double.isFinite(mass) || mass <= 0) throw new Rejected("mass");
        int rpm = net.minecraft.util.Mth.clamp(24 + (int) (mass / 18000), 24, 56);
        return new LandPlan(Map.copyOf(hull), Map.copyOf(parts), heading, rpm);
    }

    private static boolean seatBay(ServerLevel level, BlockPos core, Map<BlockPos, BlockState> hull, BlockPos helm,
                                   Direction back, BlockPos anchor) {
        if (!hull.containsKey(helm.below()) || helm.equals(anchor)) return false;
        for (int i = 1; i <= 2; i++) {
            var pos = helm.relative(back, i);
            if (pos.equals(anchor) || !clear(level, core, pos) || !clear(level, core, pos.above())
                    || !hull.containsKey(pos.below())) return false;
        }
        return true;
    }

    private static List<Map<BlockPos, BlockState>> chooseAxles(ServerLevel level, BlockPos core, Direction heading,
            Map<BlockPos, BlockState> hull, Marks marks, Block wheel, Block motor) throws Rejected {
        var origin = core.below(2);
        var side = heading.getClockWise();
        for (int width = 2; width <= 8; width++) {
            var fronts = new java.util.ArrayList<Integer>();
            var rears = new java.util.ArrayList<Integer>();
            for (int offset = -12; offset <= 12; offset++) {
                var center = origin.relative(heading, offset);
                var axle = axle(level, core, hull, center, side, width, wheel, motor);
                if (axle.isEmpty()) continue;
                if (matches(axle, marks.frontWheel())) fronts.add(offset);
                if (matches(axle, marks.rearWheel())) rears.add(offset);
            }
            for (int i = fronts.size() - 1; i >= 0; i--) {
                for (int back : rears) {
                    int front = fronts.get(i);
                    if (front - back < 3) break;
                    return List.of(
                        axle(level, core, hull, origin.relative(heading, front), side, width, wheel, motor),
                        axle(level, core, hull, origin.relative(heading, back), side, width, wheel, motor));
                }
            }
        }
        throw new Rejected("land_wheels");
    }

    private static boolean matches(Map<BlockPos, BlockState> axle, BlockPos position) {
        return position == null || axle.containsKey(position) && isWheel(axle.get(position));
    }

    private static Map<BlockPos, BlockState> axle(ServerLevel level, BlockPos core, Map<BlockPos, BlockState> hull,
                                                  BlockPos center, Direction side, int width, Block wheel, Block motor) {
        var planned = new LinkedHashMap<BlockPos, BlockState>();
        for (var outward : List.of(side, side.getOpposite())) {
            var tire = center.relative(outward, width);
            var drive = tire.relative(outward.getOpposite());
            var tireBlock = wheel.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, outward.getOpposite());
            var motorBlock = motor.defaultBlockState().setValue(BlockStateProperties.FACING, outward);
            if (!hull.containsKey(drive.above()) || hull.containsKey(tire.above())
                    || !canPlace(level, core, hull, tire, tireBlock) || !canPlace(level, core, hull, drive, motorBlock)
                    || !clear(level, core, tire.below()) || !clear(level, core, drive.below())) return Map.of();
            planned.put(tire, tireBlock);
            planned.put(drive, motorBlock);
        }
        return planned;
    }

    private static boolean clear(ServerLevel level, BlockPos core, BlockPos pos) {
        return BoatPlan.inRange(level, core, pos) && BoatPlan.loaded(level, pos) && level.getBlockState(pos).isAir();
    }

    private static boolean canPlace(ServerLevel level, BlockPos core, Map<BlockPos, BlockState> hull,
                                    BlockPos pos, BlockState value) {
        return hull.get(pos) == value || clear(level, core, pos) && level.getBlockEntity(pos) == null
            && level.getEntities(null, new AABB(pos)).isEmpty();
    }

    private static void put(ServerLevel level, BlockPos core, Map<BlockPos, BlockState> hull,
                            Map<BlockPos, BlockState> parts, BlockPos pos, BlockState value) throws Rejected {
        if (!canPlace(level, core, hull, pos, value) || parts.containsKey(pos)) {
            throw new Rejected("occupied_at", pos.toShortString());
        }
        if (!hull.containsKey(pos)) parts.put(pos, value);
    }
}
