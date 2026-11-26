package lab.test;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.class_2170;
import net.minecraft.class_2338;
import net.minecraft.class_2561;
import net.minecraft.class_7923;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.eureka.ship.EurekaShipControl;
import org.valkyrienskies.mod.common.IShipObjectWorldServerProvider;

/** Console-only recorder, built separately from the installable engineering JAR. */
public final class MobilePoseProbe implements ModInitializer {
    @Override public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((commands,access,environment)->
            commands.register(class_2170.method_9247("engineering_test_ship_pose")
                .requires(source->source.method_9228()==null)
                .executes(ctx->{
                    var source=ctx.getSource();
                    var world=source.method_9225();
                    for(var ship:((IShipObjectWorldServerProvider)world).getShipObjectWorld().getLoadedShips()) {
                        var box=ship.getShipAABB();
                        if(box==null || (box.maxX()-box.minX()+1L)*(box.maxY()-box.minY()+1)
                            *(box.maxZ()-box.minZ()+1)>8192) continue;
                        var transform=ship.getTransform();
                        var cells=new ArrayList<Object>();
                        for(var at:class_2338.method_10094(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ())) {
                            var state=world.method_8320(at);
                            if(state.method_26215()) continue;
                            int x=at.method_10263(), y=at.method_10264(), z=at.method_10260();
                            var feet=transform.getShipToWorld().transformPosition(new Vector3d(x+.5,y+1,z+.5));
                            cells.add(Map.of("pos",List.of(x,y,z),"feet",xyz(feet),
                                "block",class_7923.field_41175.method_10221(state.method_26204()).toString()));
                        }
                        var player=source.method_9211().method_3760().method_14566("LabOwner");
                        var eureka=ship.getAttachment(EurekaShipControl.class);
                        var data=new LinkedHashMap<String,Object>();
                        data.put("id",Long.toString(ship.getId()));
                        data.put("stationary",ship.isStatic());
                        data.put("cruising",eureka!=null && eureka.isCruising());
                        data.put("position",xyz(transform.getPositionInWorld()));
                        data.put("velocity",xyz(ship.getVelocity()));
                        data.put("blocks",cells);
                        if(player!=null) {
                            var p=player.method_19538();
                            var global=new Vector3d(p.field_1352,p.field_1351,p.field_1350);
                            data.put("playerWorld",xyz(global));
                            data.put("playerLocal",xyz(transform.getWorldToShip().transformPosition(global)));
                            data.put("mounted",player.method_5765());
                            data.put("creative",player.method_7337());
                        }
                        source.method_9226(()->class_2561.method_43470("LAB_SHIP_POSE "+new Gson().toJson(data)),false);
                        return 1;
                    }
                    return 0;
                })));
    }
    private static List<Double> xyz(Vector3dc v) { return List.of(v.x(),v.y(),v.z()); }
}
