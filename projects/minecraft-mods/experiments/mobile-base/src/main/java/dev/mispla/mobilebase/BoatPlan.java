package dev.mispla.mobilebase;

import java.util.ArrayDeque;
import java.util.ArrayList;
import dev.mispla.mobilebase.BoatMarkerItem.Marks;
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

record BoatPlan(Map<BlockPos, BlockState> hull, Map<BlockPos, BlockState> parts, double mass,
                int wetColumns, int floaters, boolean adapted) {
    static final int MAX_BLOCKS = 512;
    private static final int RADIUS = 12;
    private static final double STOCK_MASS = 80_000;

    double engineScale() {
        return adapted ? Math.max(1, mass / STOCK_MASS) : 1;
    }

    static BoatPlan scan(ServerLevel level, BlockPos core, Direction coreFacing, Marks marks) throws Rejected {
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
            if (!inRange(level, core, pos) || hull.size() >= MAX_BLOCKS) {
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
            .map(e -> e.getKey().above()).filter(pos -> inRange(level, core, pos) && freeAir(level, pos))
            .sorted(nearCore.thenComparingLong(BlockPos::asLong)).toList();
        var anchorPos = core.relative(coreFacing.getClockWise());
        Block helm = part("oak_ship_helm");
        Block engine = part("engine");
        Block anchor = part("anchor");
        Block floater = part("floater");

        var helms = hull.entrySet().stream().filter(e -> e.getValue().getBlock() instanceof ShipHelmBlock).toList();
        if (helms.size() > 1) throw new Rejected("multiple_helms");
        var existing = helms.isEmpty() ? null : helms.get(0);
        Direction heading = existing != null
            ? existing.getValue().getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite()
            : marks.heading() == null ? coreFacing : marks.heading();
        if (existing != null && ((marks.helm() != null && !marks.helm().equals(existing.getKey()))
                || (marks.heading() != null && marks.heading() != heading))) {
            throw new Rejected("marked_installed");
        }
        var seatDirection = heading.getOpposite();
        if (existing == null) {
            BlockPos place = marks.helm();
            if (place == null) {
                place = deck.stream().filter(pos -> hasSeatBay(level, deck, pos, seatDirection, anchorPos))
                    .findFirst().orElse(null);
                if (place == null) {
                    place = deck.stream().filter(pos -> cockpitExtension(level, core, hull, pos, seatDirection, anchorPos) != null)
                        .findFirst().orElseThrow(() -> new Rejected("helm_space"));
                }
            }
            var supports = deck.contains(place) ? cockpitExtension(level, core, hull, place, seatDirection, anchorPos) : null;
            if (supports == null) throw new Rejected("marked_helm");
            supports.forEach(p -> parts.put(p, Blocks.OAK_PLANKS.defaultBlockState()));
            parts.put(place, facing(helm, seatDirection));
        } else if (!hasSeatBay(level, deck, existing.getKey(), seatDirection, anchorPos)) {
            throw new Rejected("helm_space");
        }

        var installedAnchor = hull.getOrDefault(anchorPos, Blocks.AIR.defaultBlockState());
        long anchorCount = hull.values().stream().filter(s -> s.is(anchor)).count();
        if (anchorCount > 1 || (anchorCount == 1 && (!installedAnchor.is(anchor)
                || installedAnchor.getValue(BlockStateProperties.POWERED))) || level.hasNeighborSignal(anchorPos)) {
            throw new Rejected("anchor");
        }
        if (anchorCount == 0) {
            if (!inRange(level, core, anchorPos) || !freeAir(level, anchorPos)
                    || parts.containsKey(anchorPos) || drivingSpace(hull, parts, anchorPos)) {
                throw new Rejected("anchor");
            }
            if (!deck.contains(anchorPos)) {
                var support = anchorPos.below();
                if (!inRange(level, core, support) || !freeAir(level, support)
                        || parts.containsKey(support) || drivingSpace(hull, parts, support)) throw new Rejected("anchor");
                parts.put(support, Blocks.OAK_PLANKS.defaultBlockState());
            }
            parts.put(anchorPos, facing(anchor, coreFacing.getClockWise()).setValue(BlockStateProperties.POWERED, false));
        }

        var enginePos = marks.engine();
        if (hull.values().stream().noneMatch(s -> s.is(engine))) {
            if (enginePos == null) {
                enginePos = deck.stream().filter(pos -> engineSlot(level, hull, parts, pos))
                    .findFirst().orElseThrow(() -> new Rejected("deck_space"));
            } else if (!deck.contains(enginePos) || !engineSlot(level, hull, parts, enginePos)) {
                throw new Rejected("marked_engine");
            }
            parts.put(enginePos, facing(engine, heading));
        } else if (enginePos != null && !hull.getOrDefault(enginePos, Blocks.AIR.defaultBlockState()).is(engine)) {
            throw new Rejected("marked_installed");
        }

        // Fit floaters on underside cells at the waterline, alternating left and right.
        Map<Long, BlockPos> underside = new HashMap<>();
        for (var pos : hull.keySet()) {
            long column = ((long) pos.getX() << 32) | (pos.getZ() & 0xffffffffL);
            underside.merge(column, pos, (a, b) -> a.getY() < b.getY() ? a : b);
        }
        var wetColumns = underside.values().stream()
            .filter(pos -> !hull.get(pos).getCollisionShape(level, pos).isEmpty())
            .map(BlockPos::below).filter(pos -> inRange(level, core, pos) && launchWater(level, pos))
            .sorted(nearCore.thenComparingLong(BlockPos::asLong)).toList();
        if (wetColumns.isEmpty()) throw new Rejected("water");
        var fullColumns = wetColumns.stream().filter(pos -> hull.get(pos.above()).isCollisionShapeFullBlock(level, pos.above()))
            .toList();
        double outfittedMass = hullMass + parts.values().stream().mapToDouble(BoatPlan::massOf).sum();
        if (!Double.isFinite(outfittedMass) || outfittedMass <= 0) throw new Rejected("mass");
        double floaterMass = massOf(floater.defaultBlockState());
        int available = (int) hull.entrySet().stream().filter(e -> e.getValue().is(floater)
            && e.getValue().getValue(BlockStateProperties.POWER) == 0 && !level.hasNeighborSignal(e.getKey())).count();
        int required = Math.max(2, (int) Math.ceil((outfittedMass - available * floaterMass) / 18_000));
        var usable = wetColumns.stream().filter(p -> !parts.containsKey(p) && !level.hasNeighborSignal(p)).toList();
        var stock = fullColumns.stream().filter(usable::contains).toList();
        boolean adapted = outfittedMass > STOCK_MASS || fullColumns.size() < 6
            || stock.size() < required - available;
        var candidates = adapted ? usable : stock;
        var port = new ArrayList<BlockPos>();
        var starboard = new ArrayList<BlockPos>();
        Direction side = heading.getClockWise();
        double center = candidates.stream().mapToInt(p -> side.getStepX() * p.getX() + side.getStepZ() * p.getZ())
            .average().orElse(0);
        var markedFloater = marks.floater();
        if (markedFloater != null && !candidates.contains(markedFloater)) {
            if (!hull.getOrDefault(markedFloater, Blocks.AIR.defaultBlockState()).is(floater)) {
                throw new Rejected("marked_floater");
            }
            markedFloater = null;
        }
        for (var pos : candidates) {
            if (pos.equals(markedFloater)) continue;
            double distance = side.getStepX() * pos.getX() + side.getStepZ() * pos.getZ() - center;
            (distance < 0 ? port : starboard).add(pos);
        }
        int missing = Math.min(adapted ? Math.min(required, 6) : required, candidates.size() + available) - available;
        if (markedFloater != null) {
            parts.put(markedFloater, floater.defaultBlockState());
            missing = Math.max(1, missing);
        }
        int added = markedFloater == null ? 0 : 1;
        for (int i = added; i < missing; i++, added++) {
            var preferred = i % 2 == 0 ? port : starboard;
            var alternative = i % 2 == 0 ? starboard : port;
            if (preferred.isEmpty()) preferred = alternative;
            if (preferred.isEmpty()) break;
            parts.put(preferred.remove(0), floater.defaultBlockState());
        }
        int floaters = available + added;
        if (floaters == 0) throw new Rejected("floater_space");
        double mass = outfittedMass + added * floaterMass;
        adapted |= mass > STOCK_MASS;
        if (!Double.isFinite(mass) || mass <= 0) throw new Rejected("mass");
        if (hull.size() + parts.size() > MAX_BLOCKS) throw new Rejected("boundary");
        for (var pos : parts.keySet()) {
            if (!level.getEntities(null, new AABB(pos)).isEmpty()) throw new Rejected("entity");
        }
        return new BoatPlan(Map.copyOf(hull), Map.copyOf(parts), mass, wetColumns.size(), floaters, adapted);
    }

    private static boolean inRange(ServerLevel level, BlockPos core, BlockPos pos) {
        return Math.abs(pos.getX() - core.getX()) <= RADIUS && Math.abs(pos.getY() - core.getY()) <= RADIUS
            && Math.abs(pos.getZ() - core.getZ()) <= RADIUS && level.getWorldBorder().isWithinBounds(pos);
    }

    private static boolean engineSlot(ServerLevel level, Map<BlockPos, BlockState> hull,
                                      Map<BlockPos, BlockState> parts, BlockPos pos) {
        return !parts.containsKey(pos) && !level.hasNeighborSignal(pos) && !drivingSpace(hull, parts, pos);
    }

    private static List<BlockPos> cockpitExtension(ServerLevel level, BlockPos core, Map<BlockPos, BlockState> hull,
                                                    BlockPos helm, Direction direction, BlockPos anchor) {
        var supports = new ArrayList<BlockPos>();
        for (int step = 0; step < 3; step++) {
            var cell = helm.relative(direction, step);
            if (cell.equals(anchor) || !inRange(level, core, cell) || !freeAir(level, cell)
                    || (step > 0 && !freeAir(level, cell.above()))) return null;
            var below = cell.below();
            var state = hull.get(below);
            if (state != null && state.isFaceSturdy(level, below, Direction.UP)) continue;
            if (!inRange(level, core, below) || !freeAir(level, below)) return null;
            supports.add(below);
        }
        return supports;
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

    private static boolean hasSeatBay(ServerLevel level, List<BlockPos> deck, BlockPos helm,
                                      Direction direction, BlockPos anchor) {
        var seat = helm.relative(direction);
        var exit = seat.relative(direction);
        return !helm.equals(anchor) && !seat.equals(anchor) && !exit.equals(anchor)
            && deck.contains(seat) && freeAir(level, seat.above())
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
