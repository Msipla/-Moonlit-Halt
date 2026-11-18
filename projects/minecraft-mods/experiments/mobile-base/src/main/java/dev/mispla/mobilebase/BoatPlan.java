package dev.mispla.mobilebase;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import org.valkyrienskies.eureka.block.ShipHelmBlock;
import org.valkyrienskies.mod.common.BlockStateInfo;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

record BoatPlan(Map<BlockPos, BlockState> hull, Map<BlockPos, BlockState> parts, double mass) {
    static final int MAX_BLOCKS = 512;
    private static final int RADIUS = 12;
    private static final double MAX_MASS = 80_000;

    static BoatPlan scan(ServerLevel level, BlockPos core, Direction heading) throws Rejected {
        Map<BlockPos, BlockState> hull = new LinkedHashMap<>();
        Set<BlockPos> visited = new HashSet<>();
        var queue = new ArrayDeque<BlockPos>();
        queue.add(core);
        while (!queue.isEmpty()) {
            var pos = queue.remove();
            if (!visited.add(pos)) continue;
            if (!loaded(level, pos)) throw new Rejected("unloaded");
            var state = level.getBlockState(pos);
            if (state.isAir() || state.getBlock() instanceof LiquidBlock) continue;
            if (Math.abs(pos.getX() - core.getX()) > RADIUS || Math.abs(pos.getY() - core.getY()) > RADIUS
                    || Math.abs(pos.getZ() - core.getZ()) > RADIUS || hull.size() >= MAX_BLOCKS) {
                throw new Rejected("boundary");
            }
            if ((state.is(MobileBase.CORE) && !pos.equals(core)) || !supported(state)
                    || VSGameUtilsKt.getShipManagingPos(level, pos) != null) {
                throw new Rejected("unsupported", pos.toShortString());
            }
            hull.put(pos, state);
            for (Direction direction : Direction.values()) queue.add(pos.relative(direction));
        }

        var parts = new LinkedHashMap<BlockPos, BlockState>();
        double hullMass = hull.values().stream().mapToDouble(BoatPlan::massOf).sum();
        Comparator<BlockPos> nearCore = Comparator.comparingDouble(core::distSqr);
        var deck = hull.entrySet().stream()
            .filter(e -> e.getValue().isFaceSturdy(level, e.getKey(), Direction.UP))
            .map(e -> e.getKey().above()).filter(pos -> freeAir(level, pos))
            .sorted(nearCore.thenComparingLong(BlockPos::asLong)).toList();

        Block helm = part("oak_ship_helm");
        Block engine = part("engine");
        Block anchor = part("anchor");
        Block floater = part("floater");

        long helmCount = hull.values().stream().filter(s -> s.getBlock() instanceof ShipHelmBlock).count();
        if (helmCount > 1) throw new Rejected("multiple_helms");
        if (helmCount == 0) {
            var anchorSlot = core.relative(heading.getClockWise());
            var seatDirection = heading.getOpposite();
            var place = deck.stream().filter(pos -> {
                var seat = pos.relative(seatDirection);
                return !pos.equals(anchorSlot) && !seat.equals(anchorSlot)
                    && !seat.relative(seatDirection).equals(anchorSlot)
                    && hasSeatBay(level, deck, seat, seatDirection);
            }).findFirst()
                .orElseThrow(() -> new Rejected("helm_space"));
            parts.put(place, facing(helm, heading.getOpposite()));
        } else {
            var existing = hull.entrySet().stream().filter(e -> e.getValue().getBlock() instanceof ShipHelmBlock)
                .findFirst().orElseThrow();
            var seatDirection = existing.getValue().getValue(BlockStateProperties.HORIZONTAL_FACING);
            var seat = existing.getKey().relative(seatDirection);
            if (!hasSeatBay(level, deck, seat, seatDirection)) throw new Rejected("helm_space");
        }

        var anchorPos = core.relative(heading.getClockWise());
        long anchorCount = hull.values().stream().filter(s -> s.is(anchor)).count();
        if (anchorCount > 1 || (anchorCount == 1 && !hull.getOrDefault(anchorPos, Blocks.AIR.defaultBlockState()).is(anchor))
                || level.hasNeighborSignal(anchorPos)
                || (anchorCount == 1 && hull.get(anchorPos).getValue(BlockStateProperties.POWERED))) {
            throw new Rejected("anchor");
        }
        if (anchorCount == 0) {
            if (!deck.contains(anchorPos) || parts.containsKey(anchorPos) || drivingSpace(hull, parts, anchorPos)) {
                throw new Rejected("anchor");
            }
            parts.put(anchorPos, facing(anchor, heading.getClockWise()).setValue(BlockStateProperties.POWERED, false));
        }
        if (hull.values().stream().noneMatch(s -> s.is(engine))) {
            var place = deck.stream().filter(pos -> !parts.containsKey(pos) && !level.hasNeighborSignal(pos))
                .filter(pos -> !drivingSpace(hull, parts, pos)).findFirst()
                .orElseThrow(() -> new Rejected("deck_space"));
            parts.put(place, facing(engine, heading));
        }

        // Fit floaters on underside cells at the waterline, alternating left and right.
        Map<Long, BlockPos> underside = new HashMap<>();
        for (var pos : hull.keySet()) {
            long column = ((long) pos.getX() << 32) | (pos.getZ() & 0xffffffffL);
            underside.merge(column, pos, (a, b) -> a.getY() < b.getY() ? a : b);
        }
        var wetColumns = underside.values().stream()
            .filter(pos -> hull.get(pos).isCollisionShapeFullBlock(level, pos))
            .map(BlockPos::below).filter(pos -> launchWater(level, pos))
            .sorted(nearCore.thenComparingLong(BlockPos::asLong)).toList();
        if (wetColumns.size() < 6) throw new Rejected("water");
        double outfittedMass = hullMass + parts.values().stream().mapToDouble(BoatPlan::massOf).sum();
        double floaterMass = massOf(floater.defaultBlockState());
        long availableFloaters = hull.entrySet().stream()
            .filter(e -> e.getValue().is(floater) && e.getValue().getValue(BlockStateProperties.POWER) == 0
                && !level.hasNeighborSignal(e.getKey()))
            .count();
        double massWithoutWorkingFloaters = outfittedMass - availableFloaters * floaterMass;
        int requiredFloaters = Math.max(2, (int) Math.ceil(massWithoutWorkingFloaters / 18_000));
        int missing = Math.max(0, requiredFloaters - (int) availableFloaters);
        var port = new ArrayList<BlockPos>();
        var starboard = new ArrayList<BlockPos>();
        Direction side = heading.getClockWise();
        double center = wetColumns.stream().mapToInt(p -> side.getStepX() * p.getX() + side.getStepZ() * p.getZ())
            .average().orElseThrow();
        for (var pos : wetColumns) {
            if (level.hasNeighborSignal(pos)) continue;
            double distance = side.getStepX() * pos.getX() + side.getStepZ() * pos.getZ() - center;
            (distance < 0 ? port : starboard).add(pos);
        }
        for (int i = 0; i < missing; i++) {
            var candidates = i % 2 == 0 ? port : starboard;
            if (candidates.isEmpty()) throw new Rejected("floater_space");
            parts.put(candidates.remove(0), floater.defaultBlockState());
        }

        double mass = outfittedMass + missing * floaterMass;
        if (!Double.isFinite(mass) || mass <= 0 || mass > MAX_MASS || hull.size() + parts.size() > MAX_BLOCKS) {
            throw new Rejected("mass");
        }
        for (var pos : parts.keySet()) {
            if (!level.getEntities(null, new AABB(pos)).isEmpty()) throw new Rejected("entity");
        }
        return new BoatPlan(Map.copyOf(hull), Map.copyOf(parts), mass);
    }

