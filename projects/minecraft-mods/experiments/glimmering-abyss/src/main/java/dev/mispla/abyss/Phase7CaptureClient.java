package dev.mispla.abyss;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;
import java.nio.file.Files;
import java.nio.file.Path;

/** Opt-in official-client harness: actual bell interactions, two rides and walking keys. */
public final class Phase7CaptureClient implements ClientModInitializer {
    private static final boolean RECONNECT=Boolean.getBoolean("abyss.capture.phase7.reconnect");
    private static final boolean NATURAL=Boolean.getBoolean("abyss.capture.phase75");
    private static final boolean SECOND=Boolean.getBoolean("abyss.capture.phase75.second");
    private long dreamSeenAt;
    private int dreamCaptured;
    private boolean refugeRested;
    private final JsonArray motion=new JsonArray();
    private static final Path OUT=Path.of(RECONNECT?"abyss-phase7-reconnect":"abyss-phase7-captures");
    private final JsonArray frames=new JsonArray(),walks=new JsonArray(),rides=new JsonArray(),accesses=new JsonArray();
    private boolean connected,finished,midShot,lowShot,exteriorShot;
    private long exteriorAt;
    private net.minecraft.entity.decoration.ArmorStandEntity photoCamera;
    private long started,changed;
    private int phase=-1,leg,sleeps;
    private boolean observedSleep,restartReady;
    private double startX,startZ;
    private static final double[][] WALK=NATURAL?new double[][]{{6.5,72.5},{20.5,72.5},{20.5,70.5},{38.5,70.5},{38.5,43.5},{30.5,43.5},{30.5,35.5},{-29.5,35.5},{-46.5,35.5},{-48.5,35.5},{-48.5,32.5},{-48.5,35.5},{-29.5,35.5},{30.5,35.5},{30.5,5.5},{47.5,5.5},{47.5,-22.5},{47.5,5.5},{30.5,5.5},{30.5,43.5},{38.5,43.5},{38.5,70.5},{20.5,70.5},{20.5,72.5},{6.5,72.5},{6.5,70.5}}:new double[][]{{6.5,72.5},{20.5,72.5},{20.5,70.5},{38.5,70.5},{38.5,43.5},{30.5,43.5},{30.5,35.5},{-29.5,35.5},{30.5,35.5},{30.5,5.5},{47.5,5.5},{47.5,-22.5},{47.5,5.5},{30.5,5.5},{30.5,43.5},{38.5,43.5},{38.5,70.5},{20.5,70.5},{20.5,72.5},{6.5,72.5},{6.5,70.5}};
    @Override public void onInitializeClient(){if(!Boolean.getBoolean("abyss.capture.phase7"))return;started=System.nanoTime();ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    private double seconds(){return (System.nanoTime()-changed)/1e9;}
    private void next(){phase++;changed=System.nanoTime();System.out.println("ABYSS_PHASE7_STAGE "+phase);}
    private void command(MinecraftClient c,String command){c.getNetworkHandler().sendChatCommand("abyss phase7 "+command);}
    private void bell(MinecraftClient c,boolean upper){
        var p=new BlockPos(JourneyClient.x+6,upper?JourneyClient.entryY+1:45,JourneyClient.z+68);
        c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(p),Direction.SOUTH,p,false));
    }
    private void tick(MinecraftClient c){
        if(finished)return;
        try{
            if(System.nanoTime()-started>1500_000_000_000L)throw new IllegalStateException("Capture deadline");
            if(!connected&&c.currentScreen!=null&&c.getOverlay()==null&&!(c.currentScreen instanceof TitleScreen))c.setScreen(new TitleScreen());
            if(!connected&&c.currentScreen instanceof TitleScreen&&c.getOverlay()==null){
                connected=true;c.options.getViewDistance().setValue(16);c.options.getSimulationDistance().setValue(6);c.options.getFov().setValue(75);c.options.getGamma().setValue(.5);c.options.getMaxFps().setValue(45);c.options.getEnableVsync().setValue(false);c.options.getGraphicsMode().setValue(GraphicsMode.FANCY);c.options.getAo().setValue(true);c.options.pauseOnLostFocus=false;c.options.hudHidden=true;
                String address="127.0.0.1:"+Integer.getInteger("abyss.port",25585);ConnectScreen.connect(new TitleScreen(),c,ServerAddress.parse(address),new ServerInfo("Phase 7 release test",address,false),false);return;
            }
            if(restartReady&&c.world==null){finished=true;c.scheduleStop();return;}
            if(c.player==null||c.world==null||!JourneyClient.ready())return;
            if(!c.player.isAlive())throw new IllegalStateException("Acceptance player died at "+c.player.getPos());
            if(NATURAL&&c.player.hasVehicle()&&(phase==3||phase==6)){
                var r=new JsonObject();r.addProperty("phase",phase);r.addProperty("seconds",seconds());r.addProperty("x",c.player.getX());r.addProperty("y",c.player.getY());r.addProperty("z",c.player.getZ());
                var car=c.player.getVehicle();r.addProperty("car_x",car.getX());r.addProperty("car_y",car.getY());r.addProperty("car_z",car.getZ());motion.add(r);
            }
            if(SECOND){
                if(phase==-1){command(c,"view overview");next();return;}
                if(seconds()<18||!queues(c))return;
                capture(c,"second-seed-overview.png");finished=true;c.scheduleStop();return;
            }
            if(c.currentScreen instanceof DreamScreen dream){
                if(dream.step>dreamCaptured){
                    if(dreamSeenAt==0)dreamSeenAt=System.nanoTime();
                    if(System.nanoTime()-dreamSeenAt>400_000_000L){capture(c,"dream-"+dream.step+".png");dreamCaptured=dream.step;dreamSeenAt=0;}
                }
                return;
            }
            if(RECONNECT){
                if(phase==-1){next();return;}if(seconds()<12)return;checkLanding(c,true);capture(c,"reconnected-safe-station.png");
                var recovery=new JsonObject();recovery.addProperty("pass",true);recovery.addProperty("same_offline_identity",true);write("recovery.json",recovery);finished=true;c.scheduleStop();return;
            }
            if(phase==-1){command(c,"view outside");next();return;}
            if(phase==0){if(seconds()<65)return;command(c,"natural");capture(c,"outside-normal-overworld.png");command(c,"bedtime");phase=20;changed=System.nanoTime();return;}
            if(phase==20){
                if(seconds()<3)return;var bed=new BlockPos(JourneyClient.x+400,c.player.getBlockY(),JourneyClient.z+70);
                c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(bed),Direction.UP,bed,false));observedSleep=false;phase=21;changed=System.nanoTime();return;
            }
            if(phase==21){
                observedSleep|=c.player.isSleeping();if(c.player.isSleeping()||!observedSleep){if(seconds()>30)throw new IllegalStateException("Real bed sleep did not complete");return;}
                if(++sleeps<2){command(c,"bedtime");phase=20;changed=System.nanoTime();return;}
                if(!c.player.getInventory().contains(new net.minecraft.item.ItemStack(Journey.ECHO)))throw new IllegalStateException("Two real sleeps did not deliver guide");
                var dream=new JsonObject();dream.addProperty("actual_bed_sleeps",sleeps);dream.addProperty("guide_received",true);write("dreams.json",dream);
                for(int i=0;i<9;i++)if(c.player.getInventory().getStack(i).isOf(Journey.ECHO))c.player.getInventory().selectedSlot=i;
                c.interactionManager.interactItem(c.player,Hand.MAIN_HAND);command(c,"view approach");phase=17;changed=System.nanoTime();return;
            }
            if(phase==22){
                if(seconds()<3)return;var bed=new BlockPos(JourneyClient.x-46,45,JourneyClient.z+30);
                c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(bed),Direction.UP,bed,false));phase=23;changed=System.nanoTime();return;
            }
            if(phase==23){
                observedSleep|=c.player.isSleeping();if(c.player.isSleeping()||!observedSleep){if(seconds()>30)throw new IllegalStateException("Refuge bed sleep did not complete");return;}
                var r=new JsonObject();r.addProperty("actual_refuge_bed_sleep",true);r.addProperty("health",c.player.getHealth());r.addProperty("dimension",c.world.getRegistryKey().getValue().toString());write("refuge-sleep.json",r);
                refugeRested=true;leg++;startX=c.player.getX();startZ=c.player.getZ();phase=4;changed=System.nanoTime();return;
            }
            if(phase==17){if(!surfaceWalk(c,true))return;c.player.setYaw(180);c.player.setPitch(8);phase=1;changed=System.nanoTime();return;}
            if(phase==18){if(!surfaceWalk(c,false))return;capture(c,"surface-walk-exit.png");phase=19;changed=System.nanoTime();return;}
            if(phase==19){if(!surfaceWalk(c,true))return;bell(c,true);phase=7;changed=System.nanoTime();return;}
            if(phase==1){if(seconds()<18||!queues(c))return;capture(c,"surface-station.png");bell(c,true);next();return;}
            if(phase==2){if(!c.player.hasVehicle()){if(seconds()>12)throw new IllegalStateException("Upper bell did not board");return;}next();return;}
            if(phase==3){
                if(!midShot&&c.player.getY()<JourneyClient.entryY-10){capture(c,"cabin-descent.png");midShot=true;}
                if(!lowShot&&c.player.getY()<72){c.player.setYaw(-140);c.player.setPitch(8);capture(c,"cabin-cavern-arrival.png");lowShot=true;}
                if(!exteriorShot&&photoCamera==null&&c.player.hasVehicle()&&c.player.getY()<66){
                    // Detached photography camera only; the real passenger remains seated and the server car keeps moving.
                    photoCamera=new net.minecraft.entity.decoration.ArmorStandEntity(c.world,0,0,0);
                    photoCamera.refreshPositionAndAngles(JourneyClient.x-9.5,c.player.getY()+2,JourneyClient.z+60.5,-45,18);
                    photoCamera.prevYaw=photoCamera.getYaw();photoCamera.prevPitch=photoCamera.getPitch();photoCamera.headYaw=photoCamera.prevHeadYaw=-45;photoCamera.bodyYaw=photoCamera.prevBodyYaw=-45;
                    c.setCameraEntity(photoCamera);exteriorAt=System.nanoTime();
                }
                if(photoCamera!=null&&System.nanoTime()-exteriorAt>1_000_000_000L){capture(c,"phantom-car-exterior.png");exteriorShot=true;c.setCameraEntity(c.player);photoCamera=null;}
                if(c.player.hasVehicle()){if(seconds()>150)throw new IllegalStateException("Descent did not finish");return;}
                checkLanding(c,false);phase=16;changed=System.nanoTime();return;
            }
            if(phase==16){if(seconds()<2)return;capture(c,"lower-arrival.png");leg=0;startX=c.player.getX();startZ=c.player.getZ();phase=4;changed=System.nanoTime();return;}
            if(phase==4){
                if(leg>=WALK.length){c.options.forwardKey.setPressed(false);bell(c,false);next();return;}
                double tx=JourneyClient.x+WALK[leg][0],tz=JourneyClient.z+WALK[leg][1],dx=tx-c.player.getX(),dz=tz-c.player.getZ();
                c.player.setYaw((float)Math.toDegrees(Math.atan2(-dx,dz)));c.player.setPitch(0);c.options.forwardKey.setPressed(true);
                if(Math.hypot(dx,dz)>.38&&seconds()<35)return;
                c.options.forwardKey.setPressed(false);boolean pass=Math.hypot(dx,dz)<=.5&&Math.abs(c.player.getY()-45)<.15&&!c.player.getAbilities().flying;
                var r=new JsonObject();r.addProperty("leg",leg);r.addProperty("start_x",startX);r.addProperty("start_z",startZ);r.addProperty("end_x",c.player.getX());r.addProperty("end_z",c.player.getZ());r.addProperty("end_y",c.player.getY());r.addProperty("forward_seconds",seconds());r.addProperty("pass",pass);walks.add(r);write("walk.json",walks);
                if(!pass)throw new IllegalStateException("Walking blocked: "+r);
                if(leg==7)capture(c,"walk-bridge.png");if(NATURAL&&leg==8)capture(c,"walk-library.png");if(leg==(NATURAL?16:11))capture(c,"walk-town.png");
                if(NATURAL&&leg==10&&!refugeRested){c.getNetworkHandler().sendChatCommand("time set 18000");observedSleep=false;phase=22;changed=System.nanoTime();return;}
                leg++;startX=c.player.getX();startZ=c.player.getZ();changed=System.nanoTime();return;
            }
            if(phase==5){if(!c.player.hasVehicle()){if(seconds()>12)throw new IllegalStateException("Lower bell did not board");return;}next();return;}
            if(phase==6){if(c.player.hasVehicle()){if(seconds()>150)throw new IllegalStateException("Return did not finish");return;}checkLanding(c,true);capture(c,"surface-return.png");phase=18;changed=System.nanoTime();return;}
            if(phase==7){if(seconds()<8)return;if(!c.player.hasVehicle())throw new IllegalStateException("Emergency test failed to board");c.options.sneakKey.setPressed(true);next();return;}
            if(phase==8){if(seconds()<3)return;c.options.sneakKey.setPressed(false);if(c.player.hasVehicle())throw new IllegalStateException("Emergency dismount still riding");checkLanding(c,true);capture(c,"emergency-safe-return.png");command(c,"view street");next();return;}
            if(phase>=9&&phase<=12){
                if(seconds()<18||!queues(c))return;
                String[] names={"town-street.png","naturalized-cliff.png","overworld-town-overview.png","surface-entrance-overview.png"};capture(c,names[phase-9]);
                if(phase==9)command(c,"view cliff");if(phase==10)command(c,"view overview");if(phase==11)command(c,"view surface");
                if(phase==12&&NATURAL){command(c,"view bridge-destination");phase=30;changed=System.nanoTime();return;}next();return;
            }
            if(phase>=30&&phase<=35){
                if(seconds()<18||!queues(c))return;
                String[] names={"bridge-destination.png","library-interior.png","residence-bedroom.png","contained-water.png","side-cave.png","natural-floor.png"};
                String[] next={"library","bedroom","water","west-cave","floor"};capture(c,names[phase-30]);
                if(phase<35){command(c,"view "+next[phase-30]);phase++;}else phase=13;
                changed=System.nanoTime();return;
            }
            if(phase==13){
                if(NATURAL){command(c,"debugcheck");write("cabin-motion.json",motion);}
                command(c,"audit client");command(c,"intrusion");write("rides.json",rides);
                var r=new JsonObject();r.addProperty("pass",true);r.addProperty("real_bell_interactions",4);r.addProperty("real_walking_legs",walks.size());r.addProperty("real_surface_access_walks",accesses.size());r.addProperty("native_dimension","minecraft:overworld");r.addProperty("method","Bell right clicks, server-authoritative cabin rides, actual forward-key walking; Operator placement at the native-ground approach endpoint and photographic positions after round trip; actual stair walking enters and leaves the station; detached client camera for one exterior frame does not move the seated passenger.");write("journey.json",r);
                command(c,"view entrance");next();return;
            }
            if(phase==14){if(seconds()<12)return;bell(c,true);next();return;}
            if(phase==15){
                if(seconds()<10)return;if(!c.player.hasVehicle())throw new IllegalStateException("Restart test did not board");
                if(!restartReady){var restart=new JsonObject();restart.addProperty("riding_before_stop",true);restart.addProperty("y",c.player.getY());write("restart-ready.json",restart);restartReady=true;}
                if(seconds()>90)throw new IllegalStateException("Server did not stop for recovery test");
            }
        }catch(Exception e){var failure=new JsonObject();failure.addProperty("phase",phase);failure.addProperty("error",e.toString());if(c.world!=null)failure.addProperty("chunk_debug",c.worldRenderer.getChunksDebugString());write("failure.json",failure);e.printStackTrace();finished=true;c.options.forwardKey.setPressed(false);c.options.sneakKey.setPressed(false);System.err.println("ABYSS_PHASE7_FAILED");c.scheduleStop();}
    }
    private boolean surfaceWalk(MinecraftClient c,boolean inward){
        double x=JourneyClient.x+(inward?6.5:9.5+JourneyClient.approachLength),z=JourneyClient.z+70.5;
        double y=inward?JourneyClient.entryY+1:JourneyClient.approachY+1,dx=x-c.player.getX(),dz=z-c.player.getZ();
        c.player.setYaw((float)Math.toDegrees(Math.atan2(-dx,dz)));c.player.setPitch(0);
        boolean reached=Math.hypot(dx,dz)<.4&&Math.abs(c.player.getY()-y)<.6;
        c.options.forwardKey.setPressed(!reached);
        if(!reached){if(seconds()>60)throw new IllegalStateException("Surface stairs blocked at "+c.player.getPos()+" target "+x+","+y+","+z);return false;}
        if(c.player.getHealth()!=20||c.player.getAbilities().flying)throw new IllegalStateException("Surface access was not safe walking");
        var r=new JsonObject();r.addProperty("inward",inward);r.addProperty("seconds",seconds());r.addProperty("x",c.player.getX());r.addProperty("y",c.player.getY());r.addProperty("z",c.player.getZ());r.addProperty("health",c.player.getHealth());r.addProperty("pass",true);accesses.add(r);write("surface-access.json",accesses);return true;
    }
    private void checkLanding(MinecraftClient c,boolean upper){
        boolean pass=Math.abs(c.player.getX()-(JourneyClient.x+6.5))<.2&&Math.abs(c.player.getZ()-(JourneyClient.z+70.5))<.2&&Math.abs(c.player.getY()-(upper?JourneyClient.entryY+1:45))<.3&&c.player.getHealth()>0&&!c.player.hasVehicle();
        var r=new JsonObject();r.addProperty("upper",upper);r.addProperty("x",c.player.getX());r.addProperty("y",c.player.getY());r.addProperty("z",c.player.getZ());r.addProperty("health",c.player.getHealth());r.addProperty("pass",pass);rides.add(r);if(!pass)throw new IllegalStateException("Unsafe landing "+r);
    }
    private boolean queues(MinecraftClient c){
        boolean idle=c.worldRenderer.getChunksDebugString().matches(".*pC: 0+, pU: 0+(?:,|\\s|$).*");
        if(!idle&&seconds()>150)throw new IllegalStateException("Chunk queues did not drain: "+c.worldRenderer.getChunksDebugString());
        return idle;
    }
    private void capture(MinecraftClient c,String file)throws Exception{
        Files.createDirectories(OUT);try(var frame=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){frame.writeTo(OUT.resolve(file));}
        var r=new JsonObject();r.addProperty("file",file);r.addProperty("x",c.player.getX());r.addProperty("y",c.player.getY());r.addProperty("z",c.player.getZ());r.addProperty("yaw",c.player.getYaw());r.addProperty("pitch",c.player.getPitch());r.addProperty("riding",c.player.hasVehicle());r.addProperty("health",c.player.getHealth());r.addProperty("dimension",c.world.getRegistryKey().getValue().toString());r.addProperty("chunk_debug",c.worldRenderer.getChunksDebugString());r.addProperty("detached_photo_camera",c.getCameraEntity()!=c.player);r.addProperty("camera_position",c.gameRenderer.getCamera().getPos().toString());r.addProperty("camera_yaw",c.gameRenderer.getCamera().getYaw());r.addProperty("camera_pitch",c.gameRenderer.getCamera().getPitch());r.addProperty("gl_error",GL11.glGetError());r.addProperty("renderer",GL11.glGetString(GL11.GL_RENDERER));r.add("environment",EnvironmentClient.measurements());frames.add(r);write("captures.json",frames);
    }
    private void write(String name,Object value){try{Files.createDirectories(OUT);Files.writeString(OUT.resolve(name),new GsonBuilder().setPrettyPrinting().create().toJson(value));}catch(Exception e){throw new IllegalStateException(e);}}
}
