package dev.mispla.abyss;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import org.lwjgl.opengl.GL11;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Opt-in same-session real Minecraft framebuffer capture and movement checks. */
public final class Phase2CaptureClient implements ClientModInitializer {
    private record Case(String file,String profile,GlimmeringAbyss.View view,String light,String kind) {}
    private static final List<Case> CASES=cases();
    private static final Path OUT=Path.of("abyss-phase2-captures");
    private final JsonArray captures=new JsonArray();
    private final List<Double> frames=new ArrayList<>();
    private boolean connected,entered,finished,saved,walking,prepared;
    private int phase=-1,stability;
    private long started,phaseStarted,lastFrame,walkStarted;
    private double walkStartZ;
    private static List<Case> cases() {
        var result=new ArrayList<Case>();var views=new ArrayList<>(GlimmeringAbyss.VIEWS);views.add(GlimmeringAbyss.OVERLOOK);
        for(String p:new String[]{"a","b","c"})for(var v:views)result.add(new Case(p+"-"+v.name()+".png",p,v,"glimmer","comparison"));
        result.add(new Case("veil-platform.png","veil",GlimmeringAbyss.VIEWS.get(1),"glimmer","failure"));
        for(String p:new String[]{"b","c"})result.add(new Case(p+"-no-sources.png",p,GlimmeringAbyss.VIEWS.get(1),"baseline","natural_without_sources"));
        result.add(new Case("overworld-b.png","b",null,"glimmer","dimension_scope"));
        result.add(new Case("overworld-a.png","a",null,"glimmer","dimension_scope"));return List.copyOf(result);
    }
    @Override public void onInitializeClient() {
        if(!Boolean.getBoolean("abyss.capture.phase2"))return;started=System.nanoTime();
        WorldRenderEvents.END.register(c->{long now=System.nanoTime();if(phase>=0&&phase<CASES.size()&&!saved&&now-phaseStarted>8_000_000_000L&&lastFrame!=0)frames.add((now-lastFrame)/1e6);lastFrame=now;});
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private void tick(MinecraftClient client) {
        if(finished)return;
        if(System.nanoTime()-started>700_000_000_000L){fail(client,new IllegalStateException("Capture timeout"));return;}
        if(!connected&&client.currentScreen!=null&&client.currentScreen.getClass().getSimpleName().equals("AccessibilityOnboardingScreen"))client.setScreen(new TitleScreen());
        if(!connected&&client.currentScreen instanceof TitleScreen&&client.getOverlay()==null) {
            connected=true;client.options.getViewDistance().setValue(16);client.options.getSimulationDistance().setValue(6);
            client.options.getFov().setValue(75);client.options.getGamma().setValue(.5);client.options.getMaxFps().setValue(60);
            client.options.getEnableVsync().setValue(false);client.options.getGraphicsMode().setValue(GraphicsMode.FANCY);client.options.getAo().setValue(true);
            client.options.pauseOnLostFocus=false;client.options.hudHidden=true;client.options.write();
            String address="127.0.0.1:"+Integer.getInteger("abyss.port",25585);
            ConnectScreen.connect(new TitleScreen(),client,ServerAddress.parse(address),new ServerInfo("Abyss phase 2 local test",address,false),false);return;
        }
        if(client.player==null||client.world==null||client.getNetworkHandler()==null)return;
        if(!entered){entered=true;command(client,"abyss enter");return;}
        if(phase==-1){if(EnvironmentClient.studyActive()&&ClientCommandManager.getActiveDispatcher()!=null)next(client);return;}
        if(phase>=CASES.size()){walk(client);return;}
        long elapsed=System.nanoTime()-phaseStarted;if(elapsed<13_000_000_000L)return;Case test=CASES.get(phase);
        try {
            if(!saved) {
                screenshot(client,test.file);captures.add(metadata(client,test));saved=true;write("captures.json",captures);
                if(test.kind.equals("comparison")&&test.view.name().equals("overlook"))command(client,"abyss audit "+test.profile);
                if(test.kind.equals("natural_without_sources"))command(client,"abyss audit no-sources-"+test.profile);
                if(test.file.equals("b-platform.png"))stability=1;
            }
            if(stability>0&&test.file.equals("b-platform.png")) {
                if(elapsed<13_000_000_000L+stability*600_000_000L)return;
                screenshot(client,"b-platform-stability-"+stability+".png");if(stability++<2)return;
            }
            next(client);
        }catch(Exception e){fail(client,e);}
    }
    private JsonObject metadata(MinecraftClient c,Case test) {
        var r=new JsonObject();r.addProperty("file",test.file);r.addProperty("kind",test.kind);r.addProperty("render_profile",test.profile);r.addProperty("light_mode",test.light);r.addProperty("view",test.view==null?"overworld":test.view.name());
        r.addProperty("x",c.player.getX());r.addProperty("y",c.player.getY());r.addProperty("z",c.player.getZ());r.addProperty("yaw",c.player.getYaw());r.addProperty("pitch",c.player.getPitch());
        r.addProperty("framebuffer_width",c.getFramebuffer().textureWidth);r.addProperty("framebuffer_height",c.getFramebuffer().textureHeight);
        r.addProperty("fov",c.options.getFov().getValue());r.addProperty("brightness_gamma",c.options.getGamma().getValue());r.addProperty("view_distance_chunks",c.options.getViewDistance().getValue());r.addProperty("simulation_distance_chunks",c.options.getSimulationDistance().getValue());
        r.addProperty("graphics",c.options.getGraphicsMode().getValue().toString());r.addProperty("ambient_occlusion",c.options.getAo().getValue());r.addProperty("dimension_ambient_light",c.world.getDimension().ambientLight());r.addProperty("dimension_fixed_time",c.world.getDimension().fixedTime().orElse(-1));
        r.addProperty("gl_renderer",GL11.glGetString(GL11.GL_RENDERER));r.addProperty("gl_vendor",GL11.glGetString(GL11.GL_VENDOR));r.addProperty("gl_version",GL11.glGetString(GL11.GL_VERSION));r.addProperty("gl_error_at_capture",GL11.glGetError());r.addProperty("chunk_debug",c.worldRenderer.getChunksDebugString());
        var foot=BlockPos.ofFloored(c.player.getPos());r.addProperty("block_light_at_feet",c.world.getLightLevel(LightType.BLOCK,foot));r.addProperty("sky_light_at_feet",c.world.getLightLevel(LightType.SKY,foot));r.add("environment",EnvironmentClient.measurements());
        r.addProperty("sample_frames",frames.size());r.addProperty("settle_seconds",8);r.addProperty("sample_seconds",5);
        if(!frames.isEmpty()) {
            double sum=frames.stream().mapToDouble(Double::doubleValue).sum();Collections.sort(frames);
            r.addProperty("sample_fps",frames.size()*1000/sum);r.addProperty("median_frame_ms",frames.get(frames.size()/2));r.addProperty("p95_frame_ms",frames.get((int)Math.floor((frames.size()-1)*.95)));r.addProperty("max_frame_ms",frames.get(frames.size()-1));r.addProperty("frames_over_100ms",frames.stream().filter(v->v>100).count());
        }return r;
    }
    private void next(MinecraftClient client) {
        phase++;saved=false;stability=0;frames.clear();lastFrame=0;phaseStarted=System.nanoTime();if(phase>=CASES.size())return;
        var test=CASES.get(phase);selectWithCommand(client,test.profile);command(client,"abyss light "+test.light);command(client,test.view==null?"abyss return":"abyss view "+test.view.name());
    }
    private void screenshot(MinecraftClient client,String name)throws Exception {
        Files.createDirectories(OUT);try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())){image.writeTo(OUT.resolve(name));}System.out.println("ABYSS_PHASE2_CAPTURE_SAVED "+name);
    }
    private void write(String name,com.google.gson.JsonElement value)throws Exception {Files.writeString(OUT.resolve(name),new GsonBuilder().setPrettyPrinting().create().toJson(value));}
    private void walk(MinecraftClient c) {
        if(!prepared){prepared=true;selectWithCommand(c,"c");command(c,"abyss light glimmer");command(c,"abyss view tunnel");walkStarted=System.nanoTime();return;}
        long elapsed=System.nanoTime()-walkStarted;if(!walking&&elapsed>2_000_000_000L){walking=true;walkStartZ=c.player.getZ();c.options.forwardKey.setPressed(true);}if(elapsed<10_000_000_000L)return;
        c.options.forwardKey.setPressed(false);double z=c.player.getZ(),y=c.player.getY();boolean pass=walkStartZ-z>20&&z>62.7&&z<64&&Math.abs(y-39)<.01;
        var r=new JsonObject();r.addProperty("method","Actual forward-key movement for 8 seconds under profile C; stopped by existing iron rail.");r.addProperty("start_z",walkStartZ);r.addProperty("end_z",z);r.addProperty("end_y",y);r.addProperty("walk_and_collision_pass",pass);
        try{write("walk-collision.json",r);}catch(Exception e){fail(c,e);return;}finished=true;System.out.println("ABYSS_PHASE2_COMPLETE "+captures.size()+" walk_collision="+pass);c.scheduleStop();
    }
    private void selectWithCommand(MinecraftClient c,String profile) {
        try {int result=ClientCommandManager.getActiveDispatcher().execute("abyssrender "+profile,(FabricClientCommandSource)c.getNetworkHandler().getCommandSource());if(result!=1||!EnvironmentClient.selectedName().equals(profile))throw new IllegalStateException("Bad command result");System.out.println("ABYSS_RENDER_COMMAND_DISPATCH "+profile+" result="+result);}catch(Exception e){throw new IllegalStateException(e);}
    }
    private void command(MinecraftClient c,String text){c.getNetworkHandler().sendChatCommand(text);}
    private void fail(MinecraftClient c,Exception e){e.printStackTrace();finished=true;System.err.println("ABYSS_PHASE2_FAILED");c.scheduleStop();}
}
