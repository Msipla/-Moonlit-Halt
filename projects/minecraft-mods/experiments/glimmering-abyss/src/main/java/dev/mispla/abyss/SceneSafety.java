package dev.mispla.abyss;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.LightType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Entire current landscape dimension, including its dark outer caves and abyss.
 * No global gamerule change. Vanilla aggressive phantoms are also excluded;
 * a future explicitly authored neutral scenic phantom is a separate decision. */
public final class SceneSafety {
    private SceneSafety(){}
    public static boolean protects(ServerWorld world){return world.getRegistryKey().equals(GlimmeringAbyss.STUDY);}
    public static boolean protects(ServerWorld world,double x,double y,double z){
        return protects(world)||world.getRegistryKey().equals(net.minecraft.world.World.OVERWORLD)&&OverworldSite.get(world).contains(x,y,z);
    }
    public static void initialize(){
        // Also covers old save entities, spawners and alternate insertion paths.
        ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{if(entity instanceof MobEntity&&protects(world,entity.getX(),entity.getY(),entity.getZ()))entity.discard();});
    }
    public static int probe(ServerCommandSource s,String label){
        try{
            if(!Boolean.getBoolean("abyss.testServer")||!label.matches("[a-zA-Z0-9_-]{1,40}"))throw new IllegalArgumentException("Isolated test server only");
            var w=s.getServer().getWorld(GlimmeringAbyss.STUDY);var overworld=s.getServer().getOverworld();
            boolean before=overworld.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING);int entries=0;
            // Generator uses one quiet_depths biome everywhere, not a town-only mask.
            for(var group:SpawnGroup.values())entries+=w.getBiome(new BlockPos(-45,-40,-60)).value().getSpawnSettings().getSpawnEntries(group).getEntries().size();
            var attempts=new JsonArray();boolean blocked=true;
            for(var type:List.of(EntityType.ZOMBIE,EntityType.SKELETON,EntityType.CREEPER,EntityType.SPIDER,EntityType.ENDERMAN,EntityType.PHANTOM,EntityType.BAT,EntityType.COW,EntityType.SQUID)){
                var e=type.create(w);e.setPosition(-40,20,-60);boolean accepted=w.spawnEntity(e);blocked&=!accepted;
                var row=new JsonObject();row.addProperty("type",EntityType.getId(type).toString());row.addProperty("spawn_accepted",accepted);attempts.add(row);if(accepted)e.discard();
            }
            var control=EntityType.ZOMBIE.create(overworld);control.setPosition(0,45,0);boolean allowed=overworld.spawnEntity(control);boolean retained=!control.isRemoved();control.discard();
            int mobs=0;for(var entity:w.iterateEntities())if(entity instanceof MobEntity)mobs++;
            boolean after=overworld.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING);
            var r=new JsonObject();r.addProperty("label",label);r.addProperty("difficulty",w.getDifficulty().getName());r.addProperty("entire_study_dimension_protected",protects(w));r.addProperty("overworld_protected",protects(overworld));r.addProperty("natural_biome_spawn_entries",entries);r.add("actual_spawn_attempts",attempts);
            r.addProperty("all_scene_mob_insertions_rejected",blocked);r.addProperty("loaded_scene_mobs",mobs);r.addProperty("overworld_zombie_spawn_accepted",allowed&&retained);r.addProperty("overworld_doMobSpawning_before",before);r.addProperty("overworld_doMobSpawning_after",after);r.addProperty("dark_abyss_native_block_light",w.getLightLevel(LightType.BLOCK,new BlockPos(0,-60,-20)));
            r.addProperty("pass",entries==0&&blocked&&mobs==0&&allowed&&retained&&before&&after&&!protects(overworld)&&w.getDifficulty()!=net.minecraft.world.Difficulty.PEACEFUL);
            Path out=Path.of("abyss-results");Files.createDirectories(out);Files.writeString(out.resolve("safety-"+label+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(r));s.sendFeedback(()->Text.literal("[Abyss] Safety "+r),false);return r.get("pass").getAsBoolean()?1:0;
        }catch(Exception e){e.printStackTrace();s.sendError(Text.literal(e.getMessage()));return 0;}
    }
}
