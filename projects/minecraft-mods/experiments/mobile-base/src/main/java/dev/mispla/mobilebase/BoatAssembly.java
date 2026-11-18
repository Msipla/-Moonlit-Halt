package dev.mispla.mobilebase;

import dev.mispla.mobilebase.BoatPlan.Rejected;
import dev.mispla.mobilebase.MobileBase.SurfaceBoat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3i;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.eureka.ship.EurekaShipControl;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;

public final class BoatAssembly {
    private static final ThreadLocal<Assembly> pending = new ThreadLocal<>();

    private BoatAssembly() {}

    static ServerShip launch(ServerLevel level, BoatPlan plan) throws Rejected {
        return launch(level, plan.hull(), plan.parts(), () -> {}, (ship, target) ->
            EurekaShipControl.Companion.deferUntilLoaded(ship,
                control -> ((SurfaceBoat) (Object) control).mobilebase$adapt(
                    plan.adapted() ? plan.wetColumns() : 0, plan.floaters(), plan.engineScale())));
    }

    @FunctionalInterface
    interface OnAssembled {
        void run(ServerShip ship, Map<BlockPos, SavedBlock> target);
    }

    static ServerShip launch(ServerLevel level, Map<BlockPos, BlockState> hull, Map<BlockPos, BlockState> parts,
                             Runnable prepare, OnAssembled onAssembled) throws Rejected {
        var positions = new HashSet<>(hull.keySet());
        positions.addAll(parts.keySet());
        var bounds = ShipAssembler.findMinAndMax(positions);
        if (VSGameUtilsKt.getShipsIntersecting(level, new AABB(bounds.getFirst(),
                bounds.getSecond().offset(1, 1, 1)).inflate(0.1)).iterator().hasNext()) {
            throw new Rejected("occupied");
        }

        var transaction = new Assembly(level, Set.copyOf(positions), capture(level, positions),
            new LinkedHashMap<>(), new LinkedHashMap<>());
        pending.set(transaction);
        try {
            for (var part : parts.entrySet()) {
                if (!level.setBlock(part.getKey(), part.getValue(), Block.UPDATE_CLIENTS)
                        || level.getBlockState(part.getKey()) != part.getValue()) {
                    throw new IllegalStateException("Could not install " + part.getKey());
                }
            }
            prepare.run();
            transaction.expected.putAll(capture(level, positions));
            var ship = ShipAssembler.assembleToShip(level, positions, 1.0);
            if (transaction.ship != ship || transaction.target.size() != positions.size()) {
                throw new IllegalStateException("Assembly target was not captured");
            }
            for (var entry : transaction.target.entrySet()) {
                var actual = SavedBlock.read(level, entry.getKey());
                var expected = entry.getValue();
                if (!expected.equals(actual)) {
                    throw new IllegalStateException("VS changed assembly target " + entry.getKey() + " from "
                        + expected.state() + " to " + actual.state() + " (block entity data equal: "
                        + java.util.Objects.equals(expected.data(), actual.data()) + ")");
                }
            }
            for (var pos : positions) {
                if (!level.getBlockState(pos).isAir()) {
                    throw new IllegalStateException("VS left a source block at " + pos);
                }
            }
            onAssembled.run(ship, transaction.target);
            return ship;
        } catch (RuntimeException | AssertionError error) {
            MobileBase.LOGGER.error("Ship launch failed, restoring snapshot", error);
            if (transaction.ship != null) {
                for (var pos : transaction.target.keySet()) {
                    level.removeBlockEntity(pos);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                ShipAssembler.INSTANCE.deleteShip(level, transaction.ship, false, false);
            }
            restore(level, transaction.original);
            throw new Rejected("rolled_back");
        } finally {
            pending.remove();
        }
    }

    // VS exposes the new ship and placement origin before removing any of the source blocks.
    public static void captureTarget(ServerLevel level, Set<BlockPos> blocks, ServerShip from, ServerShip to,
                                     BlockPos min, BlockPos max, Vector3i destination) {
        var assembly = pending.get();
        if (assembly == null || level != assembly.level || from != null || to == null
                || !assembly.positions.equals(blocks)) return;
        assembly.ship = to;
        var corner = new BlockPos(
            destination.x - (max.getX() - min.getX() + 1) / 2,
            destination.y - (max.getY() - min.getY() + 1) / 2,
            destination.z - (max.getZ() - min.getZ() + 1) / 2);
        for (var entry : assembly.expected.entrySet()) {
            assembly.target.put(corner.offset(entry.getKey().subtract(min)), entry.getValue());
        }
    }

    static Map<BlockPos, SavedBlock> capture(ServerLevel level, Iterable<BlockPos> positions) {
        var snapshot = new LinkedHashMap<BlockPos, SavedBlock>();
        for (var pos : positions) snapshot.put(pos, SavedBlock.read(level, pos));
        return snapshot;
    }

    static void restore(ServerLevel level, Map<BlockPos, SavedBlock> snapshot) {
        for (var entry : snapshot.entrySet()) {
            var pos = entry.getKey();
            var saved = entry.getValue();
            if (saved.matches(level, pos)) continue;
            var entity = level.getBlockEntity(pos);
            if (entity != null && level.getBlockState(pos).getBlock() != saved.state().getBlock()) {
                level.removeBlockEntity(pos);
            }
            level.setBlock(pos, saved.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            if (saved.data() != null) {
                entity = level.getBlockEntity(pos);
                if (entity == null) {
                    throw new IllegalStateException("Cannot restore saved block entity at " + pos);
                }
                entity.load(saved.data().copy());
                entity.setChanged();
            }
        }
        snapshot.forEach((pos, saved) -> {
            if (!saved.matches(level, pos)) throw new IllegalStateException("Rollback differs at " + pos);
        });
    }

    record SavedBlock(BlockState state, CompoundTag data) {
        static SavedBlock read(ServerLevel level, BlockPos pos) {
            var entity = level.getBlockEntity(pos);
            return new SavedBlock(level.getBlockState(pos), entity == null ? null : entity.saveWithoutMetadata());
        }

        boolean matches(ServerLevel level, BlockPos pos) {
            return equals(read(level, pos));
        }
    }

    private static final class Assembly {
        final ServerLevel level;
        final Set<BlockPos> positions;
        final Map<BlockPos, SavedBlock> original;
        final Map<BlockPos, SavedBlock> expected;
        final Map<BlockPos, SavedBlock> target;
        ServerShip ship;

        Assembly(ServerLevel level, Set<BlockPos> positions, Map<BlockPos, SavedBlock> original,
                 Map<BlockPos, SavedBlock> expected, Map<BlockPos, SavedBlock> target) {
            this.level = level;
            this.positions = positions;
            this.original = original;
            this.expected = expected;
            this.target = target;
        }
    }
}
