package dev.mispla.abyss;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.LightType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** Six buildings on an explicitly authored local shelf, study dimension only. */
public final class Town {
    public static final BlockPos ORIGIN=new BlockPos(-55,-63,-106);
    public static final List<GlimmeringAbyss.View> VIEWS=List.of(
        new GlimmeringAbyss.View("street",47.5,39,4.5,180,-9),
        new GlimmeringAbyss.View("plaza",63.5,39,4.5,112,-8),
        new GlimmeringAbyss.View("high",55.5,59,-60.5,27,18),
        new GlimmeringAbyss.View("cliff",20.5,39,-11.5,90,55),
        new GlimmeringAbyss.View("bridge",4.5,39,33.4,180,47),
        new GlimmeringAbyss.View("scale",-29.5,39,35.5,-55,-10),
        new GlimmeringAbyss.View("overview",-31.5,73,66.5,-145,24),
        Architecture.VIEWS.get(0),Architecture.VIEWS.get(4));
    private Town(){}
    private static ServerWorld world(ServerCommandSource s){
        var w=s.getServer().getWorld(GlimmeringAbyss.STUDY);if(w==null)throw new IllegalStateException("Study missing");return w;
    }
    private static StructureTemplate template(ServerWorld w){return w.getStructureTemplateManager().getTemplate(GlimmeringAbyss.id("town")).orElseThrow(()->new IllegalStateException("Town template missing"));}
    public static int place(ServerCommandSource s){
        try{
            var w=world(s);var t=template(w);
            if(!t.place(w,ORIGIN,ORIGIN,new StructurePlacementData().setIgnoreEntities(true).setUpdateNeighbors(true),w.random,Block.NOTIFY_LISTENERS|Block.FORCE_STATE))throw new IllegalStateException("Town placement failed");
            s.sendFeedback(()->Text.literal("[Abyss] Phase 6 town ready. /abyss town view street | high | cliff; /abyssrender blue | blue95 | blue95-fog. Explicit local terrain replacement: use a test save."),false);return 1;
        }catch(Exception e){e.printStackTrace();s.sendError(Text.literal(e.getMessage()));return 0;}
    }
    public static int view(ServerCommandSource s,String name)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var v=VIEWS.stream().filter(x->x.name().equals(name)).findFirst().orElse(null);
        if(v==null){s.sendError(Text.literal("street | plaza | high | cliff | bridge | scale | overview | facade | interior"));return 0;}
        var p=s.getPlayerOrThrow();if(name.equals("overview")&&!p.isCreative()){s.sendError(Text.literal("Overview requires Creative"));return 0;}
        p.getAbilities().flying=name.equals("overview");p.sendAbilitiesUpdate();p.teleport(world(s),v.x(),v.y(),v.z(),v.yaw(),v.pitch());return 1;
    }
    private static <T extends Comparable<T>> String value(BlockState s,Property<T> p){return p.name(s.get(p));}
    private static boolean clear(ServerWorld w,BlockPos p){
        var floor=w.getBlockState(p.down()).getCollisionShape(w,p.down());
        return !floor.isEmpty()&&floor.getMax(Direction.Axis.Y)==1.0&&w.getBlockState(p).getCollisionShape(w,p).isEmpty()&&w.getBlockState(p.up()).getCollisionShape(w,p.up()).isEmpty();
    }
    public static int audit(ServerCommandSource s,String label){
        try{
            if(!label.matches("[a-zA-Z0-9_-]{1,40}"))throw new IllegalArgumentException("label");
            var w=world(s);var n=template(w).writeNbt(new NbtCompound());var palette=n.getList("palette",NbtElement.COMPOUND_TYPE);var list=n.getList("blocks",NbtElement.COMPOUND_TYPE);
            int mismatch=0,propsMismatch=0,windows=0,lit=0;var digest=MessageDigest.getInstance("SHA-256");
            for(int i=0;i<list.size();i++){
                var e=list.getCompound(i);var xyz=e.getList("pos",NbtElement.INT_TYPE);var p=ORIGIN.add(xyz.getInt(0),xyz.getInt(1),xyz.getInt(2));
                var expected=palette.getCompound(e.getInt("state"));var actual=w.getBlockState(p);
                if(!expected.getString("Name").equals(Registries.BLOCK.getId(actual.getBlock()).toString()))mismatch++;
                var props=expected.getCompound("Properties");for(var prop:actual.getProperties())if(props.contains(prop.getName())&&!props.getString(prop.getName()).equals(value(actual,prop)))propsMismatch++;
                digest.update((p.toShortString()+"="+actual+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if(expected.getString("Name").equals("glimmeringabyss:warm_window")){windows++;if(actual.isOf(GlimmeringAbyss.WARM_WINDOW)&&w.getLightLevel(LightType.BLOCK,p)==12)lit++;}
            }
            int samples=0;boolean route=true;
            for(int z=5;z<=43;z++){route&=clear(w,new BlockPos(30,39,z));samples++;}
            for(int x=30;x<=47;x++){route&=clear(w,new BlockPos(x,39,1));samples++;}
            for(int z=-60;z<=4;z++){
                if(z==-26||z==-43){route&=w.getBlockState(new BlockPos(47,z==-26?39:40,z)).isOf(Blocks.DEEPSLATE_TILE_STAIRS);continue;}
                route&=clear(w,new BlockPos(47,39+(z<=-26?1:0)+(z<=-43?1:0),z));samples++;
            }
            for(int x=-29;x<=30;x++){route&=clear(w,new BlockPos(x,39,35));samples++;}
            boolean ladder=true;for(int y=41;y<60;y++)ladder&=w.getBlockState(new BlockPos(54,y,-58)).isOf(Blocks.LADDER);
            boolean drop=true;int dropSamples=0;
            for(int x:new int[]{0,4,20})for(int z:new int[]{-20,35})for(int y=-62;y<=36;y++){
                var p=new BlockPos(x,y,z);drop&=w.getBlockState(p).isAir();dropSamples++;
            }
            // Independently re-read the old shipped template's 52 accepted windows.
            var old=w.getStructureTemplateManager().getTemplate(GlimmeringAbyss.id("workshop")).orElseThrow().writeNbt(new NbtCompound());
            var op=old.getList("palette",NbtElement.COMPOUND_TYPE);var ob=old.getList("blocks",NbtElement.COMPOUND_TYPE);int preserved=0;
            for(int i=0;i<ob.size();i++){
                var e=ob.getCompound(i);if(!op.getCompound(e.getInt("state")).getString("Name").equals("glimmeringabyss:warm_window"))continue;
                var xyz=e.getList("pos",NbtElement.INT_TYPE);var p=Architecture.ORIGIN.add(xyz.getInt(0),xyz.getInt(1),xyz.getInt(2));
                if(w.getBlockState(p).isOf(GlimmeringAbyss.WARM_WINDOW)&&w.getLightLevel(LightType.BLOCK,p)==12)preserved++;
            }
            var r=new JsonObject();r.addProperty("label",label);r.addProperty("template_blocks_checked",list.size());r.addProperty("block_id_mismatches",mismatch);r.addProperty("explicit_property_mismatches",propsMismatch);
            r.addProperty("actual_structure_state_sha256",HexFormat.of().formatHex(digest.digest()));r.addProperty("warm_window_blocks",windows);r.addProperty("warm_windows_native_light_12",lit);r.addProperty("accepted_workshop_windows_preserved",preserved);
            r.addProperty("route_samples",samples);r.addProperty("route_collision_clearance_pass",route);r.addProperty("ladder_pass",ladder);r.addProperty("deep_air_samples",dropSamples);r.addProperty("open_depth_pass",drop);r.addProperty("finite_floor_y",-63);
            r.addProperty("template_entities",n.getList("entities",NbtElement.COMPOUND_TYPE).size());r.addProperty("buildings",6);r.addProperty("pass",mismatch==0&&propsMismatch==0&&windows==lit&&preserved==52&&route&&ladder&&drop);
            Path out=Path.of("abyss-results");Files.createDirectories(out);Files.writeString(out.resolve("town-"+label+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(r));
            s.sendFeedback(()->Text.literal("[Abyss] Town "+label+" / "+r),false);return r.get("pass").getAsBoolean()?1:0;
        }catch(Exception e){e.printStackTrace();s.sendError(Text.literal(e.getMessage()));return 0;}
    }
}
