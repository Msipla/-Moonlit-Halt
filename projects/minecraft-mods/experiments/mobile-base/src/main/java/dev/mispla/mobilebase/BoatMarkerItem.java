package dev.mispla.mobilebase;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

final class BoatMarkerItem extends Item {
    enum Role {
        HELM, ENGINE, FLOATER, HEADING;

        String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        Component label() {
            return Component.translatable("mobilebase.marker." + key());
        }
    }

    record Marks(BlockPos helm, BlockPos engine, BlockPos floater, Direction heading) {
        static final Marks DEFAULT = new Marks(null, null, null, null);
    }

    BoatMarkerItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(), role(stack).label());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!player.isCreative() || !player.isShiftKeyDown() || hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) cycle(stack, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null || !player.isCreative() || context.getHand() != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel world) || !(player instanceof ServerPlayer user)) {
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }

        var pos = context.getClickedPos();
        var state = world.getBlockState(pos);
        var stack = context.getItemInHand();
        if (state.is(MobileBase.CORE)) {
            if (user.isShiftKeyDown()) {
                stack.removeTagKey("BoatMarks");
                BoatCoreBlock.clearPreview(user);
                report(user, "mobilebase.marker.cleared");
            } else {
                BoatCoreBlock.launchWithMarks(world, pos, state, user, marks(stack, world));
            }
        } else if (user.isShiftKeyDown()) {
            cycle(stack, user);
        } else if (world.getBlockEntity(pos) == null && !state.isAir()) {
            var role = role(stack);
            var data = stack.getOrCreateTagElement("BoatMarks");
            var dimension = world.dimension().location().toString();
            if (!data.getString("dimension").equals(dimension)) {
                stack.removeTagKey("BoatMarks");
                data = stack.getOrCreateTagElement("BoatMarks");
                data.putString("dimension", dimension);
            }
            if (role == Role.HEADING) {
                var clicked = context.getClickedFace();
                var dir = clicked.getAxis().isHorizontal() ? clicked : context.getHorizontalDirection();
                data.putString(role.key(), dir.getName());
                report(user, "mobilebase.marker.heading_set", Component.translatable("mobilebase.direction." + dir.getName()));
            } else {
                var target = pos.relative(context.getClickedFace());
                data.put(role.key(), NbtUtils.writeBlockPos(target));
                report(user, "mobilebase.marker.marked", role.label(), target.toShortString());
            }
            BoatCoreBlock.clearPreview(user);
        } else {
            report(user, "mobilebase.marker.surface");
        }
        return InteractionResult.CONSUME;
    }

    private static Marks marks(ItemStack stack, ServerLevel world) {
        var data = stack.getTagElement("BoatMarks");
        if (data == null || !data.getString("dimension").equals(world.dimension().location().toString())) {
            return Marks.DEFAULT;
        }
        return new Marks(read(data, Role.HELM), read(data, Role.ENGINE), read(data, Role.FLOATER),
            Direction.byName(data.getString(Role.HEADING.key())));
    }

    private static BlockPos read(net.minecraft.nbt.CompoundTag data, Role role) {
        return data.contains(role.key(), Tag.TAG_COMPOUND) ? NbtUtils.readBlockPos(data.getCompound(role.key())) : null;
    }

    private static Role role(ItemStack stack) {
        var tag = stack.getTag();
        return Role.values()[tag == null ? 0 : Math.floorMod(tag.getInt("BoatRole"), Role.values().length)];
    }

    private static void cycle(ItemStack stack, Player player) {
        var next = Role.values()[(role(stack).ordinal() + 1) % Role.values().length];
        stack.getOrCreateTag().putInt("BoatRole", next.ordinal());
        report(player, "mobilebase.marker.mode", next.label());
    }

    private static void report(Player player, String key, Object... arguments) {
        player.displayClientMessage(Component.translatable(key, arguments), true);
    }
}
