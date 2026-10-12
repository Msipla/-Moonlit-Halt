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
public final class Phase6CaptureClient implements ClientModInitializer {
    private record Case(String file,String profile,GlimmeringAbyss.View view,String light,String kind) {}
    private static final List<Case> CASES=cases();
    private static final Path OUT=Path.of("abyss-phase6-captures");
    private final JsonArray captures=new JsonArray();
    private final List<Double> frames=new ArrayList<>();
    private final JsonObject intervals=new JsonObject();
    private boolean connected,entered,finished,saved,walking,prepared;
    private int phase=-1,stability;
    private long started,phaseStarted,lastFrame,walkStarted,walkPressed;
    private double walkStartZ;
    private int leg;
    private final JsonArray legs=new JsonArray();
    private long legStarted;
    private double legStartX,legStartZ;
    private boolean wallTest,railTest;
    private static final double[][] WAYPOINTS={{42.5,43.5,39},{30.5,43.5,39},{30.5,35.5,39},{-29.5,35.5,39},{-29.5,30.5,39},{-29.5,35.5,39},{30.5,35.5,39},{30.5,5.5,39},{47.5,5.5,39},{47.5,-22.5,39},{47.5,-39.5,40},{47.5,-47.5,41},{58.5,-47.5,40},{58.5,-55.5,41},{54.5,-55.5,41},{54.5,-58.3,41}};
    private boolean climbing,climbed,steppedOff;
    private long climbStarted;
    private static List<Case> cases() {
        var r=new ArrayList<Case>();
        r.add(new Case("before-platform.png","blue",GlimmeringAbyss.VIEWS.get(1),"glimmer","before_building"));
        r.add(new Case("before-facade.png","blue",Architecture.VIEWS.get(0),"glimmer","phase4_control"));
        r.add(new Case("before-bridge.png","blue",Exploration.VIEWS.get(2),"glimmer","phase5_control"));
        for(var v:Town.VIEWS)r.add(new Case("blue-"+v.name()+".png","blue",v,"glimmer","town"));
        for(String profile:new String[]{"blue95","blue95-fog"})for(String name:new String[]{"street","high","cliff"}){
            var v=Town.VIEWS.stream().filter(x->x.name().equals(name)).findFirst().orElseThrow();
            r.add(new Case(profile+"-"+name+".png",profile,v,"glimmer","lighting_comparison"));
        }
        r.add(new Case("blue95-fog-bridge.png","blue95-fog",Town.VIEWS.get(4),"glimmer","lighting_comparison"));
        r.add(new Case("a-street.png","a",Town.VIEWS.get(0),"glimmer","native_control"));
        r.add(new Case("overworld-blue95-fog.png","blue95-fog",null,"glimmer","dimension_scope"));return List.copyOf(r);
    }
    @Override public void onInitializeClient() {
        if(!Boolean.getBoolean("abyss.capture.phase6"))return;started=System.nanoTime();System.out.println("ABYSS_PHASE6_RELEASE_HARNESS_ENABLED");
        WorldRenderEvents.END.register(c->{long now=System.nanoTime();if(phase>=0&&phase<CASES.size()&&!saved&&now-phaseStarted>8_000_000_000L&&lastFrame!=0)frames.add((now-lastFrame)/1e6);lastFrame=now;});
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }
    private void tick(MinecraftClient client) {
        if(finished)return;
        if(System.nanoTime()-started>1700_000_000_000L){fail(client,new IllegalStateException("Capture timeout"));return;}
        // Release Minecraft uses intermediary class names; never identify screens with a name string.
        if(!connected&&client.currentScreen!=null&&client.getOverlay()==null&&!(client.currentScreen instanceof TitleScreen))client.setScreen(new TitleScreen());
        if(!connected&&System.nanoTime()-started>180_000_000_000L){fail(client,new IllegalStateException("Release title-screen connection timeout"));return;}
        if(!connected&&client.currentScreen instanceof TitleScreen&&client.getOverlay()==null) {
            connected=true;client.options.getViewDistance().setValue(16);client.options.getSimulationDistance().setValue(6);
            client.options.getFov().setValue(75);client.options.getGamma().setValue(.5);client.options.getMaxFps().setValue(60);
            client.options.getEnableVsync().setValue(false);client.options.getGraphicsMode().setValue(GraphicsMode.FANCY);client.options.getAo().setValue(true);
            client.options.pauseOnLostFocus=false;client.options.hudHidden=true;client.options.write();
            String address="127.0.0.1:"+Integer.getInteger("abyss.port",25585);
            ConnectScreen.connect(new TitleScreen(),client,ServerAddress.parse(address),new ServerInfo("Abyss phase 6 release test",address,false),false);return;
        }
        if(client.player==null||client.world==null||client.getNetworkHandler()==null)return;
        if(!entered){entered=true;command(client,"abyss enter");return;}
        if(phase==-1){if(EnvironmentClient.studyActive()&&ClientCommandManager.getActiveDispatcher()!=null)next(client);return;}
        if(phase>=CASES.size()){walk(client);return;}
        long elapsed=System.nanoTime()-phaseStarted;if(elapsed<13_000_000_000L)return;Case test=CASES.get(phase);
        if(!cQueuesReady(client)){if(elapsed<150_000_000_000L)return;fail(client,new IllegalStateException("Chunk queues did not drain: "+client.worldRenderer.getChunksDebugString()));return;}
        try {
            if(!saved) {
                screenshot(client,test.file);var times=new JsonArray();for(double value:frames)times.add(value);intervals.add(test.file,times);write("frame-intervals.json",intervals);captures.add(metadata(client,test));saved=true;write("captures.json",captures);
                if(test.kind.equals("before_building"))command(client,"abyss audit before-building");
                if(test.file.equals("before-facade.png"))command(client,"abyss building audit phase4-regression");
                if(test.file.equals("before-bridge.png"))command(client,"abyss route audit phase5-regression");
                if(test.file.equals("blue-street.png"))command(client,"abyss town audit first");
                if(test.file.equals("blue-interior.png")){command(client,"abyss town");command(client,"abyss town audit repeat");}
                
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
        var foot=BlockPos.ofFloored(c.player.getPos());r.addProperty("block_light_at_feet",c.world.getLightLevel(LightType.BLOCK,foot));r.addProperty("sky_light_at_feet",c.world.getLightLevel(LightType.SKY,foot));r.add("environment",EnvironmentClient.measurements());r.add("space_lighting",SpaceLighting.measurements());
        if(SpaceLighting.active()&&SpaceLighting.measurements().get("terrain_shader_selections").getAsLong()==0)throw new IllegalStateException("New terrain shader never selected");
        r.addProperty("sample_frames",frames.size());r.addProperty("settle_seconds",8);r.addProperty("sample_seconds",frames.stream().mapToDouble(Double::doubleValue).sum()/1000);
        if(!frames.isEmpty()) {
            double sum=frames.stream().mapToDouble(Double::doubleValue).sum();Collections.sort(frames);
            r.addProperty("sample_fps",frames.size()*1000/sum);r.addProperty("median_frame_ms",frames.get(frames.size()/2));r.addProperty("p95_frame_ms",frames.get((int)Math.floor((frames.size()-1)*.95)));r.addProperty("max_frame_ms",frames.get(frames.size()-1));r.addProperty("frames_over_100ms",frames.stream().filter(v->v>100).count());
        }return r;
    }
    private void next(MinecraftClient client) {
        phase++;saved=false;stability=0;frames.clear();lastFrame=0;phaseStarted=System.nanoTime();if(phase>=CASES.size())return;
        var test=CASES.get(phase);if(phase==1)command(client,"abyss building");if(phase==2)command(client,"abyss route");if(phase==3)command(client,"abyss town");selectWithCommand(client,test.profile);command(client,"abyss lamps "+(test.kind.equals("native_lanterns")?"on":"off"));command(client,"abyss light "+test.light);command(client,test.view==null?"abyss return":test.kind.equals("phase4_control")?"abyss building view facade":test.kind.equals("phase5_control")?"abyss route view "+test.view.name():Town.VIEWS.contains(test.view)?"abyss town view "+test.view.name():"abyss view "+test.view.name());
    }
    private boolean cQueuesReady(MinecraftClient c) {
        return c.worldRenderer.getChunksDebugString().matches(".*pC: 0+, pU: 0+(?:,|\\s|$).*");
    }
    private void screenshot(MinecraftClient client,String name)throws Exception {
        Files.createDirectories(OUT);try(var image=ScreenshotRecorder.takeScreenshot(client.getFramebuffer())){image.writeTo(OUT.resolve(name));}System.out.println("ABYSS_PHASE6_CAPTURE_SAVED "+name);
    }
    private void write(String name,com.google.gson.JsonElement value)throws Exception {Files.writeString(OUT.resolve(name),new GsonBuilder().setPrettyPrinting().create().toJson(value));}
    private void walk(MinecraftClient c) {
        if(!prepared){prepared=true;selectWithCommand(c,"blue");command(c,"abyss town view interior");walkStarted=System.nanoTime();return;}
        if(System.nanoTime()-walkStarted<2_000_000_000L)return;
        if(leg<WAYPOINTS.length) {
            double tx=WAYPOINTS[leg][0],tz=WAYPOINTS[leg][1];
            if(!walking){walking=true;legStarted=System.nanoTime();legStartX=c.player.getX();legStartZ=c.player.getZ();
                double dx=tx-legStartX,dz=tz-legStartZ;c.player.setYaw((float)Math.toDegrees(Math.atan2(-dx,dz)));c.player.setPitch(0);}
            c.options.forwardKey.setPressed(true);
            double distance=Math.hypot(tx-c.player.getX(),tz-c.player.getZ());
            if(distance>.26&&System.nanoTime()-legStarted<25_000_000_000L)return;
            c.options.forwardKey.setPressed(false);
            boolean pass=distance<.4&&Math.abs(c.player.getY()-WAYPOINTS[leg][2])<.02;
            var r=new JsonObject();r.addProperty("leg",leg);r.addProperty("start_x",legStartX);r.addProperty("start_z",legStartZ);r.addProperty("target_x",tx);r.addProperty("target_z",tz);r.addProperty("end_x",c.player.getX());r.addProperty("end_z",c.player.getZ());r.addProperty("end_y",c.player.getY());r.addProperty("held_forward_seconds",(System.nanoTime()-legStarted)/1e9);r.addProperty("pass",pass);legs.add(r);
            try{write("walk-legs.json",legs);if(leg==4)screenshot(c,"blue-walk-arrival.png");if(leg==10)screenshot(c,"blue-walk-street.png");}catch(Exception e){fail(c,e);return;}
            if(!pass){fail(c,new IllegalStateException("Actual walking leg blocked: "+r));return;}
            leg++;walking=false;return;
        }
        if(!climbed) {
            if(!climbing){climbing=true;climbStarted=System.nanoTime();c.player.setYaw(90);c.player.setPitch(-20);}
            c.options.forwardKey.setPressed(true);
            if(c.player.getY()<59&&System.nanoTime()-climbStarted<20_000_000_000L)return;
            c.options.forwardKey.setPressed(false);
            boolean pass=c.player.getY()>=59&&!c.player.getAbilities().flying;
            var r=new JsonObject();r.addProperty("method","Actual forward-key walking from workshop, across bridge and back, through streets and up the vanilla ladder; no intermediate teleport or flight.");r.addProperty("end_y",c.player.getY());r.addProperty("actual_ladder_climb_pass",pass);r.addProperty("walk_and_collision_pass",pass);r.add("walking_legs",legs);
            try{write("walk-collision.json",r);screenshot(c,"blue-walk-high.png");}catch(Exception e){fail(c,e);return;}
            if(!pass){fail(c,new IllegalStateException("Ladder did not reach balcony: "+r));return;}
            climbed=true;legStarted=System.nanoTime();c.player.setYaw(-90);return;
        }
        if(!steppedOff){
            c.options.forwardKey.setPressed(true);
            if(c.player.getX()<55.5&&System.nanoTime()-legStarted<4_000_000_000L)return;
            c.options.forwardKey.setPressed(false);steppedOff=true;
            try{screenshot(c,"blue-walk-balcony.png");}catch(Exception e){fail(c,e);return;}
            command(c,"abyss town view bridge");legStarted=System.nanoTime();return;
        }
        if(!railTest) {
            if(!walking){if(System.nanoTime()-legStarted<2_000_000_000L)return;walking=true;legStarted=System.nanoTime();c.player.setYaw(180);c.player.setPitch(35);}
            c.options.forwardKey.setPressed(true);if(System.nanoTime()-legStarted<5_000_000_000L)return;
            c.options.forwardKey.setPressed(false);double z=c.player.getZ();
            boolean pass=z>32.8&&z<33.4&&Math.abs(c.player.getY()-39)<.01&&Math.abs(c.player.getX()-4.5)<.01;
            var r=new JsonObject();r.addProperty("method","Actual forward key held north into bridge iron rail, flight disabled, five seconds including stationary collision.");r.addProperty("end_x",c.player.getX());r.addProperty("end_z",z);r.addProperty("end_y",c.player.getY());r.addProperty("rail_collision_pass",pass);
            try{write("rail-collision.json",r);screenshot(c,"blue-rail-collision.png");}catch(Exception e){fail(c,e);return;}
            if(!pass){fail(c,new IllegalStateException("Rail did not stop player: "+r));return;}
            railTest=true;
        }
        finished=true;System.out.println("ABYSS_PHASE6_COMPLETE "+captures.size()+" walking_and_ladder=true rail_collision=true");c.scheduleStop();
    }
    private void selectWithCommand(MinecraftClient c,String profile) {
        try {int result=ClientCommandManager.getActiveDispatcher().execute("abyssrender "+profile,(FabricClientCommandSource)c.getNetworkHandler().getCommandSource());if(result!=1||!EnvironmentClient.selectedName().equals(profile))throw new IllegalStateException("Bad command result");System.out.println("ABYSS_RENDER_COMMAND_DISPATCH "+profile+" result="+result);}catch(Exception e){throw new IllegalStateException(e);}
    }
    private void command(MinecraftClient c,String text){c.getNetworkHandler().sendChatCommand(text);}
    private void fail(MinecraftClient c,Exception e){e.printStackTrace();finished=true;System.err.println("ABYSS_PHASE6_FAILED");c.scheduleStop();}
}
