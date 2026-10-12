package dev.mispla.abyss;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class GlimmeringAbyss implements ModInitializer {
    public static final RegistryKey<World> STUDY = RegistryKey.of(RegistryKeys.WORLD, id("study"));
    public static final Block DARK = new Block(AbstractBlock.Settings.copy(Blocks.DEEPSLATE).luminance(s -> 0));
    public static final Block GLIMMER = new Block(AbstractBlock.Settings.copy(Blocks.DEEPSLATE).luminance(s -> 5));
    public static final Block REFERENCE = new Block(AbstractBlock.Settings.copy(Blocks.DEEPSLATE).luminance(s -> 15));
    public static final Block WARM_WINDOW = new Block(AbstractBlock.Settings.copy(Blocks.DEEPSLATE).luminance(state -> 12));
    private static String mode = "glimmer";
    private static final java.util.Map<BlockPos,BlockState> lampBackup=new java.util.HashMap<>();
    private static long ticks, tickStart;
    private static double tickSum, tickMax;
    private static int tickSamples;
    private static final List<Terrain.Fixture> FIXTURES = Terrain.fixtures(Terrain.SEED);

    public record View(String name, double x, double y, double z, float yaw, float pitch) {}
    public static final List<View> VIEWS = List.of(
            new View("tunnel", 0.5, 39, 90.5, 180, 0),
            new View("platform", 0.5, 39, 73.5, 180, 12),
            new View("edge", 20.5, 39, 66.5, 165, 15));
    public static final View OVERLOOK = new View("overlook",0.5,55,60.5,180,28);

    public static Identifier id(String path) { return new Identifier("glimmeringabyss", path); }

    @Override public void onInitialize() {
        Registry.register(Registries.BLOCK, id("warm_window"), WARM_WINDOW);
        Registry.register(Registries.BLOCK, id("dark_marker"), DARK);
        Registry.register(Registries.BLOCK, id("glimmerstone"), GLIMMER);
        Registry.register(Registries.BLOCK, id("reference_marker"), REFERENCE);
        Registry.register(Registries.CHUNK_GENERATOR, id("cavern"), AbyssChunkGenerator.CODEC);

        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
                literal("abyss")
                .executes(c -> message(c.getSource(), "enter | view tunnel/platform/edge/overlook | light baseline/glimmer/reference | audit | return"))
                .then(literal("building").requires(s -> s.hasPermissionLevel(2))
                    .executes(c -> Architecture.place(c.getSource()))
                    .then(literal("audit").then(argument("label", StringArgumentType.word()).executes(c -> Architecture.audit(c.getSource(), StringArgumentType.getString(c,"label")))))
                    .then(literal("view").then(argument("name", StringArgumentType.word())
                        .suggests((c,b) -> { Architecture.VIEWS.forEach(v -> b.suggest(v.name()));return b.buildFuture(); })
                        .executes(c -> Architecture.view(c.getSource(),StringArgumentType.getString(c,"name"))))))
                .then(literal("enter").executes(c -> teleport(c.getSource(), VIEWS.get(0))))
                .then(literal("view").then(argument("name", StringArgumentType.word())
                        .suggests((c, b) -> { VIEWS.forEach(v -> b.suggest(v.name)); b.suggest("overlook"); return b.buildFuture(); })
                        .executes(c -> {
                            String name = StringArgumentType.getString(c, "name");
                            View view = name.equals("overlook") ? OVERLOOK : VIEWS.stream().filter(v -> v.name.equals(name)).findFirst().orElse(null);
                            if (view == null) return message(c.getSource(), "Unknown view: " + name);
                            return teleport(c.getSource(), view);
                        })))
                .then(literal("light").requires(s -> s.hasPermissionLevel(2))
                        .then(argument("mode", StringArgumentType.word())
                        .suggests((c, b) -> { for (String name : new String[]{"baseline", "glimmer", "reference"}) b.suggest(name); return b.buildFuture(); })
                        .executes(c -> changeLight(c.getSource(), StringArgumentType.getString(c, "mode")))))
                .then(literal("lamps").requires(s -> s.hasPermissionLevel(2)).then(argument("state",StringArgumentType.word())
                        .executes(c -> calibrationLamps(c.getSource(),StringArgumentType.getString(c,"state")))))
                .then(literal("audit").requires(s -> s.hasPermissionLevel(2)).executes(c -> audit(c.getSource(),""))
                        .then(argument("label",StringArgumentType.word()).executes(c -> audit(c.getSource(),StringArgumentType.getString(c,"label")))))
                .then(literal("return").executes(c -> {
                    ServerPlayerEntity player = c.getSource().getPlayerOrThrow();
                    ServerWorld world = c.getSource().getServer().getOverworld();
                    BlockPos pos = world.getSpawnPos();
                    player.getAbilities().flying = false;
                    player.sendAbilitiesUpdate();
                    player.teleport(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 0, 0);
                    return 1;
                }))));

        // Test-only operator access is limited to the isolated localhost test server.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (Boolean.getBoolean("abyss.testServer")) server.getPlayerManager().addToOperators(handler.player.getGameProfile());
        });
        ServerTickEvents.START_SERVER_TICK.register(server -> tickStart = System.nanoTime());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            double millis = (System.nanoTime() - tickStart) / 1e6;
            tickSum += millis; tickMax = Math.max(tickMax, millis); tickSamples++; ticks++;
            if (Boolean.getBoolean("abyss.testServer") && ticks % 20 == 0) {
                Path request = Path.of("abyss-command.txt");
                if (Files.isRegularFile(request)) try {
                    List<String> commands = Files.readAllLines(request); Files.delete(request);
                    for (String command : commands) {
                        if (command.equals("stop")) server.stop(false);
                        else if (command.startsWith("abyss ")) server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
                    }
                } catch (Exception error) { error.printStackTrace(); }
            }
        });
    }

    private static ServerWorld study(MinecraftServer server) {
        ServerWorld world = server.getWorld(STUDY);
        if (world == null) throw new IllegalStateException("Study dimension did not load; use Minecraft 1.20.1 with this mod's data enabled.");
        return world;
    }

    private static int message(ServerCommandSource source, String text) {
        source.sendFeedback(() -> Text.literal("[Abyss] " + text), false);
        return 1;
    }

    private static int teleport(ServerCommandSource source, View view) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        if (view == OVERLOOK && !player.isCreative()) return message(source, "Overlook requires Creative mode.");
        player.getAbilities().flying = view == OVERLOOK;
        player.sendAbilitiesUpdate();
        player.teleport(study(source.getServer()), view.x, view.y, view.z, view.yaw, view.pitch);
        return message(source, "View " + view.name + " / study seed " + Terrain.SEED);
    }

    private static int changeLight(ServerCommandSource source, String requested) {
        Block block = switch (requested) {
            case "baseline" -> DARK;
            case "glimmer" -> GLIMMER;
            case "reference" -> REFERENCE;
            default -> null;
        };
        if (block == null) return message(source, "Use baseline, glimmer or reference.");
        ServerWorld world = study(source.getServer());
        if (Boolean.getBoolean("abyss.testServer")) {
            // Without a player, unloaded light data reads as zero. Hold only the
            // sealed calibration lane and its sampled neighbor in this test runtime.
            world.setChunkForced(-5, 6, true);
            world.setChunkForced(-4, 6, true);
        }
        for (Terrain.Fixture fixture : FIXTURES) {
            world.setBlockState(new BlockPos(fixture.x(), fixture.y(), fixture.z()), block.getDefaultState(), Block.NOTIFY_ALL);
        }
        mode = requested;
        tickSum = tickMax = 0; tickSamples = 0;
        return message(source, "Light " + mode + " / " + FIXTURES.size() + " emitters. Wait for lighting/chunks to settle before audit.");
    }

    private static int calibrationLamps(ServerCommandSource source,String state) {
        ServerWorld world=study(source.getServer());
        if(state.equals("on")) {
            for(int x:new int[]{-16,16})for(int z:new int[]{68,78}) {
                BlockPos pos=new BlockPos(x,39,z);lampBackup.putIfAbsent(pos,world.getBlockState(pos));
                world.setBlockState(pos,Blocks.LANTERN.getDefaultState(),Block.NOTIFY_ALL);
            }
        } else if(state.equals("off")) {
            lampBackup.forEach((pos,original)->world.setBlockState(pos,original,Block.NOTIFY_ALL));lampBackup.clear();
        } else return message(source,"lamps on/off: temporary native lantern calibration only");
        return message(source,"Native calibration lanterns "+state);
    }

    private static int audit(ServerCommandSource source,String label) {
        try {
            if(!label.matches("[a-zA-Z0-9_-]{0,40}"))throw new IllegalArgumentException("Invalid audit label");
            ServerWorld world = study(source.getServer());
            JsonObject result = new JsonObject();
            result.addProperty("minecraft", "1.20.1");
            result.addProperty("dimension", STUDY.getValue().toString());
            result.addProperty("study_seed", Terrain.SEED);
            result.addProperty("world_seed", world.getSeed());
            result.addProperty("light_mode", mode);
            result.addProperty("emitter_count", FIXTURES.size());
            result.addProperty("dimension_has_skylight", world.getDimension().hasSkyLight());
            result.addProperty("ambient_light", world.getDimension().ambientLight());
            result.addProperty("calibration_chunks_kept_loaded_for_test_server", Boolean.getBoolean("abyss.testServer"));
            JsonArray profile = new JsonArray();
            int strength = mode.equals("reference") ? 15 : mode.equals("glimmer") ? 5 : 0;
            boolean lightPass = true;
            for (int distance = 0; distance <= 16; distance++) {
                BlockPos pos = new BlockPos(-78 + distance, 41, 100);
                int block = world.getLightLevel(LightType.BLOCK, pos);
                int sky = world.getLightLevel(LightType.SKY, pos);
                JsonObject sample = new JsonObject();
                sample.addProperty("distance", distance); sample.addProperty("block", block); sample.addProperty("sky", sky);
                sample.addProperty("expected_unobstructed_block", Math.max(0, strength - distance));
                profile.add(sample);
                lightPass &= block == Math.max(0, strength - distance) && sky == 0;
            }
            result.add("light_profile", profile);
            result.addProperty("native_light_profile_pass", lightPass);

            JsonArray views = new JsonArray();
            boolean routePass = true;
            for (View view : VIEWS) {
                BlockPos foot = BlockPos.ofFloored(view.x, view.y, view.z);
                boolean floor = world.getBlockState(foot.down()).isSolidBlock(world, foot.down());
                boolean clearance = world.getBlockState(foot).isAir() && world.getBlockState(foot.up()).isAir();
                JsonObject check = new JsonObject();
                check.addProperty("name", view.name); check.addProperty("x", view.x); check.addProperty("y", view.y); check.addProperty("z", view.z);
                check.addProperty("yaw", view.yaw); check.addProperty("pitch", view.pitch);
                check.addProperty("floor_solid", floor); check.addProperty("two_block_clearance", clearance);
                check.addProperty("block_light_at_feet", world.getLightLevel(LightType.BLOCK, foot));
                check.addProperty("sky_light_at_feet", world.getLightLevel(LightType.SKY, foot));
                routePass &= floor && clearance;
                views.add(check);
            }
            for (int z = 78; z <= 111; z++) {
                routePass &= world.getBlockState(new BlockPos(0, 38, z)).isSolidBlock(world, new BlockPos(0, 38, z));
                routePass &= world.getBlockState(new BlockPos(0, 39, z)).isAir();
            }
            for (int x = -22; x <= 22; x++) for (int y = 39; y <= 40; y++) {
                routePass &= world.getBlockState(new BlockPos(x, y, 62)).isOf(Blocks.IRON_BARS);
                if (Math.abs(x) > 3) routePass &= world.getBlockState(new BlockPos(x, y, 77)).isOf(Blocks.IRON_BARS);
            }
            for (int z = 62; z <= 77; z++) for (int x : new int[]{-22, 22}) for (int y = 39; y <= 40; y++) {
                routePass &= world.getBlockState(new BlockPos(x, y, z)).isOf(Blocks.IRON_BARS);
            }
            result.add("views", views); result.addProperty("route_and_rail_pass", routePass);

            // Read generated world data, not the pure terrain function. Normalize lamp luminance only.
            Set<Long> lamps = FIXTURES.stream().map(f -> BlockPos.asLong(f.x(), f.y(), f.z())).collect(Collectors.toSet());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            int samples = 0;
            for (int x = -104; x <= 104; x += 4) for (int z = -164; z <= 112; z += 4)
                for (int y = -64; y <= 124; y += 4) {
                    BlockPos pos = new BlockPos(x, y, z);
                    String state = lamps.contains(pos.asLong()) ? "fixture" : world.getBlockState(pos).toString();
                    digest.update(state.getBytes(StandardCharsets.UTF_8)); digest.update((byte) 0); samples++;
                }
            result.addProperty("generated_world_sample_count", samples);
            result.addProperty("generated_terrain_sha256", HexFormat.of().formatHex(digest.digest()));
            result.addProperty("server_tick_samples_since_mode_change", tickSamples);
            result.addProperty("server_tick_mean_ms", tickSamples == 0 ? 0 : tickSum / tickSamples);
            result.addProperty("server_tick_max_ms", tickMax);
            result.addProperty("server_total_ticks", ticks);
            result.addProperty("lighting_pending_at_audit_end", world.getChunkManager().getLightingProvider().hasUpdates());
            Path directory = Path.of("abyss-results"); Files.createDirectories(directory);
            Path path = directory.resolve("audit-" + mode + (label.isEmpty()?"":"-"+label) + ".json");
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(result));
            message(source, "Audit " + path + " / light=" + lightPass + " route=" + routePass);
            return lightPass && routePass ? 1 : 0;
        } catch (Exception error) {
            error.printStackTrace();
            source.sendError(Text.literal("[Abyss] Audit failed: " + error.getMessage()));
            return 0;
        }
    }
}
