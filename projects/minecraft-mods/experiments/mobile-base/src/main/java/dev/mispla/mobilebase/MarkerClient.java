package dev.mispla.mobilebase;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mispla.mobilebase.BoatMarkerItem.Edit;
import dev.mispla.mobilebase.BoatMarkerItem.Role;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class MarkerClient implements ClientModInitializer {
    private static final RenderType GHOST = RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(BoatMarkerItem.OPEN, (client, handler, buffer, sender) -> {
            int slot = buffer.readUnsignedByte();
            client.execute(() -> {
                var player = client.player;
                if (client.screen == null && player != null && player.isCreative() && player.getInventory().selected == slot
                        && player.getMainHandItem().getItem() instanceof BoatMarkerItem) {
                    client.setScreen(new MarkerScreen(slot, player.getMainHandItem(), player.level()));
                }
            });
        });
        WorldRenderEvents.AFTER_ENTITIES.register(MarkerClient::render);
    }

    private static void render(WorldRenderContext context) {
        var client = Minecraft.getInstance();
        var player = client.player;
        var buffers = context.consumers();
        if (player == null || !player.isCreative() || buffers == null || client.options.hideGui) return;
        var stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof BoatMarkerItem)) return;

        var world = context.world();
        var marks = BoatMarkerItem.marks(stack, world);
        var editing = BoatMarkerItem.editing(stack, world);
        var heading = marks.heading() == null ? player.getDirection() : marks.heading();
        BlockHitResult hit = editing != null && client.screen == null && client.hitResult instanceof BlockHitResult block
            && block.getType() == HitResult.Type.BLOCK ? block : null;
        BlockPos target = hit == null ? null : BoatMarkerItem.target(hit, editing);

        var camera = context.camera().getPosition();
        var pose = context.matrixStack();
        var models = buffers.getBuffer(GHOST);
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        for (var role : Role.values()) {
            var position = marks.position(role);
            if (position == null || position.equals(target) || position.distToCenterSqr(camera.x, camera.y, camera.z) >= 64 * 64
                    || !world.hasChunkAt(position) || !BoatMarkerItem.available(world, role, position)) continue;
            var support = role == Role.FLOATER ? position.above() : position.below();
            if (role.partId == null || !world.getBlockState(support).getCollisionShape(world, support).isEmpty()) {
                renderPart(client, pose, models, role, position, heading, 0.4f);
            }
        }
        if (target != null && BoatMarkerItem.available(world, editing, target) && editing != Role.HEADING) {
            renderPart(client, pose, models, editing, target, heading, 0.66f);
        }
        if (target != null) {
            var lines = buffers.getBuffer(RenderType.lines());
            if (!BoatMarkerItem.available(world, editing, target)) {
                LevelRenderer.renderLineBox(pose, lines, new AABB(target).inflate(0.003), 1f, 0.29f, 0.21f, 0.9f);
            } else if (editing == Role.HEADING) {
                renderHeading(pose.last(), lines, target, BoatMarkerItem.heading(hit, player.getDirection()));
            } else {
                LevelRenderer.renderLineBox(pose, lines, new AABB(target).inflate(0.003), 0.31f, 0.91f, 1f, 0.65f);
            }
        }
        pose.popPose();
    }

    private static void renderPart(Minecraft client, PoseStack pose, VertexConsumer models, Role role, BlockPos position,
                                   Direction heading, float opacity) {
        var state = role.partId == null ? LandPlan.wheelPreview(heading) : BoatPlan.part(role.partId).defaultBlockState();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, role == Role.HELM ? heading.getOpposite() : heading);
        }
        var renderer = client.getBlockRenderer();
        pose.pushPose();
        pose.translate(position.getX(), position.getY(), position.getZ());
        renderer.getModelRenderer().renderModel(pose.last(), new GhostVertex(models, opacity), state,
            renderer.getBlockModel(state), 0.78f, 0.96f, 1f, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    private static void renderHeading(PoseStack.Pose pose, VertexConsumer lines, BlockPos position, Direction direction) {
        var center = new Vec3(position.getX() + 0.5, position.getY() + 0.55, position.getZ() + 0.5);
        var forward = Vec3.atLowerCornerOf(direction.getNormal());
        var side = Vec3.atLowerCornerOf(direction.getClockWise().getNormal());
        var tip = center.add(forward.scale(0.65));
        var head = center.add(forward.scale(0.26));
        line(pose, lines, center.subtract(forward.scale(0.54)), tip);
        line(pose, lines, head.add(side.scale(0.32)), tip);
        line(pose, lines, head.subtract(side.scale(0.32)), tip);
    }

    private static void line(PoseStack.Pose pose, VertexConsumer lines, Vec3 from, Vec3 to) {
        var tangent = to.subtract(from).normalize();
        for (var end : new Vec3[]{from, to}) {
            lines.vertex(pose.pose(), (float) end.x, (float) end.y, (float) end.z)
                .color(0.31f, 0.91f, 1f, 0.88f)
                .normal(pose.normal(), (float) tangent.x, (float) tangent.y, (float) tangent.z).endVertex();
        }
    }

    private record GhostVertex(VertexConsumer target, float opacity) implements VertexConsumer {
        @Override public VertexConsumer vertex(double x, double y, double z) { target.vertex(x, y, z); return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { target.color(r, g, b, Math.round(a * opacity)); return this; }
        @Override public VertexConsumer uv(float u, float v) { target.uv(u, v); return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { target.overlayCoords(u, v); return this; }
        @Override public VertexConsumer uv2(int u, int v) { target.uv2(u, v); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { target.normal(x, y, z); return this; }
        @Override public void endVertex() { target.endVertex(); }
        @Override public void defaultColor(int r, int g, int b, int a) { target.defaultColor(r, g, b, Math.round(a * opacity)); }
        @Override public void unsetDefaultColor() { target.unsetDefaultColor(); }
    }

    private static final class MarkerScreen extends Screen {
        private static final int PANEL_WIDTH = 282;
        private static final int PANEL_HEIGHT = 210;
        private final int slot;
        private final ItemStack stack;
        private final Level world;

        MarkerScreen(int slot, ItemStack stack, Level world) {
            super(Component.translatable("mobilebase.marker.menu"));
            this.slot = slot;
            this.stack = stack.copy();
            this.world = world;
        }

        @Override
        protected void init() {
            int left = (width - PANEL_WIDTH) / 2;
            int top = (height - PANEL_HEIGHT) / 2;
            var marks = BoatMarkerItem.marks(stack, world);
            int row = top + 40;
            for (var role : Role.values()) {
                var location = role == Role.HEADING
                    ? marks.heading() == null ? null : BoatMarkerItem.directionLabel(marks.heading())
                    : marks.position(role) == null ? null : Component.literal(marks.position(role).toShortString());
                var detail = location == null ? Component.translatable("mobilebase.marker.unset") : location;
                addRenderableWidget(Button.builder(Component.translatable("mobilebase.marker.row", role.label(), detail),
                        button -> send(Edit.SELECT, role)).bounds(left + 12, row, 204, 19).build());
                var remove = addRenderableWidget(Button.builder(Component.translatable("mobilebase.marker.remove"),
                        button -> send(Edit.REMOVE, role)).bounds(left + 222, row, 48, 19).build());
                remove.active = location != null;
                row += 22;
            }
            var undo = addRenderableWidget(Button.builder(Component.translatable("mobilebase.marker.undo"),
                    button -> send(Edit.UNDO, Role.HELM)).bounds(left + 12, top + 180, 125, 20).build());
            undo.active = BoatMarkerItem.canUndo(stack, world);
            addRenderableWidget(Button.builder(Component.translatable("mobilebase.marker.stop"),
                button -> send(Edit.STOP, Role.HELM)).bounds(left + 145, top + 180, 125, 20).build());
        }

        private void send(Edit edit, Role role) {
            var buffer = PacketByteBufs.create();
            buffer.writeByte(slot);
            buffer.writeByte(edit.ordinal());
            buffer.writeByte(role.ordinal());
            ClientPlayNetworking.send(BoatMarkerItem.EDIT, buffer);
            onClose();
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            renderBackground(graphics);
            int left = (width - PANEL_WIDTH) / 2;
            int top = (height - PANEL_HEIGHT) / 2;
            graphics.fill(left - 1, top - 1, left + PANEL_WIDTH + 1, top + PANEL_HEIGHT + 1, 0xFF58717C);
            graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFA15212B);
            graphics.fill(left, top, left + PANEL_WIDTH, top + 2, 0xFF68DAE7);
            graphics.drawCenteredString(font, title, width / 2, top + 10, 0xEBFBFF);
            graphics.drawCenteredString(font, Component.translatable("mobilebase.marker.help"), width / 2, top + 24, 0xB3CBD8);
            super.render(graphics, mouseX, mouseY, delta);
        }
    }
}
