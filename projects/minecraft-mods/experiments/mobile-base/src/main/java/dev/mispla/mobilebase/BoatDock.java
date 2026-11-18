package dev.mispla.mobilebase;

import dev.mispla.mobilebase.BoatAssembly.SavedBlock;
import dev.mispla.mobilebase.BoatPlan.Rejected;
import dev.mispla.mobilebase.MobileBase.SurfaceBoat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.eureka.ship.EurekaShipControl;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.util.RelocationUtilKt;

record BoatDock(Map<BlockPos, BlockPos> moves, Rotation rotation) {
    static BoatDock inspect(ServerLevel level, LoadedServerShip ship, EurekaShipControl control) throws Rejected {
        if (control.getAnchorsActive() < 1 || control.isCruising() || !isStill(ship, (SurfaceBoat) (Object) control)) {
            throw new Rejected("not_stopped");
        }

        var transform = ship.getTransform();
        var scale = transform.getShipToWorldScaling();
        if (Math.abs(scale.x() - 1) > 1e-6 || Math.abs(scale.y() - 1) > 1e-6
                || Math.abs(scale.z() - 1) > 1e-6) throw new Rejected("alignment");
        // Relocation only supports horizontal quarter turns. Refuse tilted or diagonal poses.
        var rotation = transform.getShipToWorldRotation();
        int yaw = 0;
        double closest = 0.0;
        for (int candidate = 0; candidate < 4; candidate++) {
            var cardinal = new Quaterniond().rotationY(candidate * Math.PI / 2);
            double dot = Math.abs(rotation.dot(cardinal));
            if (dot > closest) {
                yaw = candidate;
                closest = dot;
            }
        }
        if (closest < Math.cos(Math.toRadians(0.5))) throw new Rejected("alignment");
        var snapped = new Quaterniond().rotationY(yaw * Math.PI / 2);
        var blockRotation = switch (yaw) {
            case 1 -> Rotation.COUNTERCLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.CLOCKWISE_90;
            default -> Rotation.NONE;
        };

        var aboard = new AABB(ship.getWorldAABB().minX(), ship.getWorldAABB().minY(),
            ship.getWorldAABB().minZ(), ship.getWorldAABB().maxX(), ship.getWorldAABB().maxY(),
            ship.getWorldAABB().maxZ()).inflate(0.15, 2.0, 0.15);
        if (!level.getEntities((Entity) null, aboard).isEmpty()) throw new Rejected("aboard");

        var sources = new HashSet<BlockPos>();
        var chunksReady = new boolean[]{true};
        ship.getActiveChunksSet().forEach((x, z) -> {
            if (!level.hasChunk(x, z) || sources.size() > BoatPlan.MAX_BLOCKS) {
                chunksReady[0] = false;
                return;
            }
            var chunk = level.getChunk(x, z);
            for (int i = 0; i < chunk.getSectionsCount(); i++) {
                var section = chunk.getSection(i);
                if (section.hasOnlyAir()) continue;
                int bottom = chunk.getSectionYFromSectionIndex(i) << 4;
                for (int sx = 0; sx < 16; sx++) {
                    for (int sy = 0; sy < 16; sy++) {
                        for (int sz = 0; sz < 16; sz++) {
                            if (!section.getBlockState(sx, sy, sz).isAir()) {
                                sources.add(new BlockPos((x << 4) + sx, bottom + sy, (z << 4) + sz));
                            }
                        }
                    }
                }
            }
        });
        if (!chunksReady[0] || sources.isEmpty() || sources.size() > BoatPlan.MAX_BLOCKS) {
            throw new Rejected("dock_size");
        }
        if (sources.stream().filter(pos -> level.getBlockState(pos).is(MobileBase.CORE)).count() != 1) {
            throw new Rejected("dock_size");
        }

        var moves = new LinkedHashMap<BlockPos, BlockPos>();
        var destinations = new HashSet<BlockPos>();
        var local = new Vector3d();
        for (var pos : sources) {
            var state = level.getBlockState(pos);
            if (!BoatPlan.supported(state)) throw new Rejected("unsupported", pos.toShortString());
            // Transform block centers about the same pivot as VS, using the accepted cardinal pose.
            local.set(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                .sub(transform.getPositionInShip()).rotate(snapped).add(transform.getPositionInWorld());
            var dest = BlockPos.containing(local.x(), local.y(), local.z());
            if (Math.abs(local.x() - dest.getX() - 0.5) > 0.49
                    || Math.abs(local.y() - dest.getY() - 0.5) > 0.49
                    || Math.abs(local.z() - dest.getZ() - 0.5) > 0.49) throw new Rejected("alignment");
            if (!BoatPlan.loaded(level, dest) || !level.getWorldBorder().isWithinBounds(dest)
                    || !destinations.add(dest)) throw new Rejected("occupied");
            var previous = level.getBlockState(dest);
            boolean canReplaceWater = previous.is(Blocks.WATER) && previous.getFluidState().isSource()
                && state.isCollisionShapeFullBlock(level, pos);
            if ((!previous.isAir() && !canReplaceWater) || level.getBlockEntity(dest) != null
                    || !level.getEntities((Entity) null, new AABB(dest)).isEmpty()) {
                throw new Rejected("occupied_at", dest.toShortString());
            }
            for (var other : VSGameUtilsKt.getShipsIntersecting(level, new AABB(dest))) {
                if (other.getId() != ship.getId()) throw new Rejected("occupied_at", dest.toShortString());
            }
            moves.put(pos, dest);
        }
        return new BoatDock(Map.copyOf(moves), blockRotation);
    }

    private static boolean isStill(LoadedServerShip ship, SurfaceBoat control) {
        if (ship.getVelocity().lengthSquared() <= 0.0025
                && ship.getAngularVelocity().lengthSquared() <= 0.0004) return true;
        // Eureka freezes the physics body without clearing the last velocities published to the server.
        if (!control.mobilebase$isAdaptedBoatAnchored()) return false;
        var current = ship.getTransform();
        var previous = ship.getPrevTickTransform();
        return previous != null
            && current.getPositionInWorld().distanceSquared(previous.getPositionInWorld()) < 1e-8
            && Math.abs(current.getShipToWorldRotation().dot(previous.getShipToWorldRotation())) > 1 - 1e-8;
    }

    void disassemble(ServerLevel level, LoadedServerShip ship) throws Rejected {
        var originals = BoatAssembly.capture(level, moves.keySet());
        var berth = BoatAssembly.capture(level, moves.values());
        try {
            for (var entry : moves.entrySet()) {
                RelocationUtilKt.relocateBlock(level, entry.getKey(), entry.getValue(), false, null, rotation);
            }
            for (var entry : moves.entrySet()) {
                var source = originals.get(entry.getKey());
                var state = source.state().rotate(rotation);
                if (state.is(MobileBase.CORE)) state = state.setValue(BlockStateProperties.POWERED, false);
                if (state.is(BoatPlan.part("anchor"))) state = state.setValue(BlockStateProperties.POWERED, false);
                var dest = entry.getValue();
                if (!new SavedBlock(source.state().rotate(rotation), source.data()).matches(level, dest)
                        || !level.getBlockState(entry.getKey()).isAir()) {
                    throw new IllegalStateException("Incorrect relocated block at " + dest);
                }
                if (state != level.getBlockState(dest)) level.setBlock(dest, state, Block.UPDATE_CLIENTS);
            }
            for (var entry : moves.entrySet()) {
                var dest = entry.getValue();
                RelocationUtilKt.updateBlock(level, entry.getKey(), dest, level.getBlockState(dest));
            }
            org.valkyrienskies.mod.common.assembly.ShipAssembler.INSTANCE.deleteShip(level, ship, false, false);
        } catch (RuntimeException | AssertionError error) {
            MobileBase.LOGGER.error("Disassembly failed, restoring both snapshots", error);
            BoatAssembly.restore(level, berth);
            BoatAssembly.restore(level, originals);
            throw new Rejected("rolled_back");
        }
    }
}
