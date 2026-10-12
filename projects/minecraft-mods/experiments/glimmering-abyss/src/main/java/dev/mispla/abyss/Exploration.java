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

/** Explicit, local Phase 5 research placement. No generator or render changes. */
public final class Exploration {
    public static final BlockPos ORIGIN = new BlockPos(-32,-63,15);
    public static final List<GlimmeringAbyss.View> VIEWS = List.of(
        Architecture.VIEWS.get(0),
        new GlimmeringAbyss.View("core",4.5,39,36.5,-130,30),
        new GlimmeringAbyss.View("down",4.5,39,35.5,180,68),
        new GlimmeringAbyss.View("span",-7.5,57,62.5,180,25),
        new GlimmeringAbyss.View("departure",30.5,39,43.5,98,0),
        new GlimmeringAbyss.View("arrival",-29.5,39,35.5,-90,-8),
        Architecture.VIEWS.get(1), Architecture.VIEWS.get(4));
    private Exploration() {}
    private static ServerWorld world(ServerCommandSource s) {
        var w=s.getServer().getWorld(GlimmeringAbyss.STUDY);
        if(w==null)throw new IllegalStateException("Study dimension missing");return w;
    }
    private static StructureTemplate template(ServerWorld w) {
        return w.getStructureTemplateManager().getTemplate(GlimmeringAbyss.id("exploration"))
            .orElseThrow(()->new IllegalStateException("Packaged exploration template missing"));
    }
    public static int place(ServerCommandSource s) {
        try {
            var w=world(s);var t=template(w);
            var data=new StructurePlacementData().setIgnoreEntities(true).setUpdateNeighbors(true);
            if(!t.place(w,ORIGIN,ORIGIN,data,w.random,Block.NOTIFY_LISTENERS|Block.FORCE_STATE))throw new IllegalStateException("Exploration placement failed");
            s.sendFeedback(()->Text.literal("[Abyss] Phase 5 route ready. /abyss route view departure | core | arrival; /abyssrender blue"),false);return 1;
        }catch(Exception e){e.printStackTrace();s.sendError(Text.literal(e.getMessage()));return 0;}
    }
    public static int view(ServerCommandSource s,String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var v=VIEWS.stream().filter(x->x.name().equals(name)).findFirst().orElse(null);
        if(v==null){s.sendError(Text.literal("facade | core | down | span | departure | arrival | warmth | interior"));return 0;}
        var p=s.getPlayerOrThrow();if(name.equals("span")&&!p.isCreative()){s.sendError(Text.literal("Span view requires Creative mode"));return 0;}
        p.getAbilities().flying=name.equals("span");p.sendAbilitiesUpdate();p.teleport(world(s),v.x(),v.y(),v.z(),v.yaw(),v.pitch());return 1;
    }
    private static <T extends Comparable<T>> String value(BlockState s,Property<T> p){return p.name(s.get(p));}
    private static boolean clear(ServerWorld w,BlockPos p) {
        var floor=w.getBlockState(p.down()).getCollisionShape(w,p.down());
        return !floor.isEmpty()&&floor.getMax(Direction.Axis.Y)==1.0
            &&w.getBlockState(p).getCollisionShape(w,p).isEmpty()&&w.getBlockState(p.up()).getCollisionShape(w,p.up()).isEmpty();
    }
    public static int audit(ServerCommandSource s,String label) {
        try {
            if(!label.matches("[a-zA-Z0-9_-]{1,40}"))throw new IllegalArgumentException("Invalid label");
            var w=world(s);var n=template(w).writeNbt(new NbtCompound());var palette=n.getList("palette",NbtElement.COMPOUND_TYPE);var list=n.getList("blocks",NbtElement.COMPOUND_TYPE);
            int mismatch=0,stateMismatch=0,windows=0,lit=0;var digest=MessageDigest.getInstance("SHA-256");
            for(int i=0;i<list.size();i++) {
                var e=list.getCompound(i);var xyz=e.getList("pos",NbtElement.INT_TYPE);var p=ORIGIN.add(xyz.getInt(0),xyz.getInt(1),xyz.getInt(2));
                var expected=palette.getCompound(e.getInt("state"));var actual=w.getBlockState(p);String id=Registries.BLOCK.getId(actual.getBlock()).toString();
                if(!expected.getString("Name").equals(id))mismatch++;
                var props=expected.getCompound("Properties");
                for(var prop:actual.getProperties())if(props.contains(prop.getName())&&!props.getString(prop.getName()).equals(value(actual,prop)))stateMismatch++;
                digest.update((p.toShortString()+"="+actual+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if(expected.getString("Name").equals("glimmeringabyss:warm_window")){windows++;if(actual.isOf(GlimmeringAbyss.WARM_WINDOW)&&w.getLightLevel(LightType.BLOCK,p)==12)lit++;}
            }
            boolean route=true,rails=true,shaft=true;int samples=0,railChecks=0,voidChecks=0;
            for(int z=35;z<=43;z++){route&=clear(w,new BlockPos(42,39,z));samples++;}
            for(int x=30;x<=42;x++){route&=clear(w,new BlockPos(x,39,43));samples++;}
            for(int z=35;z<=43;z++){route&=clear(w,new BlockPos(30,39,z));samples++;}
            for(int x=-29;x<=30;x++)for(int z=34;z<=36;z++){route&=clear(w,new BlockPos(x,39,z));samples++;}
            for(int z=30;z<=35;z++){route&=clear(w,new BlockPos(-29,39,z));samples++;}
            for(int x=-24;x<=28;x++)for(int z:new int[]{32,38})for(int y:new int[]{39,40}){
                var p=new BlockPos(x,y,z);rails&=w.getBlockState(p).isOf(Blocks.IRON_BARS)&&!w.getBlockState(p).getCollisionShape(w,p).isEmpty();railChecks++;
            }
            // Check actual unobstructed 100-block drops below the center of span.
            for(int x=-14;x<=20;x+=2)for(int y=-62;y<=37;y++){
                var p=new BlockPos(x,y,35);shaft&=w.getBlockState(p).isAir();voidChecks++;
            }
            for(int x=-14;x<=20;x+=2)shaft&=w.getBlockState(new BlockPos(x,-63,35)).isOf(Blocks.BLACK_CONCRETE);
            var r=new JsonObject();r.addProperty("label",label);r.addProperty("template_blocks_checked",list.size());r.addProperty("block_id_mismatches",mismatch);r.addProperty("explicit_property_mismatches",stateMismatch);
            r.addProperty("actual_structure_state_sha256",HexFormat.of().formatHex(digest.digest()));r.addProperty("warm_window_blocks",windows);r.addProperty("warm_windows_native_light_12",lit);
            r.addProperty("route_samples",samples);r.addProperty("connected_route_collision_clearance_pass",route);r.addProperty("iron_rail_samples",railChecks);r.addProperty("iron_rail_collision_pass",rails);
            r.addProperty("vertical_air_samples",voidChecks);r.addProperty("finite_vertical_shaft_pass",shaft);r.addProperty("shaft_floor_y",-63);r.addProperty("walk_surface_y",39);
            r.addProperty("template_entities",n.getList("entities",NbtElement.COMPOUND_TYPE).size());r.addProperty("pass",mismatch==0&&stateMismatch==0&&windows>0&&windows==lit&&route&&rails&&shaft);
            Path out=Path.of("abyss-results");Files.createDirectories(out);Files.writeString(out.resolve("exploration-"+label+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(r));
            s.sendFeedback(()->Text.literal("[Abyss] Exploration "+label+" / "+r),false);return r.get("pass").getAsBoolean()?1:0;
        }catch(Exception e){e.printStackTrace();s.sendError(Text.literal(e.getMessage()));return 0;}
    }
}
