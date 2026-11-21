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
import net.minecraft.class_2168; // ServerCommandSource
import net.minecraft.class_2338; // BlockPos
import net.minecraft.class_2350; // Direction
import net.minecraft.class_2561; // Text
import net.minecraft.class_7923; // Registries
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.eureka.util.ShipAssembler;
import org.valkyrienskies.eureka.ship.EurekaShipControl;
import org.valkyrienskies.mod.api.SeatedControllingPlayer;
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider;

public final class LabShips implements ModInitializer {
    private static final Set<String> HULL = Set.of("minecraft:oak_planks", "minecraft:oak_fence",
        "vs_eureka:oak_ship_helm", "vs_eureka:anchor", "vs_eureka:balloon", "vs_eureka:white_balloon",
        "vs_eureka:floater");
    private long designated = -1;

    @Override
    public void onInitialize() {
        if (!Boolean.getBoolean("engineering.shipLab")) return;
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
                                .executes(c -> steer(c.getSource(), StringArgumentType.getString(c,"direction"),
                                    FloatArgumentType.getFloat(c,"forward"),FloatArgumentType.getFloat(c,"up")))))))
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
            "\"rotation\":[%.6f,%.6f,%.6f,%.6f],\"static\":%s,\"helms\":%d,\"balloons\":%d,\"anchors\":%d,\"physicsRunning\":%s,\"voxelChunks\":%d}",
            ship.getId(),p.x(),p.y(),p.z(),v.x(),v.y(),v.z(),q.x(),q.y(),q.z(),q.w(),ship.isStatic(),
            control==null?0:control.getHelms(),control==null?0:control.getBalloons(),control==null?0:control.getAnchors(),
            pipeline!=null && pipeline.getArePhysicsRunning(),pipeline==null?0:pipeline.getLoadedVoxelChunks()));
    }

    private int steer(class_2168 source, String heading, float forward, float up) {
        var ship=ship(source);
        var direction=class_2350.method_10168(heading);
        var control=ship==null?null:ship.getAttachment(EurekaShipControl.class);
        if (control==null || direction==null || !Set.of("north","east","south","west").contains(heading))
            return say(source,"{\"error\":\"native ship or horizontal heading missing\"}");
        control.setAnchorsActive(0);
        var input=new SeatedControllingPlayer(direction);
        input.setForwardImpulse(forward);
        input.setUpImpulse(up);
        ship.setAttachment(SeatedControllingPlayer.class,input);
        return sample(source);
    }

    private int stop(class_2168 source) {
        var ship=ship(source);
        var control=ship==null?null:ship.getAttachment(EurekaShipControl.class);
        if (control==null || control.getAnchors()<1)
            return say(source,"{\"error\":\"a native anchor must be part of the assembled ship\"}");
        var input=ship.getAttachment(SeatedControllingPlayer.class);
        if (input!=null) {
            input.setForwardImpulse(0);
            input.setUpImpulse(0);
            input.setLeftImpulse(0);
        }
        control.setAnchorsActive(1);
        return sample(source);
    }

    private static int say(class_2168 source,String body) {
        source.method_9213(class_2561.method_43470(body));
        return 1;
    }
}
