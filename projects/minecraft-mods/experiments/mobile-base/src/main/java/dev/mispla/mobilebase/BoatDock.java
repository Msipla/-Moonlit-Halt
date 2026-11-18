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

record BoatDock(Map<BlockPos, BlockPos> moves, Rotation rotation, boolean land) {
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
        boolean land = ((SurfaceBoat) (Object) control).mobilebase$land() != null;
        boolean air = sources.stream().anyMatch(pos -> level.getBlockState(pos).is(MobileBase.AIR_CORE));
        boolean submarine = sources.stream().anyMatch(pos -> level.getBlockState(pos).is(MobileBase.SUB_CORE));
        var core = land ? MobileBase.LAND_CORE : air ? MobileBase.AIR_CORE : submarine ? MobileBase.SUB_CORE : MobileBase.CORE;
        if (sources.stream().filter(pos -> level.getBlockState(pos).is(core)).count() != 1) {
            throw new Rejected("dock_size");
        }

        var moves = new LinkedHashMap<BlockPos, BlockPos>();
        var destinations = new HashSet<BlockPos>();
        var local = new Vector3d();
        for (var pos : sources) {
            var state = level.getBlockState(pos);
            if (!(land ? LandPlan.supported(state) : air ? BoatPlan.airSupported(state) : submarine ? BoatPlan.subSupported(state) : BoatPlan.supported(state))) throw new Rejected("unsupported", pos.toShortString());
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
            boolean canReplaceWater = !land && previous.is(Blocks.WATER) && previous.getFluidState().isSource()
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
        return new BoatDock(Map.copyOf(moves), blockRotation, land);
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

    static boolean sameChassisData(net.minecraft.nbt.CompoundTag left, net.minecraft.nbt.CompoundTag right) {
        return left != null && right != null && detachedChassisData(left).equals(detachedChassisData(right));
    }

    private static net.minecraft.nbt.CompoundTag detachedChassisData(net.minecraft.nbt.CompoundTag source) {
        var copy = source.copy();
        for (var key : java.util.List.of("Speed", "Source", "Network", "Sequence", "NeedsSpeedUpdate", "WheelTravel")) {
            copy.remove(key);
        }
        if (copy.contains("Assembled")) copy.putBoolean("Assembled", false);
        return copy;
    }

    private static void restoreChassis(ServerLevel level, BlockPos position, SavedBlock block) {
        var entity = level.getBlockEntity(position);
        if (entity == null || block.data() == null) throw new IllegalStateException("Missing restored chassis block");
        // A wheel and its kinetic network must register at the restored coordinates.
        var data = detachedChassisData(block.data());
        entity.load(data);
        entity.setChanged();
        if (!sameChassisData(data, entity.saveWithoutMetadata())) {
            throw new IllegalStateException("Incorrect restored chassis data at " + position);
        }
    }

    void disassemble(ServerLevel level, LoadedServerShip ship) throws Rejected {
        var originals = BoatAssembly.capture(level, moves.keySet());
        var berth = BoatAssembly.capture(level, moves.values());
        var setup = land ? ((SurfaceBoat) (Object) ship.getAttachment(EurekaShipControl.class)).mobilebase$land() : null;
        try {
            for (var entry : moves.entrySet()) {
                RelocationUtilKt.relocateBlock(level, entry.getKey(), entry.getValue(), false, null, rotation);
            }
            for (var entry : moves.entrySet()) {
                var source = originals.get(entry.getKey());
                var state = source.state().rotate(rotation);
                if (state.is(MobileBase.CORE) || state.is(MobileBase.LAND_CORE) || state.is(MobileBase.AIR_CORE) || state.is(MobileBase.SUB_CORE)) state = state.setValue(BlockStateProperties.POWERED, false);
                if (state.is(BoatPlan.part("anchor"))) state = state.setValue(BlockStateProperties.POWERED, false);
                var dest = entry.getValue();
                var actual = SavedBlock.read(level, dest);
                boolean chassis = land && (LandPlan.isWheel(state) || LandPlan.isMotor(state));
                if (source.state().rotate(rotation) != actual.state() || !level.getBlockState(entry.getKey()).isAir()
                        || !(chassis ? sameChassisData(source.data(), actual.data())
                            : java.util.Objects.equals(source.data(), actual.data()))) {
                    throw new IllegalStateException("Incorrect relocated block " + state + " at " + dest);
                }
                if (state != level.getBlockState(dest)) level.setBlock(dest, state, Block.UPDATE_CLIENTS);
                if (chassis) restoreChassis(level, dest, source);
            }
            for (var entry : moves.entrySet()) {
                var dest = entry.getValue();
                RelocationUtilKt.updateBlock(level, entry.getKey(), dest, level.getBlockState(dest));
            }
            org.valkyrienskies.mod.common.assembly.ShipAssembler.INSTANCE.deleteShip(level, ship, false, false);
        } catch (RuntimeException | AssertionError error) {
            MobileBase.LOGGER.error("Disassembly failed, restoring both snapshots", error);
            BoatAssembly.restore(level, berth);
            BoatAssembly.restore(level, originals, land);
            if (setup != null) {
                var control = EurekaShipControl.Companion.getOrCreate(ship);
                ((SurfaceBoat) (Object) control).mobilebase$fitLand(setup);
                originals.forEach((pos, block) -> {
                    if (LandPlan.isWheel(block.state()) || LandPlan.isMotor(block.state())) {
                        restoreChassis(level, pos, block);
                    }
                });
            }
            throw new Rejected("rolled_back");
        }
    }
}