    static boolean loaded(ServerLevel level, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.hasChunkAt(pos);
    }

    static boolean supported(BlockState state) {
        if (VSGameUtilsKt.inAssemblyBlacklist(state) || !state.getFluidState().isEmpty()) return false;
        Block block = state.getBlock();
        if (state.is(MobileBase.CORE) || block instanceof ShipHelmBlock
                || state.is(part("engine")) || state.is(part("anchor")) || state.is(part("floater"))) {
            return true;
        }
        if (!BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("minecraft")
                || block instanceof FallingBlock) {
            return false;
        }
        return !state.hasBlockEntity() || state.is(Blocks.CHEST) || state.is(Blocks.BARREL);
    }

    private static boolean freeAir(ServerLevel level, BlockPos pos) {
        return loaded(level, pos) && level.getBlockState(pos).isAir();
    }

    private static boolean launchWater(ServerLevel level, BlockPos pos) {
        return loaded(level, pos) && loaded(level, pos.below())
            && (level.getBlockState(pos).isAir() || level.getBlockState(pos).is(Blocks.WATER))
            && level.getFluidState(pos.below()).is(FluidTags.WATER);
    }

    private static boolean hasSeatBay(ServerLevel level, List<BlockPos> deck, BlockPos seat, Direction direction) {
        var exit = seat.relative(direction);
        return deck.contains(seat) && freeAir(level, seat.above())
            && deck.contains(exit) && freeAir(level, exit.above());
    }

    private static boolean drivingSpace(Map<BlockPos, BlockState> hull, Map<BlockPos, BlockState> parts, BlockPos pos) {
        return seatIn(hull, pos) || seatIn(parts, pos) || seatIn(hull, pos.below()) || seatIn(parts, pos.below());
    }

    private static boolean seatIn(Map<BlockPos, BlockState> states, BlockPos pos) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var towardHelm = facing.getOpposite();
            var adjacent = states.get(pos.relative(towardHelm));
            var twoAway = states.get(pos.relative(towardHelm, 2));
            if (facesSeat(adjacent, facing) || facesSeat(twoAway, facing)) return true;
        }
        return false;
    }

    private static boolean facesSeat(BlockState state, Direction direction) {
        return state != null && state.getBlock() instanceof ShipHelmBlock
            && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == direction;
    }

    private static BlockState facing(Block block, Direction direction) {
        return block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, direction);
    }

    private static double massOf(BlockState state) {
        var properties = BlockStateInfo.INSTANCE.get(state);
        return properties == null ? Double.NaN : properties.getFirst();
    }

    static Block part(String path) {
        return BuiltInRegistries.BLOCK.get(new net.minecraft.resources.ResourceLocation("vs_eureka", path));
    }

    static final class Rejected extends Exception {
        final String key;
        final Object[] arguments;

        Rejected(String key, Object... arguments) {
            this.key = "mobilebase.error." + key;
            this.arguments = arguments;
        }
    }
}
