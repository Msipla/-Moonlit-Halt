package dev.mispla.mobilebase;

import dev.mispla.mobilebase.BoatPlan.Rejected;
import dev.mispla.mobilebase.MobileBase.ManagedBoat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.joml.Vector3f;
import org.valkyrienskies.eureka.ship.EurekaShipControl;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

final class BoatCoreBlock extends HorizontalDirectionalBlock {
    private static final DustParticleOptions HULL = new DustParticleOptions(new Vector3f(0.22f, 0.89f, 0.94f), 1.1f);
    private static final DustParticleOptions PART = new DustParticleOptions(new Vector3f(1.0f, 0.63f, 0.1f), 1.3f);
    private static final int CONFIRM_TICKS = 200;
    private static final Map<UUID, Preview> previews = new HashMap<>();

    BoatCoreBlock() {
        super(Properties.of().sound(SoundType.COPPER).strength(3.0f));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
            .setValue(BlockStateProperties.POWERED, false));
    }

    static void clearPreviews() {
        previews.clear();
    }

    static void clearPreview(ServerPlayer player) {
        previews.remove(player.getUUID());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BlockStateProperties.POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction fromReceiver) {
        return state.getValue(BlockStateProperties.POWERED)
            && fromReceiver == state.getValue(FACING).getCounterClockWise() ? 15 : 0;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !player.getItemInHand(hand).isEmpty()) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer user) || !(level instanceof ServerLevel world)) return InteractionResult.PASS;
        if (!user.isCreative()) {
            send(user, "mobilebase.creative_only");
            return InteractionResult.CONSUME;
        }
        try {
            interact(world, pos, state, user);
        } catch (Rejected rejected) {
            previews.remove(user.getUUID());
            send(user, rejected.key, rejected.arguments);
        }
        return InteractionResult.CONSUME;
    }

    private static void interact(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer user) throws Rejected {
        long now = level.getGameTime();
        previews.values().removeIf(preview -> preview.level == level && preview.expires < now);
        var previous = previews.remove(user.getUUID());
        var ship = VSGameUtilsKt.getLoadedShipManagingPos(level, pos);
        if (ship == null) {
            if (VSGameUtilsKt.getShipManagingPos(level, pos) != null) throw new Rejected("unloaded");
            if (user.isShiftKeyDown()) {
                send(user, "mobilebase.launch_hint");
                return;
            }
            previewLaunch(level, pos, state, user, BoatMarkerItem.Marks.DEFAULT, previous, now);
            return;
        }

        var control = ship.getAttachment(EurekaShipControl.class);
        if (control == null || !ManagedBoat.of(control).mobilebase$isManaged()) throw new Rejected("foreign_ship");
        if (user.isShiftKeyDown()) {
            var anchor = pos.relative(state.getValue(FACING).getClockWise());
            if (!BoatPlan.loaded(level, anchor) || !level.getBlockState(anchor).is(BoatPlan.part("anchor"))) {
                throw new Rejected("anchor");
            }
            var next = state.cycle(BlockStateProperties.POWERED);
            level.setBlock(pos, next, Block.UPDATE_ALL);
            send(user, next.getValue(BlockStateProperties.POWERED) ? "mobilebase.park" : "mobilebase.release_anchor");
            return;
        }
        var docking = BoatDock.inspect(level, ship, control);
        if (confirmed(previous, level, pos, now, docking)) {
            docking.disassemble(level, ship);
            MobileBase.LOGGER.info("Docked boat {} and verified {} relocated blocks", ship.getId(), docking.moves().size());
            send(user, "mobilebase.docked");
        } else {
            docking.moves().values().forEach(p -> particle(level, user, p, HULL));
            previews.put(user.getUUID(), new Preview(level, pos, now + CONFIRM_TICKS, docking));
            send(user, "mobilebase.dock_preview", docking.moves().size());
        }
    }

    static void launchWithMarks(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer user,
                                BoatMarkerItem.Marks marks) {
        var previous = previews.remove(user.getUUID());
        try {
            if (VSGameUtilsKt.getShipManagingPos(level, pos) != null) throw new Rejected("marker_in_world");
            previewLaunch(level, pos, state, user, marks, previous, level.getGameTime());
        } catch (Rejected rejected) {
            send(user, rejected.key, rejected.arguments);
        }
    }

    private static void previewLaunch(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer user,
                                      BoatMarkerItem.Marks marks, Preview previous, long now) throws Rejected {
        previews.values().removeIf(preview -> preview.level == level && preview.expires < now);
        var plan = BoatPlan.scan(level, pos, state.getValue(FACING), marks);
        if (confirmed(previous, level, pos, now, plan)) {
            var launched = BoatAssembly.launch(level, plan);
            MobileBase.LOGGER.info("Launched boat {} with {} hull blocks and {} added parts ({} kg, adapted {})",
                launched.getId(), plan.hull().size(), plan.parts().size(), Math.round(plan.mass()), plan.adapted());
            send(user, plan.adapted() ? "mobilebase.adapted_launched" : "mobilebase.launched");
            return;
        }
        for (var p : plan.hull().keySet()) {
            for (var face : Direction.values()) {
                if (!plan.hull().containsKey(p.relative(face))) surfaceParticle(level, user, p, face);
            }
        }
        plan.parts().keySet().forEach(p -> particle(level, user, p, PART));
        previews.put(user.getUUID(), new Preview(level, pos, now + CONFIRM_TICKS, plan));
        send(user, plan.adapted() ? "mobilebase.adapted_preview" : "mobilebase.launch_preview",
            plan.hull().size(), plan.parts().size(), Math.round(plan.mass()));
    }

    private static boolean confirmed(Preview previous, ServerLevel level, BlockPos pos, long now, Object plan) {
        return previous != null && previous.level == level && previous.pos.equals(pos)
            && previous.expires >= now && previous.plan.equals(plan);
    }

    private static void surfaceParticle(ServerLevel level, ServerPlayer user, BlockPos pos, Direction face) {
        level.sendParticles(user, HULL, true, pos.getX() + 0.5 + face.getStepX() * 0.54,
            pos.getY() + 0.5 + face.getStepY() * 0.54, pos.getZ() + 0.5 + face.getStepZ() * 0.54,
            3, face.getStepX() == 0 ? 0.26 : 0, face.getStepY() == 0 ? 0.26 : 0,
            face.getStepZ() == 0 ? 0.26 : 0, 0.0);
    }

    private static void particle(ServerLevel level, ServerPlayer user, BlockPos pos, DustParticleOptions option) {
        level.sendParticles(user, option, true, pos.getX() + 0.5, pos.getY() + 0.7,
            pos.getZ() + 0.5, 6, 0.33, 0.21, 0.33, 0.0);
    }

    private static void send(ServerPlayer user, String key, Object... arguments) {
        user.displayClientMessage(Component.translatable(key, arguments), true);
    }

    private record Preview(ServerLevel level, BlockPos pos, long expires, Object plan) {}
}
