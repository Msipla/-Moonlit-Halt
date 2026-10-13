package dev.mispla.abyss;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One seed-selected, versioned landscape per save. No global static world state. */
public final class OverworldSite extends PersistentState {
    public static final int MIN_X=-112, MAX_X=111, MIN_Z=-176, MAX_Z=111, OFFSET_Y=6;
    public static final int CHUNKS=14*18;
    public int x,z,entryY,attempt,cursor,approachLength,approachY;
    public int layout=100;
    public String stage="search";
    public String reason="";
    public final Map<UUID,Integer> dreams=new HashMap<>();
    public final Map<UUID,Long> dreamDays=new HashMap<>();
    public final Map<UUID,Boolean> journeys=new HashMap<>();
    // Runtime-only recovery: an unfinished placement is replayed idempotently.
    public boolean resumed;
    public final java.util.List<String> caps=new java.util.ArrayList<>();
    public static OverworldSite get(ServerWorld w){
        return w.getPersistentStateManager().getOrCreate(OverworldSite::read,OverworldSite::new,"glimmeringabyss_site_v1");
    }
    private static OverworldSite read(NbtCompound n){
        var s=new OverworldSite();s.x=n.getInt("x");s.z=n.getInt("z");s.entryY=n.getInt("entryY");
        s.layout=n.contains("layout")?n.getInt("layout"):70;
        s.approachLength=n.getInt("approachLength");s.approachY=n.getInt("approachY");
        s.attempt=n.getInt("attempt");s.cursor=n.getInt("cursor");s.stage=n.getString("stage");s.reason=n.getString("reason");
        if(s.stage.equals("carve")){s.cursor=0;s.resumed=true;}
        if(s.stage.equals("check"))s.cursor=0;
        var cs=n.getList("caps",net.minecraft.nbt.NbtElement.STRING_TYPE);for(int i=0;i<cs.size();i++)s.caps.add(cs.getString(i));
        var d=n.getCompound("dreams");for(String k:d.getKeys())s.dreams.put(UUID.fromString(k),d.getInt(k));
        d=n.getCompound("dreamDays");for(String k:d.getKeys())s.dreamDays.put(UUID.fromString(k),d.getLong(k));
        d=n.getCompound("journeys");for(String k:d.getKeys())s.journeys.put(UUID.fromString(k),d.getBoolean(k));
        return s;
    }
    @Override public NbtCompound writeNbt(NbtCompound n){
        n.putInt("layout",layout);
        n.putInt("approachLength",approachLength);n.putInt("approachY",approachY);n.putInt("schema",1);n.putInt("x",x);n.putInt("z",z);n.putInt("entryY",entryY);n.putInt("attempt",attempt);n.putInt("cursor",cursor);n.putString("stage",stage);n.putString("reason",reason);
        var cs=new net.minecraft.nbt.NbtList();caps.forEach(v->cs.add(net.minecraft.nbt.NbtString.of(v)));n.put("caps",cs);
        var d=new NbtCompound();dreams.forEach((k,v)->d.putInt(k.toString(),v));n.put("dreams",d);
        var days=new NbtCompound();dreamDays.forEach((k,v)->days.putLong(k.toString(),v));n.put("dreamDays",days);
        var rides=new NbtCompound();journeys.forEach((k,v)->rides.putBoolean(k.toString(),v));n.put("journeys",rides);return n;
    }
    public int minX(){return layout>=100?-208:MIN_X;}
    public int maxX(){return layout>=100?127:MAX_X;}
    public int minZ(){return layout>=100?-320:MIN_Z;}
    public int maxZ(){return layout>=100?127:MAX_Z;}
    public int widthChunks(){return (maxX()-minX()+1)/16;}
    public int chunks(){return widthChunks()*(maxZ()-minZ()+1)/16;}
    public int chunkX(int index){return x/16+minX()/16+index%widthChunks();}
    public int chunkZ(int index){return z/16+minZ()/16+index/widthChunks();}
    public int dockX(){return layout>=100?24:0;}
    public int dockZ(){return layout>=100?58:70;}
    public BlockPos at(int lx,int ly,int lz){return new BlockPos(x+lx,ly+OFFSET_Y,z+lz);}
    public boolean ready(){return stage.equals("ready");}
    public boolean reserved(){return stage.equals("carve")||ready();}
    public boolean contains(double px,double py,double pz){
        if(!reserved())return false;
        double lx=px-x,lz=pz-z;
        // Whole cavern volume plus its separate surface station and shaft.
        return lx>=minX()&&lx<=maxX()+1&&lz>=minZ()&&lz<=maxZ()+1&&py>=-58&&py<=117
            || lx>=-10&&lx<=13+approachLength&&lz>=60&&lz<=81&&py>=45&&py<=Math.max(entryY,approachY)+9;
    }
    public BlockPos approach(){return new BlockPos(x+9+approachLength,approachY+1,z+70);}
    public BlockPos landing(boolean upper){return new BlockPos(x+6+(upper?0:dockX()),upper?entryY+1:45,z+(upper?70:dockZ()));}
}
