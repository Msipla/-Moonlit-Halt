package dev.mispla.abyss;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Two quiet dreams, a reusable direction shard and two station bells. */
public final class Journey {
    public static final Identifier SITE_PACKET=GlimmeringAbyss.id("site");
    public static final EntityType<CableCarEntity> CAR=Registry.register(Registries.ENTITY_TYPE,GlimmeringAbyss.id("cable_car"),
        FabricEntityTypeBuilder.<CableCarEntity>create(SpawnGroup.MISC,CableCarEntity::new).dimensions(EntityDimensions.fixed(3,3.3f)).trackRangeBlocks(160).trackedUpdateRate(1).build());
    public static final Block BELL=new Block(AbstractBlock.Settings.copy(Blocks.LODESTONE).strength(-1,3600000)){
        @Override public ActionResult onUse(BlockState state,World w,BlockPos pos,PlayerEntity p,Hand hand,BlockHitResult hit){
            if(w.isClient)return ActionResult.SUCCESS;
            var s=OverworldSite.get(((ServerWorld)w).getServer().getOverworld());
            if(!w.getRegistryKey().equals(World.OVERWORLD)||!s.ready()||pos.getX()!=s.x+6||pos.getZ()!=s.z+68)return ActionResult.PASS;
            board((ServerPlayerEntity)p,s,pos.getY()==s.entryY+1);return ActionResult.CONSUME;
        }
    };
    public static final Item ECHO=new Item(new Item.Settings().maxCount(1)){
        @Override public TypedActionResult<ItemStack> use(World world,PlayerEntity p,Hand hand){
            if(!world.isClient)guide((ServerPlayerEntity)p);
            return TypedActionResult.success(p.getStackInHand(hand),world.isClient);
        }
    };
    private static final Map<UUID,Long> sleepStarts=new HashMap<>();
    private static final Map<UUID,Boolean> pendingLandings=new HashMap<>();
    public static void queueLanding(ServerPlayerEntity p,boolean upper){pendingLandings.put(p.getUuid(),upper);}
    private Journey(){}
    public static void initialize(){
        Registry.register(Registries.BLOCK,GlimmeringAbyss.id("station_bell"),BELL);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server->{
            var queued=new HashMap<>(pendingLandings);pendingLandings.clear();
            queued.forEach((id,upper)->{var player=server.getPlayerManager().getPlayer(id);if(player!=null){
                player.stopRiding();pendingLandings.remove(id);
                land(player,OverworldSite.get(server.getOverworld()),upper);
                System.out.println("ABYSS_SAFE_LANDING "+player.getPos()+" health="+player.getHealth());
            }});
        });
        Registry.register(Registries.ITEM,GlimmeringAbyss.id("echo_shard"),ECHO);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server->{sleepStarts.clear();pendingLandings.clear();});
        EntitySleepEvents.START_SLEEPING.register((e,p)->{if(e instanceof ServerPlayerEntity sp)sleepStarts.put(sp.getUuid(),sp.getWorld().getTime());});
        EntitySleepEvents.STOP_SLEEPING.register((e,p)->{
            if(e instanceof ServerPlayerEntity sp){
                Long start=sleepStarts.remove(sp.getUuid());
                if(start!=null&&sp.getWorld().getTime()-start>=95&&sp.getWorld().getTimeOfDay()%24000<1000)dream(sp);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((h,sender,server)->server.execute(()->{
            sync(h.player);var s=OverworldSite.get(server.getOverworld());
            if(s.journeys.containsKey(h.player.getUuid()))recover(h.player,s,"旅途中断后，你已回到安全站台。");
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((h,server)->{
            // Player position can be saved before DISCONNECT. Keep the durable ride record; JOIN owns recovery.
            sleepStarts.remove(h.player.getUuid());
        });
    }
    public static void syncAll(MinecraftServer server){for(var p:server.getPlayerManager().getPlayerList())sync(p);}
    public static void sync(ServerPlayerEntity p){
        var s=OverworldSite.get(p.getServer().getOverworld());var b=PacketByteBufs.create();
        b.writeBoolean(s.ready());b.writeInt(s.x);b.writeInt(s.z);b.writeInt(s.entryY);b.writeInt(s.approachLength);b.writeInt(s.approachY);ServerPlayNetworking.send(p,SITE_PACKET,b);
    }
    public static void dream(ServerPlayerEntity p){
        var s=OverworldSite.get(p.getServer().getOverworld());long day=p.getWorld().getTimeOfDay()/24000;
        if(!s.ready()||s.dreamDays.getOrDefault(p.getUuid(),-1L)==day)return;
        int n=Math.min(2,s.dreams.getOrDefault(p.getUuid(),0)+1);s.dreams.put(p.getUuid(),n);s.dreamDays.put(p.getUuid(),day);s.markDirty();
        System.out.println("ABYSS_DREAM day="+day+" step="+n);
        p.sendMessage(Text.literal(n==1?"梦中，蓝色巨柱间亮着几扇暖窗。山间传来一声悠远的铃响……":"梦中的幻翼停在玻璃车厢上。掌心的微光，正指向那处山间站台。"),false);
        if(n>=2&&!p.getInventory().contains(new ItemStack(ECHO)))p.getInventory().offerOrDrop(new ItemStack(ECHO));
    }
    public static void guide(ServerPlayerEntity p){
        var s=OverworldSite.get(p.getServer().getOverworld());
        if(!s.ready()){p.sendMessage(Text.literal("微光尚未回应。先在这片世界歇息片刻。"),true);return;}
        if(!p.getWorld().getRegistryKey().equals(World.OVERWORLD)){p.sendMessage(Text.literal("回响来自主世界的群山。"),true);return;}
        Vec3d target=Vec3d.ofCenter(s.approach());Vec3d delta=target.subtract(p.getPos());
        var dir=new Vec3d(delta.x,0,delta.z).normalize();
        for(int i=1;i<=20;i++)p.getServerWorld().spawnParticles(p,ParticleTypes.END_ROD,true,p.getX()+dir.x*i*.5,p.getEyeY(),p.getZ()+dir.z*i*.5,1,.02,.03,.02,0);
        String ns=delta.z<0?"北":"南",ew=delta.x<0?"西":"东";
        String direction=Math.abs(delta.x)>Math.abs(delta.z)*2?ew:Math.abs(delta.z)>Math.abs(delta.x)*2?ns:ew+ns;
        p.sendMessage(Text.literal("微光指向"+direction+"，约 "+(int)Math.hypot(delta.x,delta.z)+" 格。"+(delta.horizontalLength()<32?"循灯走向站台，触碰回响铃登车。":"")),true);
        p.getItemCooldownManager().set(ECHO,30);
    }
    public static void board(ServerPlayerEntity p,OverworldSite s,boolean upper){
        if(!s.ready()||p.hasVehicle())return;
        var w=p.getServerWorld();
        for(var e:w.iterateEntities())if(e instanceof CableCarEntity&&!e.isRemoved()){p.sendMessage(Text.literal("缆车正在运行，请在站台稍候。"),true);return;}
        var car=CAR.create(w);car.configure(s,upper);
        if(!w.spawnEntity(car)){p.sendMessage(Text.literal("缆车暂未就绪，请稍后再试。"),true);return;}
        s.journeys.put(p.getUuid(),upper);s.markDirty();w.getPersistentStateManager().save();
        p.teleport(w,s.x+.5,upper?s.entryY+1:45,s.z+70.5,180,8);
        p.startRiding(car,true);p.sendMessage(Text.literal("玻璃车门已关闭。请欣赏沿途风景；潜行可提前返回安全站台。"),true);
    }
    public static void recover(ServerPlayerEntity p,OverworldSite s,String message){
        boolean upper=s.journeys.getOrDefault(p.getUuid(),true);
        // Vanilla restores RootVehicle after the Fabric JOIN notification. Recover once that login work has finished.
        queueLanding(p,upper);System.out.println("ABYSS_RECOVERY_QUEUED upper="+upper);
    }
    public static void land(ServerPlayerEntity p,OverworldSite s,boolean upper){
        var pos=s.landing(upper);var w=p.getServer().getOverworld();w.getChunk(pos);
        // Re-establish only the tiny emergency landing pad if a player removed its footing.
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            var q=pos.add(dx,0,dz);w.setBlockState(q.down(),Blocks.POLISHED_DEEPSLATE.getDefaultState(),Block.NOTIFY_ALL);
            w.setBlockState(q,Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);w.setBlockState(q.up(),Blocks.AIR.getDefaultState(),Block.NOTIFY_ALL);
        }
        p.fallDistance=0;p.setVelocity(Vec3d.ZERO);p.teleport(w,pos.getX()+.5,pos.getY(),pos.getZ()+.5,upper?90:180,0);
        s.journeys.remove(p.getUuid());s.markDirty();
    }
}
