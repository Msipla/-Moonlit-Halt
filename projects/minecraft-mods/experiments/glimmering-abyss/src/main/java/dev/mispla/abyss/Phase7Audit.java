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
    private static final java.util.Map<java.util.UUID,Integer> sleepStarts=new java.util.HashMap<>(),sleepStops=new java.util.HashMap<>();
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
            .then(literal("sleepstate").then(argument("label",StringArgumentType.word()).executes(c->sleepstate(c.getSource(),StringArgumentType.getString(c,"label")))))
            .then(literal("debugcheck").executes(c->debugcheck(c.getSource())))
            .then(literal("dream").executes(c->{Journey.dream(c.getSource().getPlayerOrThrow());return 1;}))
        )));
        if(Boolean.getBoolean("abyss.testServer")){
            net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents.START_SLEEPING.register((entity,pos)->{if(entity instanceof net.minecraft.server.network.ServerPlayerEntity player)sleepStarts.merge(player.getUuid(),1,Integer::sum);});
            net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents.STOP_SLEEPING.register((entity,pos)->{if(entity instanceof net.minecraft.server.network.ServerPlayerEntity player)sleepStops.merge(player.getUuid(),1,Integer::sum);});
        }
        ServerTickEvents.END_SERVER_TICK.register(server->{
            if(entering!=null&&server.getOverworld().getTime()>enteredAt+5){
                var r=new JsonObject();r.addProperty("removed_after_crossing",entering.isRemoved());r.addProperty("ticks",server.getOverworld().getTime()-enteredAt);
                write("intrusion",r);if(!entering.isRemoved())entering.discard();entering=null;
            }
        });
    }
    private static int debugcheck(ServerCommandSource source)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var p=source.getPlayerOrThrow();var old=p.interactionManager.getGameMode();boolean flying=p.getAbilities().flying;
        boolean denied=false,allowed=false;var dispatcher=source.getServer().getCommandManager().getDispatcher();
        try{
            p.changeGameMode(GameMode.SURVIVAL);
            try{dispatcher.execute("abyss debug status",p.getCommandSource());}catch(com.mojang.brigadier.exceptions.CommandSyntaxException ex){denied=true;}
            p.changeGameMode(GameMode.CREATIVE);allowed=dispatcher.execute("abyss debug status",p.getCommandSource())==1;
        }finally{p.changeGameMode(old);p.getAbilities().flying=flying;p.sendAbilitiesUpdate();}
        var r=new JsonObject();r.addProperty("survival_player_denied",denied);r.addProperty("creative_player_allowed",allowed);r.addProperty("pass",denied&&allowed);write("debug-permissions",r);return denied&&allowed?1:0;
    }
    static void write(String name,JsonObject r){try{Files.createDirectories(Path.of("abyss-results"));Files.writeString(Path.of("abyss-results/phase7-"+name+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(r));}catch(Exception e){throw new IllegalStateException(e);}}
    private static JsonObject state(ServerWorld w){var s=OverworldSite.get(w);var r=new JsonObject();r.addProperty("layout",s.layout);r.addProperty("seed",w.getSeed());r.addProperty("stage",s.stage);r.addProperty("x",s.x);r.addProperty("z",s.z);r.addProperty("entry_y",s.entryY);r.addProperty("approach_length",s.approachLength);r.addProperty("approach_y",s.approachY);r.addProperty("attempt",s.attempt);r.addProperty("cursor",s.cursor);r.addProperty("reason",s.reason);r.addProperty("unfinished_rides",s.journeys.size());return r;}
    private static int status(ServerCommandSource source){var r=state(source.getServer().getOverworld());write("status",r);source.sendFeedback(()->Text.literal(r.toString()),false);return 1;}
    private static int view(ServerCommandSource source,String name)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        var w=source.getServer().getOverworld();var s=OverworldSite.get(w);if(!s.ready())return 0;
        var p=source.getPlayerOrThrow();p.changeGameMode(GameMode.SURVIVAL);p.getAbilities().flying=false;p.sendAbilitiesUpdate();
        double x=6.5,y=s.entryY+1,z=70.5;float yaw=180,pitch=8;
        switch(name){
            case "rift" -> {x=-45.5;y=55;z=-134.5;yaw=165;pitch=12;}
            case "gallery" -> {x=-145.5;y=35;z=-205.5;yaw=-20;pitch=8;}
            case "homeward" -> {x=-66.5;y=34;z=-125.5;yaw=-30;pitch=-3;}
            case "route-start" -> {x=47.5;y=47;z=-57.5;yaw=180;pitch=0;}
            case "library" -> {x=-47.5;y=45;z=38.5;yaw=120;pitch=8;}
            case "bridge-destination" -> {x=-29.5;y=45;z=35.5;yaw=90;pitch=-5;}
            case "bedroom" -> {x=38.5;y=51;z=-10.5;yaw=180;pitch=10;}
            case "water" -> {x=76.5;y=46;z=19.5;yaw=180;pitch=25;}
            case "west-cave" -> {x=-83.5;y=36;z=-52.5;yaw=-35;pitch=8;p.changeGameMode(GameMode.CREATIVE);p.getAbilities().flying=true;p.sendAbilitiesUpdate();}
            case "floor" -> {x=-4.5;y=0;z=42.5;yaw=180;pitch=45;p.changeGameMode(GameMode.CREATIVE);p.getAbilities().flying=true;p.sendAbilitiesUpdate();}
            case "approach" -> {x=9.5+s.approachLength;y=s.approachY+1;z=70.5;yaw=90;pitch=0;}
            case "lower" -> {y=45;x=s.dockX()+6.5;z=s.dockZ()+.5;}
            case "street" -> {x=47.5;y=45;z=4.5;yaw=180;pitch=-9;}
            case "cliff" -> {x=-31.5;y=26;z=7.5;yaw=-90;pitch=8;p.changeGameMode(GameMode.CREATIVE);p.getAbilities().flying=true;p.sendAbilitiesUpdate();}
            case "overview" -> {x=-31.5;y=66;z=60.5;yaw=-135;pitch=18;p.changeGameMode(GameMode.CREATIVE);p.getAbilities().flying=true;p.sendAbilitiesUpdate();}
            case "surface" -> {x=17;y=s.entryY+7;z=85;yaw=140;pitch=20;p.changeGameMode(GameMode.CREATIVE);p.getAbilities().flying=true;p.sendAbilitiesUpdate();}
            case "outside" -> {x=400.5;z=70.5;y=OverworldLandscape.surface(w,Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,s.x+400,s.z+70)+1;w.setTimeOfDay(18000);p.changeGameMode(GameMode.CREATIVE);}
            case "entrance" -> {}
            default -> {return 0;}
        }
        if(java.util.Set.of("rift","gallery","homeward","library","bridge-destination","bedroom","water","west-cave","floor","cliff","overview","surface").contains(name)){
            p.changeGameMode(GameMode.CREATIVE);p.getAbilities().flying=true;p.sendAbilitiesUpdate();
            boolean clear=false;
            for(int radius=0;radius<=12&&!clear;radius++)for(int dx=-radius;dx<=radius&&!clear;dx++)for(int dz=-radius;dz<=radius&&!clear;dz++){
                if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
                if(w.isSpaceEmpty(p,new net.minecraft.util.math.Box(s.x+x+dx-.3,y,s.z+z+dz-.3,s.x+x+dx+.3,y+1.8,s.z+z+dz+.3))){x+=dx;z+=dz;clear=true;}
            }
            if(!clear)throw new IllegalStateException("No clear photography position for "+name);
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
        long beforeDayTime=w.getTimeOfDay(),beforeWorldTime=w.getTime();
        var dispatcher=source.getServer().getCommandManager().getDispatcher();
        dispatcher.execute("time add 24000",source);
        long advancedDay=w.getTimeOfDay()/24000;
        dispatcher.execute("time set "+(advancedDay*24000+13000),source);
        if(w.getTimeOfDay()/24000<=beforeDayTime/24000)throw new IllegalStateException("Native time command did not advance the day");
        var player=source.getPlayerOrThrow();player.changeGameMode(GameMode.SURVIVAL);player.getAbilities().flying=false;player.sendAbilitiesUpdate();player.teleport(w,x+1.5,y,z+.5,90,40);
        var fixture=new JsonObject();fixture.addProperty("bed_x",x);fixture.addProperty("bed_y",y);fixture.addProperty("bed_z",z);fixture.addProperty("player",player.getPos().toString());fixture.addProperty("time_of_day",w.getTimeOfDay());fixture.addProperty("before_day_time",beforeDayTime);fixture.addProperty("before_world_time",beforeWorldTime);fixture.addProperty("world_time",w.getTime());fixture.addProperty("day_before",beforeDayTime/24000);fixture.addProperty("day_after",w.getTimeOfDay()/24000);fixture.addProperty("command","time add 24000; time set absolute-next-day-night");write("sleep-fixture-"+(s.dreams.getOrDefault(player.getUuid(),0)+1),fixture);write("sleep-fixture",fixture);return 1;
    }
    private static int sleepstate(ServerCommandSource source,String label)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        if(!label.matches("[a-zA-Z0-9_-]{1,32}"))return 0;
        var player=source.getPlayerOrThrow();var w=player.getServerWorld();var site=OverworldSite.get(source.getServer().getOverworld());var r=new JsonObject();
        r.addProperty("world_time",w.getTime());r.addProperty("day_time",w.getTimeOfDay());r.addProperty("day",w.getTimeOfDay()/24000);r.addProperty("sleeping",player.isSleeping());
        r.addProperty("start_sleep_events",sleepStarts.getOrDefault(player.getUuid(),0));r.addProperty("stop_sleep_events",sleepStops.getOrDefault(player.getUuid(),0));
        r.addProperty("dreams",site.dreams.getOrDefault(player.getUuid(),0));r.addProperty("dream_day",site.dreamDays.getOrDefault(player.getUuid(),-1L));
        r.addProperty("has_echo",player.getInventory().contains(new net.minecraft.item.ItemStack(Journey.ECHO)));r.addProperty("game_mode",player.interactionManager.getGameMode().getName());
        write("sleep-"+label,r);return 1;
    }
    private static int intrusion(ServerCommandSource source){
        var w=source.getServer().getOverworld();var s=OverworldSite.get(w);
        entering=EntityType.ZOMBIE.create(w);entering.setPosition(s.x+s.maxX()+4,45,s.z+70);w.spawnEntity(entering);
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
            int capMatches=0;for(int i=0;i<s.chunks();i++){String cap=OverworldLandscape.capHash(w,s,s.chunkX(i),s.chunkZ(i));if(i<s.caps.size()&&s.caps.get(i).equals(cap))capMatches++;}
            r.addProperty("unchanged_surface_and_bedrock_chunks",capMatches);
            var surface=new JsonArray();
            for(int x=-104;x<112;x+=16)for(int z=-168;z<112;z+=16){
                int y=OverworldLandscape.surface(w,Heightmap.Type.OCEAN_FLOOR,s.x+x,s.z+z);
                var row=new JsonObject();row.addProperty("local_x",x);row.addProperty("local_z",z);row.addProperty("y",y);
                row.addProperty("top",w.getBlockState(new BlockPos(s.x+x,y-1,s.z+z)).toString());row.addProperty("second",w.getBlockState(new BlockPos(s.x+x,y-2,s.z+z)).toString());surface.add(row);
            }
            r.add("native_surface_samples",surface);
            int samples=0;for(int x=s.layout>=100?s.minX()+4:-108;x<(s.layout>=100?s.maxX()-4:108);x+=9)for(int z=s.layout>=100?s.minZ()+4:-172;z<(s.layout>=100?s.maxZ()-4:108);z+=9)for(int y=-63;y<=105;y+=7){
                if(y>OverworldLandscape.ceiling(s,x,z)-OverworldSite.OFFSET_Y)continue;
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
            for(int[] point:new int[][]{{47,45,4},{-40,26,-60},{4,45,35},{0,-54,-20},{0,85,70},{6,s.entryY+2,70},{9+s.approachLength/2,Math.max(s.entryY,s.approachY)+2,70},{s.minX()+1,20,s.minZ()+1},{s.maxX()-1,20,s.maxZ()-1}}){
                for(var type:List.of(EntityType.ZOMBIE,EntityType.SKELETON,EntityType.CREEPER,EntityType.PHANTOM,EntityType.BAT,EntityType.COW)){
                    var mob=type.create(w);mob.setPosition(s.x+point[0]+.5,point[1],s.z+point[2]+.5);boolean accepted=w.spawnEntity(mob);blocked&=!accepted;
                    var row=new JsonObject();row.addProperty("type",EntityType.getId(type).toString());row.addProperty("local_x",point[0]);row.addProperty("y",point[1]);row.addProperty("local_z",point[2]);row.addProperty("accepted",accepted);attempts.add(row);if(accepted)mob.discard();
                }
            }
            boolean outside=true;for(int[] q:new int[][]{{s.minX()-2,20,-50},{s.maxX()+2,20,-50},{0,20,s.minZ()-3},{0,20,s.maxZ()+3},{40,130,0},{0,-60,0}}){
                var mob=EntityType.ZOMBIE.create(w);mob.setPosition(s.x+q[0],q[1],s.z+q[2]);outside&=w.spawnEntity(mob)&&!mob.isRemoved();mob.discard();
            }
            int sceneMobs=0;for(var e:w.iterateEntities())if(e instanceof MobEntity&&SceneSafety.protects(w,e.getX(),e.getY(),e.getZ()))sceneMobs++;
            boolean bedrock=true;for(int x=s.minX();x<=s.maxX();x+=16)for(int z=s.minZ();z<=s.maxZ();z+=16)bedrock&=w.getBlockState(new BlockPos(s.x+x,-64,s.z+z)).isOf(Blocks.BEDROCK);
            boolean shaft=true;for(int y=45;y<=s.entryY+3;y++)for(int x=-1;x<=1;x++)for(int z=69;z<=71;z++)shaft&=w.getBlockState(new BlockPos(s.x+x,y,s.z+z)).getCollisionShape(w,new BlockPos(s.x+x,y,s.z+z)).isEmpty();
            r.addProperty("warm_windows",windows);r.addProperty("warm_windows_preserved",preserved);r.addProperty("warm_windows_native_12",lit);r.addProperty("bedrock_intact",bedrock);r.addProperty("shaft_clear",shaft);
            r.addProperty("geometry_sample_count",samples);r.addProperty("geometry_sha256",HexFormat.of().formatHex(digest.digest()));r.add("scene_spawn_attempts",attempts);r.addProperty("scene_insertions_blocked",blocked);r.addProperty("outside_boundary_insertions_allowed",outside);r.addProperty("loaded_scene_mobs",sceneMobs);
            r.addProperty("doMobSpawning",w.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING));r.addProperty("difficulty",w.getDifficulty().getName());r.addProperty("dark_native_light",w.getLightLevel(LightType.BLOCK,s.at(0,-54,-20)));
            r.addProperty("surface_exact_required",label.equals("first")||label.equals("second"));
            boolean fluids=true;int water=0;
            if(s.layout>=75)for(int x=74;x<=78;x++)for(int z=12;z<=16;z++){
                var pos=s.at(x,38,z);if(w.getBlockState(pos).isOf(Blocks.WATER))water++;
                fluids&=w.getBlockState(pos.down()).isSolidBlock(w,pos.down());
                for(var dir:List.of(net.minecraft.util.math.Direction.NORTH,net.minecraft.util.math.Direction.SOUTH,net.minecraft.util.math.Direction.EAST,net.minecraft.util.math.Direction.WEST)){
                    var q=pos.offset(dir);fluids&=w.getBlockState(q).isOf(Blocks.WATER)||w.getBlockState(q).isSolidBlock(w,q);
                }
            }
            r.addProperty("water_source_cells",water);r.addProperty("water_basin_contained",fluids);
            int structures=0,missing=0;
            if(s.layout>=75){
                var tn=w.getStructureTemplateManager().getTemplate(GlimmeringAbyss.id(s.layout>=100?"abyss_town":"natural_town")).orElseThrow().writeNbt(new NbtCompound());
                var tp=tn.getList("palette",NbtElement.COMPOUND_TYPE);var tb=tn.getList("blocks",NbtElement.COMPOUND_TYPE);
                for(int i=0;i<tb.size();i++){
                    var b=tb.getCompound(i);String id=tp.getCompound(b.getInt("state")).getString("Name");if(id.equals("minecraft:air"))continue;
                    var xyz=b.getList("pos",NbtElement.INT_TYPE);var pos=s.at(xyz.getInt(0)-(s.layout>=100?180:64),xyz.getInt(1)+(s.layout>=100?-8:34),xyz.getInt(2)-(s.layout>=100?290:106));
                    if(s.layout>=100&&pos.getX()>=s.x+18&&pos.getX()<=s.x+33&&pos.getZ()>=s.z+52&&pos.getZ()<=s.z+65&&pos.getY()>=45&&pos.getY()<=52)continue;
                    structures++;if(!net.minecraft.registry.Registries.BLOCK.getId(w.getBlockState(pos).getBlock()).toString().equals(id))missing++;
                }
            }
            r.addProperty("architectural_nonair_blocks",structures);r.addProperty("architectural_missing_or_wrong_blocks",missing);
            r.addProperty("player_witness_gold",w.getBlockState(s.at(45,42,8)).isOf(Blocks.GOLD_BLOCK));
            boolean natural=s.layout<75||water==25&&fluids&&missing==0;
            r.addProperty("pass",natural&&(!(label.equals("first")||label.equals("second"))||capMatches==s.chunks())&&windows==128&&preserved==128&&lit==128&&bedrock&&shaft&&blocked&&outside&&sceneMobs==0&&w.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING));write(label,r);return r.get("pass").getAsBoolean()?1:0;
        }catch(Exception e){e.printStackTrace();return 0;}
    }
}
