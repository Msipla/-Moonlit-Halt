package dev.mispla.redstonemusic;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MusicDeployClient implements ClientModInitializer {
    private static final String TOKEN = "MoonlitMusicProject";
    private static final Path IMPORTS = FabricLoader.getInstance().getConfigDir().resolve("redstone-music");
    private final Map<String, MusicProject> projects = new HashMap<>();
    private final Minecraft client = Minecraft.getInstance();
    private String lastHeldProject = "";

    @Override
    public void onInitializeClient() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.isClientSide || hand != InteractionHand.MAIN_HAND || !player.isCreative()
                || client.getSingleplayerServer() == null) {
                return InteractionResult.PASS;
            }
            ItemStack held = player.getMainHandItem();
            String projectId = reference(held);
            if (!projectId.isEmpty()) {
                MusicProject project = find(projectId);
                if (project == null) {
                    player.displayClientMessage(Component.translatable("music.deploy.missing"), true);
                } else {
                    deploy(project, player.getUUID(), hit.getBlockPos().relative(hit.getDirection()),
                           player.getDirection(), level.dimension());
                }
                return InteractionResult.CONSUME;
            }
            if (player.isShiftKeyDown() && level.getBlockState(hit.getBlockPos()).is(BlockTags.ANVIL)) {
                client.setScreen(new ImportScreen());
                return InteractionResult.CONSUME;
            }
            return InteractionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            var held = player.getItemInHand(hand);
            if (level.isClientSide && hand == InteractionHand.MAIN_HAND && player.isCreative()
                && client.getSingleplayerServer() != null && !reference(held).isEmpty()
                && (client.hitResult == null || client.hitResult.getType() == HitResult.Type.MISS)) {
                MusicProject project = find(reference(held));
                if (project == null) {
                    player.displayClientMessage(Component.translatable("music.deploy.missing"), true);
                } else {
                    client.setScreen(new InfoScreen(held.getHoverName(), project));
                }
                return InteractionResultHolder.consume(held);
            }
            return InteractionResultHolder.pass(held);
        });
        ClientTickEvents.END_CLIENT_TICK.register(game -> {
            String held = game.player == null || game.getSingleplayerServer() == null
                ? "" : reference(game.player.getMainHandItem());
            if (!held.equals(lastHeldProject) && !held.isEmpty()) {
                find(held);
            }
            lastHeldProject = held;
        });
        WorldRenderEvents.AFTER_ENTITIES.register(this::renderPreview);
    }

    private static String reference(ItemStack stack) {
        return stack.is(Items.PAPER) && stack.hasTag() ? stack.getTag().getString(TOKEN) : "";
    }

    private static StructurePlaceSettings settings(Direction direction) {
        Rotation rotation = switch (direction) {
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        return new StructurePlaceSettings().setRotation(rotation).setIgnoreEntities(true);
    }

    private void renderPreview(WorldRenderContext context) {
        if (client.player == null || !client.player.isCreative() || client.getSingleplayerServer() == null
            || !(client.hitResult instanceof BlockHitResult hit)
            || hit.getType() != HitResult.Type.BLOCK || context.consumers() == null) {
            return;
        }
        // The preview may run every frame. Resolve files on interaction or world entry.
        String id = reference(client.player.getMainHandItem());
        MusicProject project = projects.get(id);
        if (project == null) {
            return;
        }
        BlockPos origin = hit.getBlockPos().relative(hit.getDirection());
        StructurePlaceSettings rules = settings(client.player.getDirection());
        BoundingBox box = project.template().getBoundingBox(rules, origin);

        PoseStack poses = context.matrixStack();
        var camera = context.camera().getPosition();
        var lines = context.consumers().getBuffer(RenderType.lines());
        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        LevelRenderer.renderLineBox(poses, lines, AABB.of(box).inflate(.015),
                                    .20f, .83f, 1.0f, .70f);
        if (project.noteBlocks().size() <= 350) {
            for (BlockPos note : project.noteBlocks()) {
                BlockPos spot = StructureTemplate.calculateRelativePosition(rules, note).offset(origin);
                LevelRenderer.renderLineBox(poses, lines, new AABB(spot).deflate(.08),
                                            .49f, .95f, .69f, .40f);
            }
        }
        poses.popPose();
    }

    private void deploy(MusicProject project, java.util.UUID playerId, BlockPos origin, Direction direction,
                        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        var server = client.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || !player.isCreative() || !player.level().dimension().equals(dimension)
                || !project.reference().equals(reference(player.getMainHandItem()))) {
                return;
            }
            ServerLevel world = player.serverLevel();
            StructurePlaceSettings rules = settings(direction);
            BoundingBox box = project.template().getBoundingBox(rules, origin);
            BlockPos min = new BlockPos(box.minX(), box.minY(), box.minZ());
            BlockPos max = new BlockPos(box.maxX(), box.maxY(), box.maxZ());
            if (world.isOutsideBuildHeight(min) || world.isOutsideBuildHeight(max)
                || !world.getWorldBorder().isWithinBounds(min) || !world.getWorldBorder().isWithinBounds(max)
                || !world.hasChunksAt(min, max)) {
                player.displayClientMessage(Component.translatable("music.deploy.bounds"), true);
                return;
            }
            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                if (!world.getBlockState(pos).isAir()) {
                    player.displayClientMessage(Component.translatable("music.deploy.occupied",
                        pos.getX(), pos.getY(), pos.getZ()), true);
                    return;
                }
            }
            if (!world.getEntitiesOfClass(Entity.class, AABB.of(box), entity -> !entity.isSpectator()).isEmpty()) {
                player.displayClientMessage(Component.translatable("music.deploy.entities"), true);
                return;
            }
            boolean placed = project.template().placeInWorld(world, origin, origin, rules,
                                                               world.getRandom(), Block.UPDATE_CLIENTS);
            player.displayClientMessage(Component.translatable(placed ? "music.deploy.placed" : "music.deploy.failed",
                                                               project.title()), true);
        });
    }

    private MusicProject find(String sha) {
        if (!projects.containsKey(sha)) {
            scan();
        }
        return projects.get(sha);
    }

    private record ImportEntry(String file, MusicProject project, String error) {}

    private List<ImportEntry> scan() {
        projects.clear();
        List<ImportEntry> entries = new ArrayList<>();
        try {
            Files.createDirectories(IMPORTS);
            try (var files = Files.list(IMPORTS)) {
                for (Path metadata : files.filter(path -> path.getFileName().toString().endsWith(".json"))
                                          .sorted(Comparator.naturalOrder()).limit(32).toList()) {
                    String name = metadata.getFileName().toString().replaceFirst("\\.json$", "");
                    Path structure = IMPORTS.resolve(name + ".nbt");
                    if (Files.isRegularFile(metadata) && Files.isRegularFile(structure)) {
                        try {
                            MusicProject project = MusicProject.read(metadata, structure);
                            projects.put(project.reference(), project);
                            entries.add(new ImportEntry(name, project, ""));
                        } catch (IOException | RuntimeException invalid) {
                            entries.add(new ImportEntry(name, null, String.valueOf(invalid.getMessage())));
                        }
                    }
                }
            }
        } catch (IOException unreadable) {
            entries.add(new ImportEntry(IMPORTS.toString(), null, unreadable.getMessage()));
        }
        return List.copyOf(entries);
    }

    private void issue(MusicProject project, String name) {
        var server = client.getSingleplayerServer();
        if (server == null || client.player == null) {
            return;
        }
        var playerId = client.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(playerId);
            if (player == null || !player.isCreative()) {
                return;
            }
            ItemStack token = new ItemStack(Items.PAPER);
            token.getOrCreateTag().putString(TOKEN, project.reference());
            token.setHoverName(Component.literal(name));
            if (!player.getInventory().add(token)) {
                player.displayClientMessage(Component.translatable("music.deploy.inventory"), true);
            }
        });
    }

    private final class ImportScreen extends Screen {
        private List<ImportEntry> entries = List.of();
        private int selected;
        private Button cycle;
        private Button confirm;
        private EditBox name;

        ImportScreen() {
            super(Component.translatable("music.import.title"));
        }

        @Override
        protected void init() {
            int left = width / 2 - 116;
            int top = height / 2 - 54;
            cycle = addRenderableWidget(Button.builder(Component.empty(), button -> {
                if (!entries.isEmpty()) {
                    selected = (selected + 1) % entries.size();
                    showSelection();
                }
            }).bounds(left, top, 232, 20).build());
            name = addRenderableWidget(new EditBox(font, left, top + 26, 232, 20,
                                                    Component.translatable("music.import.name")));
            name.setMaxLength(80);
            name.setResponder(text -> updateConfirm());
            confirm = addRenderableWidget(Button.builder(Component.translatable("music.import.create"), button -> {
                String label = name.getValue().strip();
                if (selectedProject() != null && !label.isEmpty()) {
                    issue(selectedProject(), label);
                    onClose();
                }
            }).bounds(left, top + 58, 145, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("music.import.refresh"), button -> refresh())
                                        .bounds(left + 151, top + 58, 81, 20).build());
            refresh();
        }

        private void refresh() {
            entries = scan();
            selected = 0;
            showSelection();
        }

        private MusicProject selectedProject() {
            return entries.isEmpty() ? null : entries.get(selected).project();
        }

        private void showSelection() {
            cycle.setMessage(entries.isEmpty() ? Component.translatable("music.import.empty") :
                Component.literal((selected + 1) + "/" + entries.size() + "  " + entries.get(selected).file()));
            cycle.active = entries.size() > 1;
            name.setValue(selectedProject() == null ? "" : selectedProject().title());
            updateConfirm();
        }

        private void updateConfirm() {
            if (confirm != null) {
                confirm.active = selectedProject() != null && !name.getValue().strip().isEmpty();
            }
        }

        @Override
        public void render(GuiGraphics graphics, int x, int y, float delta) {
            renderBackground(graphics);
            graphics.drawCenteredString(font, title, width / 2, height / 2 - 75, 0xffffff);
            graphics.drawCenteredString(font, Component.literal("config/redstone-music/"),
                                        width / 2, height / 2 + 37, 0xa0bfd6);
            if (!entries.isEmpty() && selectedProject() == null) {
                graphics.drawString(font, font.plainSubstrByWidth(entries.get(selected).error(), width - 26),
                                    13, height / 2 + 53, 0xff8b8b);
            }
            super.render(graphics, x, y, delta);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    private final class InfoScreen extends Screen {
        private final MusicProject project;

        InfoScreen(Component title, MusicProject project) {
            super(title);
            this.project = project;
        }

        @Override
        protected void init() {
            if (project.pack() != null && project.pack().url() != null) {
                String url = project.pack().url().toString();
                addRenderableWidget(Button.builder(Component.translatable("music.info.link"), button ->
                    client.setScreen(new ConfirmLinkScreen(open -> {
                        if (open) {
                            net.minecraft.Util.getPlatform().openUri(project.pack().url());
                        }
                        client.setScreen(this);
                    }, url, false))).bounds(width / 2 - 88, height / 2 + 35, 176, 20).build());
            }
        }

        @Override
        public void render(GuiGraphics graphics, int x, int y, float delta) {
            renderBackground(graphics);
            int top = height / 2 - 47;
            graphics.drawCenteredString(font, title, width / 2, top, 0xffffff);
            graphics.drawCenteredString(font, Component.translatable("music.info.instruments"),
                                        width / 2, top + 17, 0xa0bfd6);
            String instruments = String.join(", ", project.instruments());
            graphics.drawWordWrap(font, Component.literal(instruments), 16, top + 29, width - 32, 0xffffff);
            MusicProject.Pack pack = project.pack();
            Component note = pack == null ? Component.translatable("music.info.vanilla") :
                Component.translatable("music.info.pack", pack.name(), pack.version());
            graphics.drawCenteredString(font, note, width / 2, top + 61, 0xffffff);
            super.render(graphics, x, y, delta);
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
