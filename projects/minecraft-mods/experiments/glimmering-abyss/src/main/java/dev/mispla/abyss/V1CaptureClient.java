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
    private static final boolean FINISH=Boolean.getBoolean("abyss.capture.v1.finish");
    private static final boolean BEFORE=Boolean.getBoolean("abyss.capture.v1.before");
    private static final boolean MECHANICS=Boolean.getBoolean("abyss.capture.v1.mechanics");
    private static final boolean TRANSPORT=Boolean.getBoolean("abyss.capture.v1.transport");
    private static final boolean RECONNECT=Boolean.getBoolean("abyss.capture.v1.reconnect");
    private static final Path OUT=Path.of(BEFORE?"abyss-v1-before":MECHANICS?"abyss-v1-mechanics":RECONNECT?"abyss-v1-reconnect":TRANSPORT?"abyss-v1-transport":"abyss-v1-captures");
    private static final double[][] ROUTE={{47.5,-57.5},{47.5,-83.5},{22.5,-83.5},{22.5,-99.5},{-27.5,-99.5},{-27.5,-123.5},{-77.5,-123.5},{-77.5,-147.5},{-125.5,-147.5},{-125.5,-183.5},{-150.5,-183.5},{-150.5,-213.5},{-89.5,-213.5},{-89.5,-175.5},{-65.5,-175.5},{-65.5,-123.5},{-77.5,-123.5},{-27.5,-123.5},{-27.5,-99.5},{22.5,-99.5},{22.5,-83.5},{47.5,-83.5},{47.5,-57.5}};
    private static final double[][] SETTLEMENT={{47.5,-23.5},{47.5,-57.5},{47.5,-77.5},{62.5,-77.5},{62.5,-71.5},{69.5,-71.5},{69.5,-84.5},{78.5,-84.5},{69.5,-84.5},{69.5,-71.5},{62.5,-71.5},{62.5,-77.5},{47.5,-77.5},{47.5,-23.5},{65.5,-23.5},{86.5,-23.5},{86.5,-31.5},{94.5,-31.5},{94.5,-38.5},{103.5,-38.5},{94.5,-38.5},{94.5,-31.5},{86.5,-31.5},{86.5,-23.5},{86.5,-13.5},{83.5,-13.5},{83.5,19.5},{100.5,19.5},{100.5,26.5},{100.5,19.5},{83.5,19.5},{83.5,42.5},{78.5,42.5},{78.5,58.5},{78.5,42.5},{83.5,42.5},{83.5,19.5},{83.5,-13.5},{86.5,-13.5},{86.5,-23.5},{65.5,-23.5},{65.5,-30.5},{65.5,-23.5},{47.5,-23.5}};
    private static final String[] ROOM_VIEWS={"workshop-entry","residence-entry","slate-entry","store-entry","tea-entry","clock-entry","library-entry"};
    private static final String[] ROOM_NAMES={"original-workshop","brick-residence","slate-residence","store-shop","tea-room","clock-reading-room","library-refuge"};
    private static final float[] ROOM_YAWS={100,180,180,90,170,-140,135};
    private static final double[][][] ROOMS={
        {{42.5,44.5},{42.5,36.5},{42.5,28.5},{52.5,28.5},{52.5,20.5},{53.5,20.5},{53.5,28.5},{42.5,28.5},{53.5,28.5},{53.5,20.5},{52.5,20.5},{52.5,28.5},{42.5,28.5},{42.5,44.5}},
        {{38.5,-3.5},{38.5,-8.5},{39.5,-8.5},{39.5,-15.5},{36.5,-15.5},{36.5,-17.5},{34.5,-17.5},{34.5,-10.5},{38.5,-10.5},{34.5,-10.5},{34.5,-17.5},{36.5,-17.5},{36.5,-15.5},{39.5,-15.5},{39.5,-8.5},{38.5,-8.5},{38.5,-3.5}},
        {{56.5,-3.5},{56.5,-8.5},{55.5,-8.5},{55.5,-14.5},{54.5,-14.5},{54.5,-16.5},{52.5,-16.5},{52.5,-9.5},{57.5,-9.5},{52.5,-9.5},{52.5,-16.5},{54.5,-16.5},{54.5,-14.5},{55.5,-14.5},{55.5,-8.5},{56.5,-8.5},{56.5,-3.5}},
        {{72.5,8.5},{72.5,2.5},{72.5,-3.5},{74.5,-3.5},{72.5,-3.5},{72.5,2.5},{72.5,8.5}},
        {{32.5,-26.5},{32.5,-30.5},{33.5,-30.5},{33.5,-35.5},{33.5,-30.5},{32.5,-30.5},{32.5,-26.5}},
        {{58.5,-49.5},{58.5,-53.5},{56.5,-53.5},{56.5,-58.5},{56.5,-53.5},{58.5,-53.5},{58.5,-49.5}},
        {{-39.5,35.5},{-46.5,35.5},{-46.5,38.5},{-50.5,38.5},{-46.5,38.5},{-46.5,35.5},{-39.5,35.5}}
    };
    private final JsonArray frames=new JsonArray(),motion=new JsonArray(),walks=new JsonArray(),townWalks=new JsonArray(),roomWalks=new JsonArray();
    private int room;private String nodeLabel,roomFrame;
    private int state=-1,leg,night;private boolean connected,done,slept,restartReady;private int dreamCaptured;
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
                connected=true;c.options.getViewDistance().setValue(16);c.options.getSimulationDistance().setValue(6);c.options.getFov().setValue(75);c.options.getGamma().setValue(.5);c.options.getMaxFps().setValue(45);c.options.getEnableVsync().setValue(false);c.options.pauseOnLostFocus=false;c.options.hudHidden=false;c.options.tutorialStep=net.minecraft.client.tutorial.TutorialStep.NONE;
                String address="127.0.0.1:25585";ConnectScreen.connect(new TitleScreen(),c,ServerAddress.parse(address),new ServerInfo("V1 release preview",address,false),false);return;
            }
            if(restartReady&&c.world==null){done=true;c.scheduleStop();return;}
            if(c.player==null||c.world==null||!JourneyClient.ready())return;
            if(RECONNECT){
                if(state==-1){next(100);return;}if(seconds()<12)return;
                checkLanding(c,true);capture(c,"reconnected-safe-station.png");
                var r=new JsonObject();r.addProperty("pass",true);r.addProperty("same_offline_identity",true);r.addProperty("health",c.player.getHealth());write("recovery.json",r);finish(c);return;
            }
            if(BEFORE){
                if(state==-1){if(!c.player.hasPermissionLevel(2))return;c.options.hudHidden=true;c.getNetworkHandler().sendChatCommand("gamemode creative");next(200);return;}
                if(state==200){if(seconds()<2)return;c.player.getAbilities().flying=true;c.player.sendAbilitiesUpdate();c.getNetworkHandler().sendChatCommand("tp @s "+(JourneyClient.x+64.5)+" 48 "+(JourneyClient.z-18.5)+" -129 1");next(201);return;}
                if(!readyFrame(c))return;
                if(state==201){capture(c,"orphan-glimmerstone.png");cmd(c,"view cliff");next(202);}
                else if(state==202){capture(c,"town-cliff.png");cmd(c,"view rift");next(203);}
                else{capture(c,"main-rift.png");finish(c);}return;
            }
            if(TRANSPORT||MECHANICS&&state>=100){transport(c);return;}
            if(!c.player.isAlive())throw new IllegalStateException("Player died");
            if(DreamScreen.step>dreamCaptured&&DreamScreen.alpha()>.98f&&DreamScreen.renderedFrames>=3){
                capture(c,"sleep-dream-"+DreamScreen.step+".png");var r=new JsonObject();r.addProperty("step",DreamScreen.step);r.addProperty("sleeping_at_capture",c.player.isSleeping());write("dream-"+DreamScreen.step+".json",r);dreamCaptured=DreamScreen.step;
            }
            if(c.player.hasVehicle()){
                var p=c.player.getVehicle();var r=new JsonObject();r.addProperty("state",state);r.addProperty("x",p.getX());r.addProperty("y",p.getY());r.addProperty("z",p.getZ());r.addProperty("passenger_dx",c.player.getX()-p.getX());r.addProperty("passenger_dz",c.player.getZ()-p.getZ());motion.add(r);
            }
            if(Boolean.getBoolean("abyss.capture.v1.second")){
                if(state==-1){if(!c.player.hasPermissionLevel(2))return;cmd(c,"view overview");next(90);return;}
                if(readyFrame(c)){capture(c,"second-seed-overview.png");finish(c);}return;
            }
            if(state==-1){next(-2);return;}
            if(state==-2){if(seconds()<5||!c.player.hasPermissionLevel(2))return;if(FINISH){c.options.hudHidden=true;cmd(c,"view overview");next(5);}else{cmd(c,"view outside");next(-3);}return;}
            if(state==-3){if(seconds()<5)return;cmd(c,"sleepstate before-1");cmd(c,"bedtime");next(0);return;}
            if(state==0){
                if(seconds()<3)return;var p=new BlockPos(JourneyClient.x+400,c.player.getBlockY(),JourneyClient.z+70);
                if(Math.abs(c.player.getX()-(JourneyClient.x+401.5))>2||!c.world.getBlockState(p).isOf(net.minecraft.block.Blocks.BLUE_BED)){
                    if(seconds()>25)throw new IllegalStateException("Sleep fixture did not arrive at "+c.player.getPos()+" bed="+c.world.getBlockState(p));return;
                }
                c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(p),Direction.UP,p,false));slept=false;next(1);return;
            }
            if(state==1){
                slept|=c.player.isSleeping();if(!slept||c.player.isSleeping()||DreamScreen.step!=0){if(seconds()>40)throw new IllegalStateException("Sleep/wake flow did not finish; observed="+slept+" sleeping="+c.player.isSleeping()+" dream="+DreamScreen.step+" at="+c.player.getPos());return;}
                cmd(c,"sleepstate after-"+(night+1));
                if(++night<2){cmd(c,"sleepstate before-2");cmd(c,"bedtime");next(0);return;}
                if(dreamCaptured!=2||!c.player.getInventory().contains(new net.minecraft.item.ItemStack(Journey.ECHO)))throw new IllegalStateException("Sleep dreams or guide absent");
                c.options.hudHidden=true;cmd(c,"view entrance");next(MECHANICS?100:2);return;
            }
            if(state==2){if(!readyFrame(c))return;capture(c,"upper-station.png");bell(c,true);next(3);return;}
            if(state==3){if(!c.player.hasVehicle()){if(seconds()>10)throw new IllegalStateException("Boarding failed");return;}next(4);return;}
            if(state==4){if(c.player.hasVehicle()){if(seconds()>100)throw new IllegalStateException("Ride deadline");return;}checkLanding(c,false);cmd(c,"view overview");next(5);return;}
            if(state==91){if(!readyFrame(c))return;capture(c,"town-culling-disabled.png");c.chunkCullingEnabled=true;cmd(c,"view rift");next(6);return;}
            if(state>=5&&state<=9){
                if(!readyFrame(c))return;String[] names={"town-in-abyss.png","main-rift.png","folded-gallery.png","warm-return.png","town-street.png"};String[] views={"rift","gallery","homeward","street"};
                capture(c,names[state-5]);if(state==5){if(FINISH){cmd(c,"view rift");next(6);}else{c.chunkCullingEnabled=false;next(91);}return;}if(state<9){cmd(c,"view "+views[state-5]);next(state+1);}else{cmd(c,"view east-lane");next(15);}return;
            }
            if(state==15){if(!readyFrame(c))return;capture(c,"east-wall-and-lane.png");cmd(c,"view public-nook");next(16);return;}
            if(state==16){if(!readyFrame(c))return;capture(c,"sheltered-public-nook.png");cmd(c,"view settlement-overlook");next(17);return;}
            if(state==17){if(!readyFrame(c))return;capture(c,"settlement-on-rock-shelves.png");cmd(c,"view north-pocket");next(18);return;}
            if(state==18){if(!readyFrame(c))return;capture(c,"north-rock-and-bakehouse.png");cmd(c,"view orphan");next(19);return;}
            if(state==19){if(!readyFrame(c))return;capture(c,"orphan-glimmerstone.png");cmd(c,"view cliff");next(29);return;}
            if(state==29){if(!readyFrame(c))return;capture(c,"town-cliff.png");cmd(c,"view route-start");next(10);return;}
            if(state==10){if(seconds()<5)return;leg=1;walkX=c.player.getX();walkZ=c.player.getZ();next(11);return;}
            if(state==11){
                if(leg==ROUTE.length){c.options.forwardKey.setPressed(false);capture(c,"walk-return-to-town.png");cmd(c,"view settlement-start");next(20);return;}
                double dx=JourneyClient.x+ROUTE[leg][0]-c.player.getX(),dz=JourneyClient.z+ROUTE[leg][1]-c.player.getZ();
                c.player.setYaw((float)Math.toDegrees(Math.atan2(-dx,dz)));c.player.setPitch(0);c.options.forwardKey.setPressed(true);
                if(Math.hypot(dx,dz)>.4){if(seconds()>60)throw new IllegalStateException("Loop blocked leg "+leg+" at "+c.player.getPos());return;}
                c.options.forwardKey.setPressed(false);var r=new JsonObject();r.addProperty("leg",leg);r.addProperty("start_x",walkX);r.addProperty("start_z",walkZ);r.addProperty("end_x",c.player.getX());r.addProperty("end_y",c.player.getY());r.addProperty("end_z",c.player.getZ());r.addProperty("health",c.player.getHealth());r.addProperty("flying",c.player.getAbilities().flying);walks.add(r);write("walk.json",walks);
                if(c.player.getAbilities().flying||c.player.getHealth()<20)throw new IllegalStateException("Unsafe exploration walk");
                int reached=leg;leg++;walkX=c.player.getX();walkZ=c.player.getZ();changed=System.nanoTime();
                if(reached==10||reached==11||reached==16){
                    nodeLabel=reached==10?"fault-observation-bay.png":reached==11?"lower-gallery-terminus.png":"signed-homeward-junction.png";
                    c.player.setYaw(reached==10?-165:reached==11?-65:-30);c.player.setPitch(-8);next(50);
                }return;
            }
            if(state==50){if(!readyFrame(c))return;capture(c,nodeLabel);next(11);return;}
            if(state==20){if(seconds()<5)return;leg=1;walkX=c.player.getX();walkZ=c.player.getZ();next(21);return;}
            if(state==21){
                if(leg==SETTLEMENT.length){c.options.forwardKey.setPressed(false);c.options.hudHidden=false;cmd(c,"sleepstate house-before");cmd(c,"house-bedtime");next(22);return;}
                double dx=JourneyClient.x+SETTLEMENT[leg][0]-c.player.getX(),dz=JourneyClient.z+SETTLEMENT[leg][1]-c.player.getZ();
                c.player.setYaw((float)Math.toDegrees(Math.atan2(-dx,dz)));c.player.setPitch(0);c.options.forwardKey.setPressed(true);
                if(Math.hypot(dx,dz)>.4){if(seconds()>60)throw new IllegalStateException("Settlement blocked leg "+leg+" at "+c.player.getPos());return;}
                c.options.forwardKey.setPressed(false);var r=new JsonObject();r.addProperty("leg",leg);r.addProperty("start_x",walkX);r.addProperty("start_z",walkZ);r.addProperty("end_x",c.player.getX());r.addProperty("end_y",c.player.getY());r.addProperty("end_z",c.player.getZ());r.addProperty("health",c.player.getHealth());r.addProperty("flying",c.player.getAbilities().flying);townWalks.add(r);write("settlement-walk.json",townWalks);
                if(c.player.getAbilities().flying||c.player.getHealth()!=20)throw new IllegalStateException("Unsafe settlement walk");
                if(leg==7||leg==19||leg==28||leg==33||leg==41){c.player.setPitch(8);capture(c,"settlement-interior-"+leg+".png");}
                leg++;walkX=c.player.getX();walkZ=c.player.getZ();changed=System.nanoTime();return;
            }
            if(state==22){
                if(seconds()<3)return;var p=new BlockPos(JourneyClient.x+106,47,JourneyClient.z-41);
                if(!c.world.getBlockState(p).isOf(net.minecraft.block.Blocks.BLUE_BED))throw new IllegalStateException("New residence has no real bed");
                c.interactionManager.interactBlock(c.player,Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(p),Direction.UP,p,false));slept=false;next(23);return;
            }
            if(state==23){
                slept|=c.player.isSleeping();if(!slept||c.player.isSleeping()||DreamScreen.step!=0){if(seconds()>40)throw new IllegalStateException("New residence sleep/wake failed");return;}
                cmd(c,"sleepstate house-after");var r=new JsonObject();r.addProperty("real_sleep_observed",slept);r.addProperty("health",c.player.getHealth());r.addProperty("flying",c.player.getAbilities().flying);write("residence-sleep.json",r);next(24);return;
            }
            if(state==24){if(seconds()<1||c.currentScreen instanceof SleepingChatScreen)return;capture(c,"residence-after-sleep.png");room=0;next(30);return;}
            if(state==30){
                if(room==ROOMS.length){if(FINISH)finish(c);else{cmd(c,"view lower");next(12);}return;}
                cmd(c,"view "+ROOM_VIEWS[room]);next(31);return;
            }
            if(state==31){if(seconds()<3)return;leg=1;walkX=c.player.getX();walkZ=c.player.getZ();next(32);return;}
            if(state==32){
                var path=ROOMS[room];
                if(leg==path.length){c.options.forwardKey.setPressed(false);room++;next(30);return;}
                double dx=JourneyClient.x+path[leg][0]-c.player.getX(),dz=JourneyClient.z+path[leg][1]-c.player.getZ();
                c.player.setYaw((float)Math.toDegrees(Math.atan2(-dx,dz)));c.player.setPitch(0);c.options.forwardKey.setPressed(true);
                if(Math.hypot(dx,dz)>.18){if(seconds()>60)throw new IllegalStateException("Interior blocked "+ROOM_NAMES[room]+" leg "+leg+" at "+c.player.getPos());return;}
                c.options.forwardKey.setPressed(false);var r=new JsonObject();r.addProperty("room",ROOM_NAMES[room]);r.addProperty("leg",leg);r.addProperty("start_x",walkX);r.addProperty("start_z",walkZ);r.addProperty("end_x",c.player.getX());r.addProperty("end_y",c.player.getY());r.addProperty("end_z",c.player.getZ());r.addProperty("health",c.player.getHealth());r.addProperty("flying",c.player.getAbilities().flying);roomWalks.add(r);write("interior-walk.json",roomWalks);
                if(c.player.getAbilities().flying||c.player.getHealth()!=20)throw new IllegalStateException("Unsafe interior walk");
                int reached=leg;leg++;walkX=c.player.getX();walkZ=c.player.getZ();changed=System.nanoTime();
                if(reached==path.length/2||room==0&&reached==2){
                    roomFrame=room==0&&reached==2?"interior-original-workshop-ground.png":"interior-"+ROOM_NAMES[room]+".png";
                    c.player.setYaw(room==0&&reached==2?-75:ROOM_YAWS[room]);c.player.setPitch(8);next(33);
                }return;
            }
            if(state==33){if(!readyFrame(c))return;capture(c,roomFrame);next(32);return;}
            if(state==12){if(seconds()<4)return;bell(c,false);next(13);return;}
            if(state==13){if(!c.player.hasVehicle()){if(seconds()>10)throw new IllegalStateException("Return boarding failed");return;}next(14);return;}
            if(state==14){if(c.player.hasVehicle()){if(seconds()>100)throw new IllegalStateException("Return ride deadline");return;}checkLanding(c,true);write("motion.json",motion);cmd(c,"debugcheck");finish(c);}
        }catch(Exception ex){var r=new JsonObject();r.addProperty("state",state);r.addProperty("leg",leg);r.addProperty("error",ex.toString());if(c.player!=null){r.addProperty("position",c.player.getPos().toString());r.addProperty("permission",c.player.hasPermissionLevel(2));r.addProperty("sleeping",c.player.isSleeping());r.addProperty("dream_step",DreamScreen.step);try{capture(c,"failure-state.png");}catch(Exception ignored){}}write("failure.json",r);ex.printStackTrace();done=true;c.options.forwardKey.setPressed(false);c.options.sneakKey.setPressed(false);c.scheduleStop();}
    }
    private void transport(MinecraftClient c)throws Exception{
        if(state==-1){if(!c.player.hasPermissionLevel(2))return;cmd(c,"view entrance");next(100);return;}
        if(state==100){if(seconds()<8)return;bell(c,true);next(101);return;}
        if(state==101){
            if(seconds()<8)return;if(!c.player.hasVehicle())throw new IllegalStateException("Early-exit test failed to board");
            c.options.sneakKey.setPressed(true);next(102);return;
        }
        if(state==102){
            if(seconds()<3)return;c.options.sneakKey.setPressed(false);
            if(c.player.hasVehicle())throw new IllegalStateException("Early-exit still riding");
            checkLanding(c,true);capture(c,"emergency-safe-return.png");
            var r=new JsonObject();r.addProperty("pass",true);r.addProperty("health",c.player.getHealth());write("emergency.json",r);
            next(104);return;
        }
        if(state==104){
            // END_CLIENT_TICK changes the key for the next input update. Do not
            // interact while the player's previous sneak state is still active.
            if(seconds()<1)return;
            if(c.player.isSneaking()){if(seconds()>5)throw new IllegalStateException("Sneak release did not reach the player input");return;}
            var r=new JsonObject();r.addProperty("sneaking_before_board",c.player.isSneaking());r.addProperty("release_wait_seconds",seconds());write("reboard-input.json",r);
            bell(c,true);next(103);return;
        }
        if(state==103){
            if(seconds()<10)return;if(!c.player.hasVehicle())throw new IllegalStateException("Restart test failed to board");
            if(!restartReady){var r=new JsonObject();r.addProperty("riding_before_stop",true);r.addProperty("y",c.player.getY());write("restart-ready.json",r);restartReady=true;}
            if(seconds()>90)throw new IllegalStateException("Server did not stop during journey");
        }
    }
    private boolean readyFrame(MinecraftClient c){
        if(seconds()>160)throw new IllegalStateException("Unsettled chunks "+c.worldRenderer.getChunksDebugString());
        return seconds()>18&&c.worldRenderer.getChunksDebugString().matches(".*pC: 0+, pU: 0+(?:,|\\s|$).*");
    }
    private void checkLanding(MinecraftClient c,boolean upper){
        if(c.player.hasVehicle()||Math.abs(c.player.getX()-(JourneyClient.x+(upper?6.5:30.5)))>.3||Math.abs(c.player.getZ()-(JourneyClient.z+(upper?70.5:58.5)))>.3||Math.abs(c.player.getY()-(upper?JourneyClient.entryY+1:45))>.3||c.player.getHealth()!=20)throw new IllegalStateException("Incorrect safe station");
    }
    private void capture(MinecraftClient c,String name)throws Exception{
        Files.createDirectories(OUT);try(var f=ScreenshotRecorder.takeScreenshot(c.getFramebuffer())){f.writeTo(OUT.resolve(name));}
        var r=new JsonObject();r.addProperty("file",name);r.addProperty("position",c.player.getPos().toString());r.addProperty("yaw",c.player.getYaw());r.addProperty("pitch",c.player.getPitch());r.addProperty("health",c.player.getHealth());r.addProperty("sleeping",c.player.isSleeping());r.addProperty("chunk_culling",c.chunkCullingEnabled);r.addProperty("cavern_backdrop_active",JourneyClient.cavernBackdropActive(c.gameRenderer.getCamera()));r.addProperty("camera_sky_light",c.world.getLightLevel(net.minecraft.world.LightType.SKY,BlockPos.ofFloored(c.gameRenderer.getCamera().getPos())));r.addProperty("dream_rendered_frames",DreamScreen.renderedFrames);r.addProperty("chunk_debug",c.worldRenderer.getChunksDebugString());r.addProperty("gl_error",GL11.glGetError());r.addProperty("renderer",GL11.glGetString(GL11.GL_RENDERER));r.add("environment",EnvironmentClient.measurements());frames.add(r);write("captures.json",frames);
    }
    private void finish(MinecraftClient c){var r=new JsonObject();r.addProperty("status","SPATIAL_PREVIEW_CAPTURED");r.addProperty("android_tested",false);r.addProperty("aesthetic_accepted",false);write("result.json",r);done=true;c.options.forwardKey.setPressed(false);c.options.sneakKey.setPressed(false);c.scheduleStop();}
    private void write(String name,Object value){try{Files.createDirectories(OUT);Files.writeString(OUT.resolve(name),new GsonBuilder().setPrettyPrinting().create().toJson(value));}catch(Exception ex){throw new IllegalStateException(ex);}}
}
