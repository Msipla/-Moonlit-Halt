package dev.mispla.abyss;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Opt-in dev capture harness; inactive in normal play. Reads the real game framebuffer. */
public final class CaptureClient implements ClientModInitializer {
    private static final String[] MODES = {"baseline", "glimmer", "reference"};
    private boolean connected, entered, finished;
    private int phase = -1;
    private long started, phaseStarted, lastFrame;
    private long walkStarted;
    private double walkStartZ;
    private boolean walking;
    private final List<Double> frameMillis = new ArrayList<>();
    private final JsonArray captures = new JsonArray();

    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("abyss.capture")) return;
        started = System.nanoTime();
        WorldRenderEvents.END.register(context -> {
            long now = System.nanoTime();
            if (phase >= 0 && now - phaseStarted > 8_000_000_000L && lastFrame != 0) {
                frameMillis.add((now - lastFrame) / 1e6);
            }
            lastFrame = now;
        });
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(MinecraftClient client) {
        if (finished) return;
        if (!connected && client.currentScreen != null
                && client.currentScreen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen")) {
            client.setScreen(new TitleScreen());
        }
        if (System.nanoTime() - started > 600_000_000_000L) {
            finished = true;
            System.err.println("ABYSS_CAPTURE_FAILED timeout");
            client.scheduleStop();
            return;
        }
        if (!connected && client.currentScreen instanceof TitleScreen && client.getOverlay() == null) {
            connected = true;
            client.options.getViewDistance().setValue(12);
            client.options.getSimulationDistance().setValue(6);
            client.options.getFov().setValue(75);
            client.options.getGamma().setValue(0.5);
            client.options.getMaxFps().setValue(60);
            client.options.getEnableVsync().setValue(false);
            client.options.pauseOnLostFocus = false;
            client.options.hudHidden = true;
            client.options.write();
            String address = "127.0.0.1:" + Integer.getInteger("abyss.port", 25585);
            ConnectScreen.connect(new TitleScreen(), client, ServerAddress.parse(address),
                    new ServerInfo("Abyss local test", address, false), false);
            return;
        }
        if (client.player == null || client.world == null || client.getNetworkHandler() == null) return;
        if (!entered) {
            entered = true;
            client.getNetworkHandler().sendChatCommand("abyss enter");
            return;
        }
        if (!client.world.getRegistryKey().equals(GlimmeringAbyss.STUDY)) return;
        if (phase >= 9) { walkTest(client); return; }
        if (phase == -1) { next(client); return; }
        if (System.nanoTime() - phaseStarted < 13_000_000_000L) return;

        String mode = MODES[phase / 3];
        GlimmeringAbyss.View view = GlimmeringAbyss.VIEWS.get(phase % 3);
        try {
            Path out = Path.of("abyss-captures"); Files.createDirectories(out);
            String filename = mode + "-" + view.name() + ".png";
            try (NativeImage image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
                image.writeTo(out.resolve(filename));
            }
            JsonObject capture = new JsonObject();
            capture.addProperty("file", filename); capture.addProperty("light_mode", mode);
            capture.addProperty("view", view.name());
            capture.addProperty("x", client.player.getX()); capture.addProperty("y", client.player.getY()); capture.addProperty("z", client.player.getZ());
            capture.addProperty("yaw", client.player.getYaw()); capture.addProperty("pitch", client.player.getPitch());
            capture.addProperty("framebuffer_width", client.getFramebuffer().textureWidth);
            capture.addProperty("framebuffer_height", client.getFramebuffer().textureHeight);
            capture.addProperty("fov", client.options.getFov().getValue());
            capture.addProperty("brightness_gamma", client.options.getGamma().getValue());
            capture.addProperty("view_distance_chunks", client.options.getViewDistance().getValue());
            capture.addProperty("simulation_distance_chunks", client.options.getSimulationDistance().getValue());
            capture.addProperty("graphics", client.options.getGraphicsMode().getValue().toString());
            capture.addProperty("ambient_occlusion", client.options.getAo().getValue().toString());
            capture.addProperty("gl_renderer", GL11.glGetString(GL11.GL_RENDERER));
            capture.addProperty("gl_vendor", GL11.glGetString(GL11.GL_VENDOR));
            capture.addProperty("gl_version", GL11.glGetString(GL11.GL_VERSION));
            capture.addProperty("chunk_debug", client.worldRenderer.getChunksDebugString());
            capture.addProperty("sample_frames", frameMillis.size());
            capture.addProperty("measurement", "Real world-render frame intervals in a 5-second window after 8 seconds of settling; software GPU, not user hardware.");
            if (!frameMillis.isEmpty()) {
                double sum = frameMillis.stream().mapToDouble(Double::doubleValue).sum();
                Collections.sort(frameMillis);
                capture.addProperty("sample_fps", frameMillis.size() * 1000.0 / sum);
                capture.addProperty("median_frame_ms", frameMillis.get(frameMillis.size() / 2));
                capture.addProperty("p95_frame_ms", frameMillis.get((int) Math.floor((frameMillis.size() - 1) * 0.95)));
                capture.addProperty("max_frame_ms", frameMillis.get(frameMillis.size() - 1));
            }
            captures.add(capture);
            Files.writeString(out.resolve("captures.json"), new GsonBuilder().setPrettyPrinting().create().toJson(captures));
            System.out.println("ABYSS_CAPTURE_SAVED " + filename);
            if (phase % 3 == 2) client.getNetworkHandler().sendChatCommand("abyss audit");
            next(client);
        } catch (Exception error) {
            error.printStackTrace(); finished = true; client.scheduleStop();
        }
    }

    private void next(MinecraftClient client) {
        phase++;
        if (phase >= MODES.length * GlimmeringAbyss.VIEWS.size()) {
            client.getNetworkHandler().sendChatCommand("abyss view tunnel");
            walkStarted = System.nanoTime();
            return;
        }
        client.getNetworkHandler().sendChatCommand("abyss light " + MODES[phase / 3]);
        client.getNetworkHandler().sendChatCommand("abyss view " + GlimmeringAbyss.VIEWS.get(phase % 3).name());
        phaseStarted = System.nanoTime(); lastFrame = 0; frameMillis.clear();
    }

    private void walkTest(MinecraftClient client) {
        long elapsed = System.nanoTime() - walkStarted;
        if (!walking && elapsed > 2_000_000_000L) {
            walking = true; walkStartZ = client.player.getZ();
            client.options.forwardKey.setPressed(true);
        }
        if (elapsed < 10_000_000_000L) return;
        client.options.forwardKey.setPressed(false);
        double z = client.player.getZ(), y = client.player.getY();
        boolean passed = walkStartZ - z > 20 && z > 62.7 && z < 64 && Math.abs(y - 39) < 0.01;
        JsonObject result = new JsonObject();
        result.addProperty("method", "Actual Minecraft player input: hold forward for 8 seconds from tunnel; stop at generated two-block iron railing.");
        result.addProperty("start_z", walkStartZ); result.addProperty("end_z", z); result.addProperty("end_y", y);
        result.addProperty("walk_and_collision_pass", passed);
        try {
            Files.writeString(Path.of("abyss-captures/walk-collision.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (Exception error) { error.printStackTrace(); }
        finished = true;
        System.out.println("ABYSS_CAPTURE_COMPLETE " + captures.size() + " walk_collision=" + passed);
        client.scheduleStop();
    }
}
