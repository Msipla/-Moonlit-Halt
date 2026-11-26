// Fabric 1.20.1 intermediary names, matching the existing research probes.
package lab;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.Set;
import java.util.Locale;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.class_1268; // Hand
import net.minecraft.class_1799; // ItemStack
import net.minecraft.class_243; // Vec3d
import net.minecraft.class_3222; // ServerPlayerEntity
import net.minecraft.class_3965; // BlockHitResult
import net.minecraft.class_2168; // ServerCommandSource
import net.minecraft.class_2338; // BlockPos
import net.minecraft.class_2350; // Direction
import net.minecraft.class_2561; // Text
import net.minecraft.class_7923; // Registries
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.eureka.util.ShipAssembler;
import org.valkyrienskies.eureka.ship.EurekaShipControl;
import org.valkyrienskies.eureka.blockentity.ShipHelmBlockEntity;
import org.valkyrienskies.mod.common.entity.ShipMountingEntity;
import org.joml.Vector3d;
import org.valkyrienskies.mod.api.SeatedControllingPlayer;
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider;

public final class LabShips implements ModInitializer {
    static final int MAX_HULL_VOLUME=8192;
    static final Set<String> HULL = Set.of("minecraft:oak_planks", "minecraft:oak_fence",
        "vs_eureka:oak_ship_helm", "vs_eureka:anchor", "vs_eureka:balloon", "vs_eureka:white_balloon",
        "vs_eureka:floater");
    private long designated = -1;
    private int pulseUntil;
    private class_3222 labPilot;

