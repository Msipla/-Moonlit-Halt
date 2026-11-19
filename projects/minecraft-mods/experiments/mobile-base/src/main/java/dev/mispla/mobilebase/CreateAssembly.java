package dev.mispla.mobilebase;

import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.contraptions.gantry.GantryCarriageBlock;
import com.simibubi.create.content.contraptions.gantry.GantryContraption;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.kinetics.gantry.GantryShaftBlock;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlock;
import com.simibubi.create.content.trains.entity.CarriageContraption;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import dev.mispla.mobilebase.BoatPlan.Rejected;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

record CreateAssembly(BlockPos root, Direction trainDirection, Map<BlockPos, BlockState> body,
                      Map<BlockPos, BlockState> parts, AABB glue) {
    private static final ResourceLocation CONTROLS = new ResourceLocation("create", "controls");

    static CreateAssembly scan(ServerLevel level, BlockPos clicked) throws Rejected {
        var state = level.getBlockState(clicked);
        if (level.getBlockEntity(clicked) instanceof StationBlockEntity station && station.isAssembling()
                && station.edgePoint.hasValidTrack() && station.getAssemblyDirection() != null) {
            var direction = station.getAssemblyDirection();
            var bogey = station.edgePoint.getGlobalPosition().relative(direction).above();
            if (!direction.getAxis().isHorizontal() || !BoatPlan.loaded(level, bogey)
                    || !(level.getBlockState(bogey).getBlock() instanceof AbstractBogeyBlock<?>)) {
                throw new Rejected("create_bogey");
            }
            return build(level, bogey, direction);
        }
        if (state.getBlock() instanceof GantryCarriageBlock && state.getValue(GantryCarriageBlock.FACING) == Direction.DOWN
                && BoatPlan.loaded(level, clicked.above())
                && level.getBlockState(clicked.above()).getBlock() instanceof GantryShaftBlock
                && level.getBlockState(clicked.above()).getValue(GantryShaftBlock.FACING).getAxis().isHorizontal()) {
            return build(level, clicked, null);
        }
        throw new Rejected("create_target");
    }

    private static CreateAssembly build(ServerLevel level, BlockPos root, Direction trainDirection) throws Rejected {
        var body = new LinkedHashMap<BlockPos, BlockState>();
        var visited = new HashSet<BlockPos>();
        var frontier = new ArrayDeque<BlockPos>();
        frontier.add(root);
        while (!frontier.isEmpty()) {
            var pos = frontier.remove();
            if (!visited.add(pos)) continue;
            if (!BoatPlan.loaded(level, pos)) throw new Rejected("unloaded");
            var state = level.getBlockState(pos);
            if (state.isAir()) continue;
            if (!BoatPlan.inRange(level, root, pos) || body.size() >= BoatPlan.MAX_BLOCKS) throw new Rejected("boundary");
            if (!allowed(state, pos.equals(root), trainDirection != null) || VSGameUtilsKt.getShipManagingPos(level, pos) != null) {
                throw new Rejected("unsupported", pos.toShortString());
            }
            body.put(pos, state);
            for (var side : Direction.values()) {
                // The track or shaft supports the stationary frame and belongs to the route.
                if (state.getBlock() instanceof AbstractBogeyBlock<?> && side == Direction.DOWN
                        || pos.equals(root) && trainDirection == null && side == Direction.UP) continue;
                frontier.add(pos.relative(side));
            }
        }
        if (body.size() < 2 || trainDirection == null && !body.containsKey(root.below())) throw new Rejected("create_body");
        Map<BlockPos, BlockState> parts = Map.of();
        if (trainDirection != null && body.values().stream().noneMatch(s -> s.getBlock() instanceof ControlsBlock
                && s.getValue(ControlsBlock.FACING) == trainDirection)) {
            var controls = body.keySet().stream().map(BlockPos::above)
                .filter(p -> p.getY() > root.getY() && BoatPlan.loaded(level, p) && BoatPlan.inRange(level, root, p)
                    && level.getBlockState(p).isAir() && level.getBlockEntity(p) == null
                    && level.getEntities(null, new AABB(p)).isEmpty()
                    && body.get(p.below()).isFaceSturdy(level, p.below(), Direction.UP))
                .min(Comparator.<BlockPos>comparingDouble(root::distSqr).thenComparingLong(BlockPos::asLong))
                .orElseThrow(() -> new Rejected("create_controls"));
            parts = Map.of(controls, BuiltInRegistries.BLOCK.get(CONTROLS).defaultBlockState()
                .setValue(ControlsBlock.FACING, trainDirection));
        }

        var all = new HashSet<>(body.keySet());
        all.addAll(parts.keySet());
        if (all.size() > BoatPlan.MAX_BLOCKS) throw new Rejected("boundary");
        var box = BoundingBox.encapsulatingPositions(all).orElseThrow();
        var min = new BlockPos(box.minX(), box.minY(), box.minZ());
        var max = new BlockPos(box.maxX(), box.maxY(), box.maxZ());
        for (var pos : BlockPos.betweenClosed(min, max)) {
            if (!BoatPlan.loaded(level, pos) || !all.contains(pos) && !level.getBlockState(pos).isAir()) {
                throw new Rejected("create_overlap", pos.toShortString());
            }
        }
        var glue = SuperGlueEntity.span(min, max);
        for (var existing : level.getEntitiesOfClass(SuperGlueEntity.class, glue)) {
            if (!glue.minmax(existing.getBoundingBox()).equals(glue)) throw new Rejected("create_glue_boundary");
        }
        return new CreateAssembly(root, trainDirection, Map.copyOf(body), parts, glue);
    }

    private static boolean allowed(BlockState state, boolean atRoot, boolean train) {
        var block = state.getBlock();
        if (block instanceof AbstractBogeyBlock<?> bogey) return train && !bogey.isUpsideDown(state);
        if (block instanceof ControlsBlock) return train && state.getFluidState().isEmpty();
        if (block instanceof GantryCarriageBlock) return atRoot && !train;
        var id = BuiltInRegistries.BLOCK.getKey(block);
        return id.getNamespace().equals("minecraft") && BoatPlan.supported(state)
            || id.getNamespace().equals("create") && id.getPath().endsWith("_seat") && state.getFluidState().isEmpty();
    }

    boolean prepare(ServerLevel level) throws Rejected {
        var existing = level.getEntitiesOfClass(SuperGlueEntity.class, glue);
        var added = existing.stream().anyMatch(e -> e.getBoundingBox().equals(glue)) ? null : new SuperGlueEntity(level, glue);
        boolean success = false;
        try {
            for (var part : parts.entrySet()) {
                if (!level.setBlock(part.getKey(), part.getValue(), Block.UPDATE_ALL)) throw new Rejected("create_controls");
            }
            if (added != null && !level.addFreshEntity(added)) throw new Rejected("create_glue");
            var nativePlan = trainDirection != null ? new CarriageContraption(trainDirection) : new GantryContraption(Direction.DOWN);
            if (!nativePlan.assemble(level, root) || nativePlan.getBlocks().size() != body.size() + parts.size()
                    || nativePlan.getBlocks().keySet().stream().map(root::offset)
                        .anyMatch(pos -> !body.containsKey(pos) && !parts.containsKey(pos))) {
                throw new Rejected("create_capture");
            }
            success = true;
            return trainDirection != null;
        } catch (AssemblyException exception) {
            throw new Rejected("create_native", exception.component);
        } finally {
            if (!success) {
                if (added != null) added.discard();
                parts.keySet().forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
            }
        }
    }
}
