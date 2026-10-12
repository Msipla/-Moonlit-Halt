package dev.mispla.abyss;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** A seated closed cabin on a fixed route. The phantom is a harmless visual rig, not an attacking mob. */
public final class CableCarEntity extends Entity {
    public static final int DURATION=900;
    private boolean fromUpper,arriving;
    private int progress,topY,originX,originZ;
    public CableCarEntity(EntityType<? extends CableCarEntity> type,World w){super(type,w);setNoGravity(true);setInvulnerable(true);}
    public void configure(OverworldSite s,boolean upper){originX=s.x;originZ=s.z;topY=s.entryY+1;fromUpper=upper;setPosition(s.x+.5,upper?topY:45,s.z+70.5);}
    @Override protected void initDataTracker(){}
    @Override protected void readCustomDataFromNbt(NbtCompound n){originX=n.getInt("x");originZ=n.getInt("z");topY=n.getInt("top");fromUpper=n.getBoolean("upper");progress=n.getInt("progress");}
    @Override protected void writeCustomDataToNbt(NbtCompound n){n.putInt("x",originX);n.putInt("z",originZ);n.putInt("top",topY);n.putBoolean("upper",fromUpper);n.putInt("progress",progress);}
    @Override public Packet<ClientPlayPacketListener> createSpawnPacket(){return new EntitySpawnS2CPacket(this);}
    @Override protected boolean canAddPassenger(Entity passenger){return passenger instanceof ServerPlayerEntity&&getPassengerList().isEmpty();}
    @Override public double getMountedHeightOffset(){return .2;}
    @Override public boolean damage(DamageSource source,float amount){return false;}
    @Override public boolean isCollidable(){return true;}
    @Override public void tick(){
        super.tick();if(getWorld().isClient)return;
        if(!hasPassengers()){discard();return;}
        var w=(ServerWorld)getWorld();var s=OverworldSite.get(w);
        if(!s.ready()){abort();return;}
        double t=Math.min(1,++progress/(double)DURATION),ease=t*t*(3-2*t);
        double y=fromUpper?topY+(45-topY)*ease:45+(topY-45)*ease;
        var pos=BlockPos.ofFloored(originX+.5,y,originZ+70.5);
        w.getChunkManager().addTicket(ChunkTicketType.PORTAL,new ChunkPos(pos),3,pos);
        if(!w.isSpaceEmpty(this,new net.minecraft.util.math.Box(originX-1, y,originZ+69,originX+2,y+3.2,originZ+72))){abort();return;}
        setPosition(originX+.5,y,originZ+70.5);setVelocity(Vec3d.ZERO);
        for(var e:getPassengerList())e.fallDistance=0;
        if(progress>=DURATION){
            arriving=true;var passengers=java.util.List.copyOf(getPassengerList());removeAllPassengers();
            for(var e:passengers)if(e instanceof ServerPlayerEntity p)Journey.land(p,s,!fromUpper);
            discard();
        }
    }
    private void abort(){var s=OverworldSite.get((ServerWorld)getWorld());arriving=true;var list=java.util.List.copyOf(getPassengerList());removeAllPassengers();for(var e:list)if(e instanceof ServerPlayerEntity p)Journey.land(p,s,fromUpper);discard();}
    @Override protected void removePassenger(Entity passenger){
        super.removePassenger(passenger);
        if(!arriving&&!getWorld().isClient&&passenger instanceof ServerPlayerEntity p){
            Journey.land(p,OverworldSite.get((ServerWorld)getWorld()),fromUpper);discard();
        }
    }
}
