package dev.mispla.abyss;

import com.google.gson.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.network.*;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import org.lwjgl.opengl.GL11;
import java.nio.file.*;

/** Opt-in release-client spatial review. Actual sleep, bell journeys and forward-key loop. */
public final class V1CaptureClient implements ClientModInitializer {
    private static final Path OUT=Path.of("abyss-v1-captures");
    private static final double[][] ROUTE={{47.5,-68.5},{47.5,-84.5},{22.5,-84.5},{22.5,-100.5},{-28.5,-100.5},{-28.5,-124.5},{-78.5,-124.5},{-78.5,-148.5},{-126.5,-148.5},{-126.5,-184.5},{-151.5,-184.5},{-151.5,-214.5},{-90.5,-214.5},{-90.5,-176.5},{-66.5,-176.5},{-66.5,-124.5},{-78.5,-124.5},{-28.5,-124.5},{-28.5,-100.5},{22.5,-100.5},{22.5,-84.5},{47.5,-84.5},{47.5,-68.5}};
    private final JsonArray frames=new JsonArray(),motion=new JsonArray(),walks=new JsonArray();
    private int state=-1,leg,night;private boolean connected,done,slept;private int dreamCaptured;
    private long started,changed;private double walkX,walkZ;
    @Override public void onInitializeClient(){if(Boolean.getBoolean("abyss.capture.v1")){started=changed=System.nanoTime();ClientTickEvents.END_CLIENT_TICK.register(this::tick);}}
    private double seconds(){return (System.nanoTime()-changed)/1e9;}
    private void next(int n){state=n;changed=System.nanoTime();System.out.println("ABYSS_V1_STAGE "+n);}
    private void cmd(MinecraftClient c,String v){c.getNetworkHandler().sendChatCommand("abyss phase7 "+v);}
    private void bell(MinecraftClient c,boolean upper){
        var p=new BlockPos(JourneyClient.x+6+(upper?0:24),upper?JourneyClient.entryY+1:45,JourneyClient.z+(upper?68:56));
        c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(p),Direction.SOUTH,p,false));
    }
    private void tick(MinecraftClient c){
        if(done)return;
        try{
            if(System.nanoTime()-started>1400_000_000_000L)throw new IllegalStateException("Preview deadline");
            if(!connected&&c.currentScreen!=null&&c.getOverlay()==null&&!(c.currentScreen instanceof TitleScreen))c.setScreen(new TitleScreen());
            if(!connected&&c.currentScreen instanceof TitleScreen&&c.getOverlay()==null){
                connected=true;c.options.getViewDistance().setValue(16);c.options.getSimulationDistance().setValue(6);c.options.getFov().setValue(75);c.options.getGamma().setValue(.5);c.options.getMaxFps().setValue(45);c.options.getEnableVsync().setValue(false);c.options.pauseOnLostFocus=false;c.options.hudHidden=false;
                String address="127.0.0.1:25585";ConnectScreen.connect(new TitleScreen(),c,ServerAddress.parse(address),new ServerInfo("V1 release preview",address,false),false);return;
            }
            if(c.player==null||c.world==null||!JourneyClient.ready())return;
            if(!c.player.isAlive())throw new IllegalStateException("Player died");
            if(DreamScreen.step>dreamCaptured&&DreamScreen.alpha()>.85f){
                capture(c,"sleep-dream-"+DreamScreen.step+".png");var r=new JsonObject();r.addProperty("step",DreamScreen.step);r.addProperty("sleeping_at_capture",c.player.isSleeping());write("dream-"+DreamScreen.step+".json",r);dreamCaptured=DreamScreen.step;
            }
            if(c.player.hasVehicle()){
                var p=c.player.getVehicle();var r=new JsonObject();r.addProperty("state",state);r.addProperty("x",p.getX());r.addProperty("y",p.getY());r.addProperty("z",p.getZ());r.addProperty("passenger_dx",c.player.getX()-p.getX());r.addProperty("passenger_dz",c.player.getZ()-p.getZ());motion.add(r);
            }
            if(Boolean.getBoolean("abyss.capture.v1.second")){
                if(state==-1){cmd(c,"view overview");next(90);return;}
                if(readyFrame(c)){capture(c,"second-seed-overview.png");finish(c);}return;
            }
            if(state==-1){cmd(c,"bedtime");next(0);return;}
            if(state==0){
                if(seconds()<3)return;var p=new BlockPos(JourneyClient.x+400,c.player.getBlockY(),JourneyClient.z+70);
                c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(p),Direction.UP,p,false));slept=false;next(1);return;
            }
            if(state==1){
                slept|=c.player.isSleeping();if(!slept||c.player.isSleeping()||DreamScreen.step!=0){if(seconds()>40)throw new IllegalStateException("Sleep/wake flow did not finish");return;}
                if(++night<2){cmd(c,"bedtime");next(0);return;}
                if(dreamCaptured!=2||!c.player.getInventory().contains(new net.minecraft.item.ItemStack(Journey.ECHO)))throw new IllegalStateException("Sleep dreams or guide absent");
                cmd(c,"view entrance");next(2);return;
            }
            if(state==2){if(!readyFrame(c))return;capture(c,"upper-station.png");bell(c,true);next(3);return;}
            if(state==3){if(!c.player.hasVehicle()){if(seconds()>10)throw new IllegalStateException("Boarding failed");return;}next(4);return;}
            if(state==4){if(c.player.hasVehicle()){if(seconds()>100)throw new IllegalStateException("Ride deadline");return;}checkLanding(c,false);cmd(c,"view overview");next(5);return;}
            if(state>=5&&state<=9){
                if(!readyFrame(c))return;String[] names={"town-in-abyss.png","main-rift.png","folded-gallery.png","warm-return.png","town-street.png"};String[] views={"rift","gallery","homeward","street"};
                capture(c,names[state-5]);if(state<9){cmd(c,"view "+views[state-5]);next(state+1);}else{cmd(c,"view route-start");next(10);}return;
            }
            if(state==10){if(seconds()<5)return;leg=1;walkX=c.player.getX();walkZ=c.player.getZ();next(11);return;}
            if(state==11){
                if(leg==ROUTE.length){c.options.forwardKey.setPressed(false);cmd(c,"view lower");next(12);return;}
                double dx=JourneyClient.x+ROUTE[leg][0]-c.player.getX(),dz=JourneyClient.z+ROUTE[leg][1]-c.player.getZ();
                c.player.setYaw((float)Math.toDegrees(Math.atan2(-dx,dz)));c.player.setPitch(0);c.options.forwardKey.setPressed(true);
                if(Math.hypot(dx,dz)>.4){if(seconds()>60)throw new IllegalStateException("Loop blocked leg "+leg+" at "+c.player.getPos());return;}
                c.options.forwardKey.setPressed(false);var r=new JsonObject();r.addProperty("leg",leg);r.addProperty("start_x",walkX);r.addProperty("start_z",walkZ);r.addProperty("end_x",c.player.getX());r.addProperty("end_y",c.player.getY());r.addProperty("end_z",c.player.getZ());r.addProperty("health",c.player.getHealth());r.addProperty("flying",c.player.getAbilities().flying);walks.add(r);write("walk.json",walks);
                if(c.player.getAbilities().flying||c.player.getHealth()<20)throw new IllegalStateException("Unsafe exploration walk");
                if(leg==9||leg==15)capture(c,"walk-gallery-"+leg+".png");leg++;walkX=c.player.getX();walkZ=c.player.getZ();changed=System.nanoTime();return;
            }
            if(state==12){if(seconds()<4)return;bell(c,false);next(13);return;}
            if(state==13){if(!c.player.hasVehicle()){if(seconds()>10)throw new IllegalStateException("Return boarding failed");return;}next(14);return;}
            if(state==14){if(c.player.hasVehicle()){if(seconds()>100)throw new IllegalStateException("Return ride deadline");return;}checkLanding(c,true);write("motion.json",motion);cmd(c,"debugcheck");finish(c);}
        }catch(Exception ex){var r=new JsonObject();r.addProperty("state",state);r.addProperty("leg",leg);r.addProperty("error",ex.toString());write("failure.json",r);ex.printStackTrace();done=true;c.options.forwardKey.setPressed(false);c.scheduleStop();}
    }
    private boolean readyFrame(MinecraftClient c){
        if(seconds()>160)throw new IllegalStateException("Unsettled chunks "+c.worldRenderer.getChunksDebugString());
        return seconds()>18&&c.worldRenderer.getChunksDebugString().matches(".*pC: 0+, pU: 0+(?:,|\\s|$).*");
    }
    private void checkLanding(MinecraftClient c,boolean upper){
        if(Math.abs(c.player.getX()-(JourneyClient.x+(upper?6.5:30.5)))>.3||Math.abs(c.player.getZ()-(JourneyClient.z+(upper?70.5:58.5)))>.3||Math.abs(c.player.getY()-(upper?JourneyClient.entryY+1:45))>.3||c.player.getHealth()!=20)throw new IllegalStateException("Incorrect safe station");
    }
    private void capture(MinecraftClient c,String name)throws Exception{
        Files.createDirectories(OUT);try(var f=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){f.writeTo(OUT.resolve(name));}
        var r=new JsonObject();r.addProperty("file",name);r.addProperty("position",c.player.getPos().toString());r.addProperty("yaw",c.player.getYaw());r.addProperty("pitch",c.player.getPitch());r.addProperty("health",c.player.getHealth());r.addProperty("sleeping",c.player.isSleeping());r.addProperty("chunk_debug",c.worldRenderer.getChunksDebugString());r.addProperty("gl_error",GL11.glGetError());r.addProperty("renderer",GL11.glGetString(GL11.GL_RENDERER));r.add("environment",EnvironmentClient.measurements());frames.add(r);write("captures.json",frames);
    }
    private void finish(MinecraftClient c){var r=new JsonObject();r.addProperty("status","SPATIAL_PREVIEW_CAPTURED");r.addProperty("android_tested",false);r.addProperty("aesthetic_accepted",false);write("result.json",r);done=true;c.options.forwardKey.setPressed(false);c.scheduleStop();}
    private void write(String name,Object value){try{Files.createDirectories(OUT);Files.writeString(OUT.resolve(name),new GsonBuilder().setPrettyPrinting().create().toJson(value));}catch(Exception ex){throw new IllegalStateException(ex);}}
}