    @Override
    public void onInitialize() {
        if (!Boolean.getBoolean("engineering.shipLab")) return;
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (labPilot != null && (server.method_3780() >= pulseUntil || !labPilot.method_5765())) {
                stop(server.method_3739());
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<class_2168>literal("engineering_ship")
                .requires(source -> source.method_9259(4) && source.method_9228() == null)
                .then(LiteralArgumentBuilder.<class_2168>literal("assemble")
                    .then(coordinate("x", 1, 15).then(coordinate("y", -60, -53).then(coordinate("z", 1, 15)
                        .executes(c -> assemble(c.getSource(), new class_2338(
                            IntegerArgumentType.getInteger(c,"x"),IntegerArgumentType.getInteger(c,"y"),
                            IntegerArgumentType.getInteger(c,"z"))))))))
                .then(LiteralArgumentBuilder.<class_2168>literal("sample").executes(c -> sample(c.getSource())))
                .then(LiteralArgumentBuilder.<class_2168>literal("steer")
                    .then(RequiredArgumentBuilder.<class_2168,String>argument("direction", StringArgumentType.word())
                        .then(RequiredArgumentBuilder.<class_2168,Float>argument("forward", FloatArgumentType.floatArg(-1,1))
                            .then(RequiredArgumentBuilder.<class_2168,Float>argument("up",FloatArgumentType.floatArg(-1,1))
                                .then(RequiredArgumentBuilder.<class_2168,Float>argument("left",FloatArgumentType.floatArg(-1,1))
                                    .executes(c -> steer(c.getSource(), StringArgumentType.getString(c,"direction"),
                                        FloatArgumentType.getFloat(c,"forward"),FloatArgumentType.getFloat(c,"up"),
                                        FloatArgumentType.getFloat(c,"left"))))))))
                .then(LiteralArgumentBuilder.<class_2168>literal("stop").executes(c -> stop(c.getSource())))));
    }

    private static RequiredArgumentBuilder<class_2168,Integer> coordinate(String name, int min, int max) {
        return RequiredArgumentBuilder.argument(name,IntegerArgumentType.integer(min,max));
    }

    private static String blockId(net.minecraft.class_2680 state) {
        return class_7923.field_41175.method_10221(state.method_26204()).toString();
    }

    private int assemble(class_2168 source, class_2338 helm) {
        var world = source.method_9225();
        if (designated!=-1 || !blockId(world.method_8320(helm)).equals("vs_eureka:oak_ship_helm"))
            return say(source,"{\"error\":\"expected one new Eureka ship helm\"}");
        var ship=ShipAssembler.INSTANCE.collectBlocks(world,helm,state->HULL.contains(blockId(state)));
        if (ship==null) return say(source,"{\"error\":\"native assembly rejected the hull\"}");
        designated=ship.getId();
        var area=ship.getShipAABB();
        int pinned=0;
        if(area!=null) {
            int minX=area.minX()>>4, maxX=area.maxX()>>4, minZ=area.minZ()>>4, maxZ=area.maxZ()>>4;
            if ((maxX-minX+1)*(maxZ-minZ+1)<=4)
                for (int cx=minX;cx<=maxX;cx++) for(int cz=minZ;cz<=maxZ;cz++) {
                    world.method_17988(cx,cz,true); // ServerLevel.setChunkForced
                    pinned++;
                }
        }
        return say(source,"{\"id\":\""+designated+"\",\"pinnedChunks\":"+pinned+"}");
    }

    private LoadedServerShip ship(class_2168 source) {
        var world = (IShipObjectWorldServerProvider)source.method_9225();
        for (LoadedServerShip tested : world.getShipObjectWorld().getLoadedShips()) {
            if (tested.getId()==designated) return tested;
        }
        return null;
    }

    private int sample(class_2168 source) {
        var ship=ship(source);
        if (ship==null) return say(source,"{\"error\":\"designated ship not loaded\"}");
        var t=ship.getTransform();
        var p=t.getPositionInWorld();
        var v=ship.getVelocity();
        var q=t.getShipToWorldRotation();
        var control=ship.getAttachment(EurekaShipControl.class);
        var pipeline=((IShipObjectWorldServerProvider)source.method_9211()).getVsPipeline();
        return say(source,String.format(Locale.ROOT,
            "{\"id\":\"%d\",\"p\":[%.6f,%.6f,%.6f],\"v\":[%.6f,%.6f,%.6f],"+
            "\"rotation\":[%.6f,%.6f,%.6f,%.6f],\"static\":%s,\"helms\":%d,\"balloons\":%d,\"anchors\":%d,\"physicsRunning\":%s,\"voxelChunks\":%d,\"mounted\":%s}",
            ship.getId(),p.x(),p.y(),p.z(),v.x(),v.y(),v.z(),q.x(),q.y(),q.z(),q.w(),ship.isStatic(),
            control==null?0:control.getHelms(),control==null?0:control.getBalloons(),control==null?0:control.getAnchors(),
            pipeline!=null && pipeline.getArePhysicsRunning(),pipeline==null?0:pipeline.getLoadedVoxelChunks(),
            control!=null && control.getSeatedPlayer()!=null && control.getSeatedPlayer().method_5765()));
    }

    private int steer(class_2168 source, String heading, float forward, float up, float left) {
        var ship=ship(source);
        var direction=class_2350.method_10168(heading);
        var pilot=source.method_9211().method_3760().method_14566(System.getProperty("engineering.shipPilot","LabOwner"));
        var control=ship==null?null:ship.getAttachment(EurekaShipControl.class);
        if (control==null || direction==null || !Set.of("north","east","south","west").contains(heading))
            return say(source,"{\"error\":\"native ship or horizontal heading missing\"}");
        if (pilot==null || pilot.method_51469()!=source.method_9225() || !pilot.method_7337() || control.getAnchors()<1)
            return say(source,"{\"error\":\"connected creative pilot and installed anchor required\"}");
        if (!engage(source.method_9225(),ship,control,pilot,direction,forward,up,left))
            return say(source,"{\"error\":\"native helm refused to mount pilot\"}");
        labPilot=pilot;
        pulseUntil=source.method_9211().method_3780()+110;
        return sample(source);
    }

    static boolean engage(net.minecraft.class_3218 level, LoadedServerShip ship, EurekaShipControl control,
                          class_3222 pilot, class_2350 direction, float forward, float up, float left) {
        if (control.getSeatedPlayer()!=null && control.getSeatedPlayer()!=pilot) return false;
        if (control.getSeatedPlayer()!=pilot || !(pilot.method_5854() instanceof ShipMountingEntity)) {
            if (pilot.method_5765()) return false;
            var helm=findHelm(level,ship);
            if (helm==null || !(level.method_8321(helm) instanceof ShipHelmBlockEntity nativeHelm)) return false;
            var position=ship.getShipToWorld().transformPosition(new Vector3d(
                helm.method_10263()+0.5,helm.method_10264()+1.5,helm.method_10260()+0.5));
            var previous=pilot.method_19538();
            float yaw=pilot.method_36454();
            float pitch=pilot.method_36455();
            boolean sneaking=pilot.method_5715();
            pilot.method_14251(level,position.x,position.y,position.z,yaw,pitch);
            pilot.method_5660(false);
            var hit=new class_3965(class_243.method_24953(helm),class_2350.field_11036,helm,false);
            pilot.field_13974.method_14262(pilot,level,class_1799.field_8037,class_1268.field_5808,hit);
            if (!pilot.method_5765()) nativeHelm.sit(pilot,false);
            if (!(pilot.method_5854() instanceof ShipMountingEntity)) {
                pilot.method_14251(level,previous.field_1352,previous.field_1351,previous.field_1350,yaw,pitch);
                pilot.method_5660(sneaking);
                return false;
            }
        }
        control.setSeatedPlayer(pilot);
        control.setAnchorsActive(0);
        ship.setStatic(false);
        var input=new SeatedControllingPlayer(direction);
        input.setForwardImpulse(forward);
        input.setUpImpulse(up);
        input.setLeftImpulse(left);
        ship.setAttachment(input);
        return true;
    }

    static class_2338 findHelm(net.minecraft.class_3218 level,LoadedServerShip ship) {
        var box=ship.getShipAABB();
        if (box==null || (long)(box.maxX()-box.minX()+1)*(box.maxY()-box.minY()+1)*(box.maxZ()-box.minZ()+1)>MAX_HULL_VOLUME) return null;
        for (var at:class_2338.method_10094(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ())) {
            if (level.method_37118(at) && level.method_8321(at) instanceof ShipHelmBlockEntity) return at.method_10062();
        }
        return null;
    }

    static void anchor(LoadedServerShip ship, EurekaShipControl control, class_3222 pilot) {
        ship.removeAttachment(SeatedControllingPlayer.class);
        control.setCruising(false);
        if (control.getSeatedPlayer()==pilot && pilot!=null) {
            if (pilot.method_5854() instanceof ShipMountingEntity) pilot.method_5848();
            control.setSeatedPlayer(null);
        }
        control.setAnchorsActive(control.getAnchors()>0?1:0);
        if (control.getAnchors()>0) ship.setStatic(true);
    }

    private int stop(class_2168 source) {
        var ship=ship(source);
        var control=ship==null?null:ship.getAttachment(EurekaShipControl.class);
        if (control==null || control.getAnchors()<1)
            return say(source,"{\"error\":\"a native anchor must be part of the assembled ship\"}");
        anchor(ship,control,labPilot);
        labPilot=null;
        return sample(source);
    }

    private static int say(class_2168 source,String body) {
        source.method_9213(class_2561.method_43470(body));
        return 1;
    }
}
