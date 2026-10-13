package dev.mispla.abyss;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;

/** Explicit, isolated-runtime diagnostics; none of these commands is a survival travel mechanism. */
public final class Phase7Audit {
    private static MobEntity entering;
    private static long enteredAt;
    private Phase7Audit(){}
    public static void initialize(){
        CommandRegistrationCallback.EVENT.register((d,a,e)->d.register(literal("abyss").then(literal("phase7")
            .requires(s->s.hasPermissionLevel(2)&&Boolean.getBoolean("abyss.testServer"))
            .then(literal("status").executes(c->status(c.getSource())))
            .then(literal("audit").then(argument("label",StringArgumentType.word()).executes(c->audit(c.getSource(),StringArgumentType.getString(c,"label")))))
            .then(literal("view").then(argument("name",StringArgumentType.word()).executes(c->view(c.getSource(),StringArgumentType.getString(c,"name")))))
            .then(literal("intrusion").executes(c->intrusion(c.getSource())))
            .then(literal("natural").executes(c->natural(c.getSource())))
            .then(literal("bedtime").executes(c->bedtime(c.getSource())))
            .then(literal("dream").executes(c->{Journey.dream(c.getSource().getPlayerOrThrow());return 1;}))
        )));
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(entering!=null&&server.getOverworld().getTime()>enteredAt+5){
                var r=new JsonObject();r.addProperty("removed_after_crossing",entering.isRemoved());r.addProperty("ticks",server.getOverworld().getTime()-enteredAt);
                write("intrusion",r);if(!entering.isRemoved())entering.discard();entering=null;
            }
        });
    }
    static void write(String name,JsonObject r){try{Files.createDirectories(Path.of("abyss-results"));Files.writeString(Path.of("abyss-results/phase7-"+name+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(r));}catch(Exception e){throw new IllegalStateException(e);}}
    private static JsonObject state(ServerWorld w){var s=OverworldSite.get(w);var r=new JsonObject();r.addProperty("seed",w.getSeed());r.addProperty("stage",s.stage);r.addProperty("x",s.x);r.addProperty("z",s.z);r.addProperty("entry_y",s.entryY);r.addProperty("approach_length",s.approachLength);r.addProperty("approach_y",s.approachY);r.addProperty("attempt",s.attempt);r.addProperty("cursor",s.cursor);r.addProperty("reason",s.reason);return r;}
    private static int status(ServerCommandSource source){var r=state(source.getServer().getOverworld());write("status",r);source.sendFeedback(()->Text.literal(r.toString()),false);return 1;}
    private static int view(ServerCommandSource source,String name)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var w=source.getServer().getOverworld();var s=OverworldSite.get(w);if(!s.ready())return 0;
        var p=source.getPlayerOrThrow();p.changeGameMode(GameMode.SURVIVAL);p.getAbilities().flying=false;p.sendAbilitiesUpdate();
        double x=6.5,y=s.entryY+1,z=70.5;float yaw=180,pitch=8;
        switch(name){
            case "approach" -> {x=9.5+s.approachLength;y=s.approachY+1;z=70.5;yaw=90;pitch=0;}
            case "lower" -> y=45;
            case "street" -> {x=47.5;y=45;z=4.5;yaw=180;pitch=-9;}
            case "cliff" -> {x=20.06;y=45;z=-11.5;yaw=90;pitch=55;}
            case "overview" -> {x=-31.5;y=66;z=60.5;yaw=-135;pitch=18;p.changeGameMode(GameMode.CREATIVE);p.getAbilities().flying=true;p.sendAbilitiesUpdate();}
            case "surface" -> {x=17;y=s.entryY+7;z=85;yaw=140;pitch=20;p.changeGameMode(GameMode.CREATIVE);p.getAbilities().flying=true;p.sendAbilitiesUpdate();}
            case "outside" -> {x=400.5;z=70.5;y=OverworldLandscape.surface(w,Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,s.x+400,s.z+70)+1;w.setTimeOfDay(18000);p.changeGameMode(GameMode.CREATIVE);}
            case "entrance" -> {}
            default -> {return 0;}
        }
        p.teleport(w,s.x+x,y,s.z+z,yaw,pitch);p.setHealth(20);p.getHungerManager().setFoodLevel(20);return 1;
    }
    private static int bedtime(ServerCommandSource source)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var w=source.getServer().getOverworld();var s=OverworldSite.get(w);int x=s.x+400,z=s.z+70;
        int y=OverworldLandscape.surface(w,Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z);
        for(int dx=-2;dx<=3;dx++)for(int dz=-2;dz<=3;dz++){
            w.setBlockState(new BlockPos(x+dx,y-1,z+dz),Blocks.STONE.getDefaultState());
            for(int h=0;h<4;h++)w.setBlockState(new BlockPos(x+dx,y+h,z+dz),Blocks.AIR.getDefaultState());
        }
        var bed=Blocks.BLUE_BED.getDefaultState().with(net.minecraft.block.BedBlock.FACING,net.minecraft.util.math.Direction.SOUTH);
        w.setBlockState(new BlockPos(x,y,z),bed.with(net.minecraft.block.BedBlock.PART,net.minecraft.block.enums.BedPart.FOOT));
        w.setBlockState(new BlockPos(x,y,z+1),bed.with(net.minecraft.block.BedBlock.PART,net.minecraft.block.enums.BedPart.HEAD));
        w.setTimeOfDay((w.getTimeOfDay()/24000+1)*24000+13000);
        var player=source.getPlayerOrThrow();player.teleport(w,x+1.5,y,z+.5,90,40);return 1;
    }
    private static int intrusion(ServerCommandSource source){
        var w=source.getServer().getOverworld();var s=OverworldSite.get(w);
        entering=EntityType.ZOMBIE.create(w);entering.setPosition(s.x+115,45,s.z+70);w.spawnEntity(entering);
        entering.setPosition(s.x+6,45,s.z+70);enteredAt=w.getTime();return 1;
    }
    private static int natural(ServerCommandSource source){
        var w=source.getServer().getOverworld();int outside=0,inside=0;var r=new JsonObject();var a=new JsonArray();
        for(var e:w.iterateEntities())if(e instanceof MobEntity&&e.getType().getSpawnGroup()==SpawnGroup.MONSTER){
            if(SceneSafety.protects(w,e.getX(),e.getY(),e.getZ()))inside++;else{outside++;var row=new JsonObject();row.addProperty("type",EntityType.getId(e.getType()).toString());row.addProperty("x",e.getX());row.addProperty("y",e.getY());row.addProperty("z",e.getZ());a.add(row);}
        }
        r.addProperty("outside_loaded_monsters",outside);r.addProperty("inside_loaded_monsters",inside);r.add("outside_entities",a);r.addProperty("doMobSpawning",w.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING));r.addProperty("difficulty",w.getDifficulty().getName());write("natural",r);return 1;
    }
    private static int audit(ServerCommandSource source,String label){
        if(!label.matches("[a-zA-Z0-9_-]{1,32}"))return 0;
        try{
            var w=source.getServer().getOverworld();var s=OverworldSite.get(w);var r=state(w);if(!s.ready()){write(label,r);return 0;}
            var n=w.getStructureTemplateManager().getTemplate(GlimmeringAbyss.id("town")).orElseThrow().writeNbt(new NbtCompound());
            var pal=n.getList("palette",NbtElement.COMPOUND_TYPE);var blocks=n.getList("blocks",NbtElement.COMPOUND_TYPE);
            int windows=0,lit=0,preserved=0;var digest=MessageDigest.getInstance("SHA-256");
            for(int i=0;i<blocks.size();i++){
                var b=blocks.getCompound(i);var xyz=b.getList("pos",NbtElement.INT_TYPE);int x=xyz.getInt(0)-55,y=xyz.getInt(1)-63,z=xyz.getInt(2)-106;
                String expected=pal.getCompound(b.getInt("state")).getString("Name");
                if(expected.equals("glimmeringabyss:warm_window")){windows++;var p=s.at(x,y,z);if(w.getBlockState(p).isOf(GlimmeringAbyss.WARM_WINDOW))preserved++;if(w.getLightLevel(LightType.BLOCK,p)==12)lit++;}
            }
            int capMatches=0;for(int i=0;i<OverworldSite.CHUNKS;i++){String cap=OverworldLandscape.capHash(w,s,s.x/16-7+i%14,s.z/16-11+i/14);if(i<s.caps.size()&&s.caps.get(i).equals(cap))capMatches++;}
            r.addProperty("unchanged_surface_and_bedrock_chunks",capMatches);
            var surface=new JsonArray();
            for(int x=-104;x<112;x+=16)for(int z=-168;z<112;z+=16){
                int y=OverworldLandscape.surface(w,Heightmap.Type.OCEAN_FLOOR,s.x+x,s.z+z);
                var row=new JsonObject();row.addProperty("local_x",x);row.addProperty("local_z",z);row.addProperty("y",y);
                row.addProperty("top",w.getBlockState(new BlockPos(s.x+x,y-1,s.z+z)).toString());row.addProperty("second",w.getBlockState(new BlockPos(s.x+x,y-2,s.z+z)).toString());surface.add(row);
            }
            r.add("native_surface_samples",surface);
            int samples=0;for(int x=-108;x<108;x+=9)for(int z=-172;z<108;z+=9)for(int y=-63;y<=105;y+=7){
                if(y>OverworldLandscape.ceiling(x,z)-OverworldSite.OFFSET_Y)continue;
                digest.update(w.getBlockState(s.at(x,y,z)).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));samples++;
            }
            for(int step=0;step<=s.approachLength;step++){
                int y=s.entryY+(int)Math.round((s.approachY-s.entryY)*step/(double)s.approachLength);
                int next=step==s.approachLength?y:s.entryY+(int)Math.round((s.approachY-s.entryY)*(step+1)/(double)s.approachLength),base=y+(next>y?1:0);
                for(int z=68;z<=72;z++)for(int h=0;h<=4;h++){
                    digest.update(w.getBlockState(new BlockPos(s.x+9+step,base+h,s.z+z)).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));samples++;
                }
            }
            var attempts=new JsonArray();boolean blocked=true;
            for(int[] point:new int[][]{{47,45,4},{-40,26,-60},{4,45,35},{0,-54,-20},{0,85,70},{6,s.entryY+2,70},{9+s.approachLength/2,Math.max(s.entryY,s.approachY)+2,70},{-111,20,-175},{110,20,110}}){
                for(var type:List.of(EntityType.ZOMBIE,EntityType.SKELETON,EntityType.CREEPER,EntityType.PHANTOM,EntityType.BAT,EntityType.COW)){
                    var mob=type.create(w);mob.setPosition(s.x+point[0]+.5,point[1],s.z+point[2]+.5);boolean accepted=w.spawnEntity(mob);blocked&=!accepted;
                    var row=new JsonObject();row.addProperty("type",EntityType.getId(type).toString());row.addProperty("local_x",point[0]);row.addProperty("y",point[1]);row.addProperty("local_z",point[2]);row.addProperty("accepted",accepted);attempts.add(row);if(accepted)mob.discard();
                }
            }
            boolean outside=true;for(int[] q:new int[][]{{-114,20,-50},{114,20,-50},{0,20,-179},{0,20,114},{40,130,0},{0,-60,0}}){
                var mob=EntityType.ZOMBIE.create(w);mob.setPosition(s.x+q[0],q[1],s.z+q[2]);outside&=w.spawnEntity(mob)&&!mob.isRemoved();mob.discard();
            }
            int sceneMobs=0;for(var e:w.iterateEntities())if(e instanceof MobEntity&&SceneSafety.protects(w,e.getX(),e.getY(),e.getZ()))sceneMobs++;
            boolean bedrock=true;for(int x=-112;x<112;x+=16)for(int z=-176;z<112;z+=16)bedrock&=w.getBlockState(new BlockPos(s.x+x,-64,s.z+z)).isOf(Blocks.BEDROCK);
            boolean shaft=true;for(int y=45;y<=s.entryY+3;y++)for(int x=-1;x<=1;x++)for(int z=69;z<=71;z++)shaft&=w.getBlockState(new BlockPos(s.x+x,y,s.z+z)).getCollisionShape(w,new BlockPos(s.x+x,y,s.z+z)).isEmpty();
            r.addProperty("warm_windows",windows);r.addProperty("warm_windows_preserved",preserved);r.addProperty("warm_windows_native_12",lit);r.addProperty("bedrock_intact",bedrock);r.addProperty("shaft_clear",shaft);
            r.addProperty("geometry_sample_count",samples);r.addProperty("geometry_sha256",HexFormat.of().formatHex(digest.digest()));r.add("scene_spawn_attempts",attempts);r.addProperty("scene_insertions_blocked",blocked);r.addProperty("outside_boundary_insertions_allowed",outside);r.addProperty("loaded_scene_mobs",sceneMobs);
            r.addProperty("doMobSpawning",w.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING));r.addProperty("difficulty",w.getDifficulty().getName());r.addProperty("dark_native_light",w.getLightLevel(LightType.BLOCK,s.at(0,-54,-20)));
            r.addProperty("surface_exact_required",label.equals("first")||label.equals("second"));
            r.addProperty("pass",(!(label.equals("first")||label.equals("second"))||capMatches==OverworldSite.CHUNKS)&&windows==128&&preserved==128&&lit==128&&bedrock&&shaft&&blocked&&outside&&sceneMobs==0&&w.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING));write(label,r);return r.get("pass").getAsBoolean()?1:0;
        }catch(Exception e){e.printStackTrace();return 0;}
    }
}
