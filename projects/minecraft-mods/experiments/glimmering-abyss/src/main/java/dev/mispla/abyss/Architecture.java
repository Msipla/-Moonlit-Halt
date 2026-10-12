package dev.mispla.abyss;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** One replaceable original structure in the study only; never modifies the generator. */
public final class Architecture {
    public static final BlockPos ORIGIN = new BlockPos(22,-34,15);
    public static final List<GlimmeringAbyss.View> VIEWS = List.of(
        new GlimmeringAbyss.View("facade",42.5,39,57.5,180,-18),
        new GlimmeringAbyss.View("warmth",30.5,39,45.5,-145,-18),
        new GlimmeringAbyss.View("scale",-11.5,57,67.5,-145,5),
        new GlimmeringAbyss.View("bridge",27.5,39,70.5,-90,0),
        new GlimmeringAbyss.View("interior",42.5,39,35.5,180,0));
    private Architecture() {}
    private static ServerWorld world(ServerCommandSource s) {
        var w=s.getServer().getWorld(GlimmeringAbyss.STUDY);
        if(w==null)throw new IllegalStateException("Study dimension missing");return w;
    }
    private static StructureTemplate template(ServerWorld w) {
        return w.getStructureTemplateManager().getTemplate(GlimmeringAbyss.id("workshop"))
            .orElseThrow(()->new IllegalStateException("Packaged workshop template missing"));
    }
    public static int place(ServerCommandSource source) {
        try {
            var w=world(source);var t=template(w);
            // Explicit invocation also upgrades an old study save; deterministic replacement, no RNG entities.
            var data=new StructurePlacementData().setIgnoreEntities(true).setUpdateNeighbors(true);
            if(!t.place(w,ORIGIN,ORIGIN,data,w.random,Block.NOTIFY_LISTENERS|Block.FORCE_STATE))throw new IllegalStateException("Template placement failed");
            source.sendFeedback(()->Text.literal("[Abyss] Workshop ready. /abyss building view facade | /abyssrender blue"),false);
            return 1;
        } catch(Exception e){e.printStackTrace();source.sendError(Text.literal(e.getMessage()));return 0;}
    }
    public static int view(ServerCommandSource s,String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var v=VIEWS.stream().filter(x->x.name().equals(name)).findFirst().orElse(null);
        if(v==null){s.sendError(Text.literal("facade | warmth | scale | bridge | interior"));return 0;}
        var p=s.getPlayerOrThrow();if(name.equals("scale")&&!p.isCreative()){s.sendError(Text.literal("Scale view requires Creative mode"));return 0;}
        p.getAbilities().flying=name.equals("scale");p.sendAbilitiesUpdate();p.teleport(world(s),v.x(),v.y(),v.z(),v.yaw(),v.pitch());return 1;
    }
    public static int audit(ServerCommandSource s,String label) {
        try {
            if(!label.matches("[a-zA-Z0-9_-]{1,40}"))throw new IllegalArgumentException("Invalid label");
            var w=world(s);var n=template(w).writeNbt(new NbtCompound());var palette=n.getList("palette",NbtElement.COMPOUND_TYPE);var list=n.getList("blocks",NbtElement.COMPOUND_TYPE);
            int mismatch=0,windows=0,lit=0,airMismatch=0;var digest=MessageDigest.getInstance("SHA-256");
            for(int i=0;i<list.size();i++) {
                var entry=list.getCompound(i);var xyz=entry.getList("pos",NbtElement.INT_TYPE);
                var pos=ORIGIN.add(xyz.getInt(0),xyz.getInt(1),xyz.getInt(2));String expected=palette.getCompound(entry.getInt("state")).getString("Name");
                var actual=w.getBlockState(pos);String id=Registries.BLOCK.getId(actual.getBlock()).toString();
                if(!expected.equals(id)){mismatch++;if(expected.equals("minecraft:air"))airMismatch++;}
                digest.update((pos.toShortString()+"="+id+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                if(expected.equals("glimmeringabyss:warm_window")){windows++;if(actual.isOf(GlimmeringAbyss.WARM_WINDOW)&&w.getLightLevel(LightType.BLOCK,pos)==12)lit++;}
            }
            boolean route=true;int samples=0;
            for(int x=22;x<=38;x++){var pos=new BlockPos(x,39,70);route &= clear(w,pos);samples++;}
            for(int z=70;z>=41;z--){var pos=new BlockPos(38,39,z);route &= clear(w,pos);samples++;}
            for(int x=38;x<=42;x++){var pos=new BlockPos(x,39,42);route &= clear(w,pos);samples++;}
            for(int z=42;z>=34;z--){var pos=new BlockPos(42,39,z);route &= clear(w,pos);samples++;}
            var result=new JsonObject();result.addProperty("label",label);result.addProperty("dimension",w.getRegistryKey().getValue().toString());
            result.addProperty("template_blocks_checked",list.size());result.addProperty("block_id_mismatches",mismatch);result.addProperty("air_clearance_mismatches",airMismatch);
            result.addProperty("actual_structure_block_id_sha256",HexFormat.of().formatHex(digest.digest()));result.addProperty("warm_window_blocks",windows);result.addProperty("warm_windows_native_light_12",lit);
            result.addProperty("route_samples",samples);result.addProperty("connected_route_floor_clearance_pass",route);result.addProperty("template_entities",n.getList("entities",NbtElement.COMPOUND_TYPE).size());
            result.addProperty("pass",mismatch==0&&windows>0&&windows==lit&&route);
            Path out=Path.of("abyss-results");Files.createDirectories(out);Files.writeString(out.resolve("architecture-"+label+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(result));
            s.sendFeedback(()->Text.literal("[Abyss] Architecture "+label+" / "+result),false);return result.get("pass").getAsBoolean()?1:0;
        }catch(Exception e){e.printStackTrace();s.sendError(Text.literal(e.getMessage()));return 0;}
    }
    private static boolean clear(ServerWorld w,BlockPos pos){return w.getBlockState(pos.down()).isSolidBlock(w,pos.down())&&w.getBlockState(pos).isAir()&&w.getBlockState(pos.up()).isAir();}
}
