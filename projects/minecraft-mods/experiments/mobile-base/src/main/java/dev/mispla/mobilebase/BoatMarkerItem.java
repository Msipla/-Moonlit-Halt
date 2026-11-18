package dev.mispla.mobilebase;

import java.util.Locale;
import java.util.Objects;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import org.valkyrienskies.eureka.block.ShipHelmBlock;

final class BoatMarkerItem extends Item {
    static final ResourceLocation OPEN = new ResourceLocation("mobilebase", "open_marker");
    static final ResourceLocation EDIT = new ResourceLocation("mobilebase", "edit_marker");
    private static final String MARKS = "BoatMarks";
    private static final String DRAFT = "BoatDraft";
    private static final String UNDO = "BoatUndo";

    enum Edit { SELECT, REMOVE, UNDO, STOP }

    enum Role {
        HELM("oak_ship_helm"), ENGINE("engine"), FLOATER("floater"), HEADING(null);

        final String partId;

        Role(String partId) {
            this.partId = partId;
        }

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        Component label() {
            return Component.translatable("mobilebase.marker." + key());
        }
    }

    record Marks(BlockPos helm, BlockPos engine, BlockPos floater, Direction heading) {
        static final Marks DEFAULT = new Marks(null, null, null, null);

        BlockPos position(Role role) {
            return switch (role) {
                case HELM -> helm;
                case ENGINE -> engine;
                case FLOATER -> floater;
                case HEADING -> null;
            };
        }
    }

    BoatMarkerItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!player.isCreative() || hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        if (player instanceof ServerPlayer user) open(user);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    static InteractionResult useBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        var stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || !player.isCreative() || !(stack.getItem() instanceof BoatMarkerItem)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer user) || !(level instanceof ServerLevel world)) return InteractionResult.PASS;

        var state = world.getBlockState(hit.getBlockPos());
        var role = editing(stack, world);
        if (!user.isShiftKeyDown() && role == null && state.is(MobileBase.CORE)) {
            BoatCoreBlock.launchWithMarks(world, hit.getBlockPos(), state, user, marks(stack, world));
        } else if (user.isShiftKeyDown() || role == null) {
            open(user);
        } else {
            mark(world, user, stack, role, hit);
        }
        return InteractionResult.CONSUME;
    }

    static void registerEdits() {
        ServerPlayNetworking.registerGlobalReceiver(EDIT, (server, player, handler, buffer, sender) -> {
            if (buffer.readableBytes() != 3) return;
            int slot = buffer.readUnsignedByte();
            int action = buffer.readUnsignedByte();
            int type = buffer.readUnsignedByte();
            if (action >= Edit.values().length || type >= Role.values().length) return;
            server.execute(() -> {
                var stack = player.getMainHandItem();
                if (!player.isCreative() || player.getInventory().selected != slot || !(stack.getItem() instanceof BoatMarkerItem)) return;
                var level = player.serverLevel();
                var role = Role.values()[type];
                switch (Edit.values()[action]) {
                    case SELECT -> {
                        var draft = new CompoundTag();
                        draft.putString("dimension", dimension(level));
                        draft.putInt("role", type);
                        stack.getOrCreateTag().put(DRAFT, draft);
                        report(player, "mobilebase.marker.mode", role.label());
                    }
                    case REMOVE -> {
                        var data = inDimension(stack, MARKS, level);
                        if (data != null && data.contains(role.key())) {
                            remember(stack, level);
                            data.remove(role.key());
                        }
                        stack.removeTagKey(DRAFT);
                        report(player, "mobilebase.marker.removed", role.label());
                    }
                    case UNDO -> {
                        var undo = inDimension(stack, UNDO, level);
                        if (undo != null) {
                            if (undo.contains(MARKS, Tag.TAG_COMPOUND)) stack.getOrCreateTag().put(MARKS, undo.getCompound(MARKS));
                            else stack.removeTagKey(MARKS);
                            stack.removeTagKey(UNDO);
                            report(player, "mobilebase.marker.undone");
                        }
                        stack.removeTagKey(DRAFT);
                    }
                    case STOP -> stack.removeTagKey(DRAFT);
                }
                changed(player);
            });
        });
    }

    private static void open(ServerPlayer user) {
        if (!ServerPlayNetworking.canSend(user, OPEN)) return;
        var buffer = PacketByteBufs.create();
        buffer.writeByte(user.getInventory().selected);
        ServerPlayNetworking.send(user, OPEN, buffer);
    }

    private static void mark(ServerLevel world, ServerPlayer player, ItemStack stack, Role role, BlockHitResult hit) {
        var target = hit.getBlockPos().relative(hit.getDirection());
        if (world.getBlockState(hit.getBlockPos()).isAir() || !BoatPlan.loaded(world, target)
                || !world.getWorldBorder().isWithinBounds(target)) {
            report(player, "mobilebase.marker.surface");
            return;
        }
        if (!available(world, role, target)) {
            report(player, "mobilebase.marker.occupied", target.toShortString());
            return;
        }
        var current = marks(stack, world);
        var heading = heading(hit, player.getDirection());
        if (role == Role.HEADING ? current.heading() != heading : !Objects.equals(current.position(role), target)) {
            remember(stack, world);
            var data = inDimension(stack, MARKS, world);
            if (data == null) {
                data = new CompoundTag();
                data.putString("dimension", dimension(world));
                stack.getOrCreateTag().put(MARKS, data);
            }
            if (role == Role.HEADING) data.putString(role.key(), heading.getName());
            else data.put(role.key(), NbtUtils.writeBlockPos(target));
        }
        if (role == Role.HEADING) report(player, "mobilebase.marker.heading_set", directionLabel(heading));
        else report(player, "mobilebase.marker.marked", role.label(), target.toShortString());
        stack.removeTagKey(DRAFT);
        changed(player);
    }

    static boolean available(Level world, Role role, BlockPos position) {
        if (world.isOutsideBuildHeight(position) || !world.getWorldBorder().isWithinBounds(position)) return false;
        var state = world.getBlockState(position);
        return role == Role.HEADING || state.isAir()
            || (role == Role.FLOATER && state.is(Blocks.WATER))
            || state.is(BoatPlan.part(role.partId))
            || (role == Role.HELM && state.getBlock() instanceof ShipHelmBlock);
    }

    static Direction heading(BlockHitResult hit, Direction facing) {
        return hit.getDirection().getAxis().isHorizontal() ? hit.getDirection() : facing;
    }

    static Component directionLabel(Direction direction) {
        return Component.translatable("mobilebase.direction." + direction.getName());
    }

    static Role editing(ItemStack stack, Level world) {
        var draft = inDimension(stack, DRAFT, world);
        if (draft == null) return null;
        int index = draft.getInt("role");
        return index >= 0 && index < Role.values().length ? Role.values()[index] : null;
    }

    static boolean canUndo(ItemStack stack, Level world) {
        return inDimension(stack, UNDO, world) != null;
    }

    static Marks marks(ItemStack stack, Level world) {
        var data = inDimension(stack, MARKS, world);
        if (data == null) return Marks.DEFAULT;
        var direction = Direction.byName(data.getString(Role.HEADING.key()));
        if (direction != null && !direction.getAxis().isHorizontal()) direction = null;
        return new Marks(read(data, Role.HELM), read(data, Role.ENGINE), read(data, Role.FLOATER), direction);
    }

    private static BlockPos read(CompoundTag data, Role role) {
        return data.contains(role.key(), Tag.TAG_COMPOUND) ? NbtUtils.readBlockPos(data.getCompound(role.key())) : null;
    }

    private static CompoundTag inDimension(ItemStack stack, String key, Level world) {
        var tag = stack.getTagElement(key);
        return tag != null && tag.getString("dimension").equals(dimension(world)) ? tag : null;
    }

    private static String dimension(Level world) {
        return world.dimension().location().toString();
    }

    private static void remember(ItemStack stack, Level world) {
        var undo = new CompoundTag();
        undo.putString("dimension", dimension(world));
        var previous = stack.getTagElement(MARKS);
        if (previous != null) undo.put(MARKS, previous.copy());
        stack.getOrCreateTag().put(UNDO, undo);
    }

    private static void changed(ServerPlayer player) {
        BoatCoreBlock.clearPreview(player);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static void report(Player player, String key, Object... arguments) {
        player.displayClientMessage(Component.translatable(key, arguments), true);
    }
}
