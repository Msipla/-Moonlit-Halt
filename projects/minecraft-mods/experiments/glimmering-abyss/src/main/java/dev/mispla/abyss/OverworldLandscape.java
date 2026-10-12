package dev.mispla.abyss;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import java.util.Arrays;
import java.util.Random;

/** Bounded preflight, then one chunk per tick. Never changes the overworld generator. */
public final class OverworldLandscape {
    private static short[] town;
    private static BlockState[] palette;
    private static final int SX=139,SY=143,SZ=180;
    private static final int FLAGS=Block.NOTIFY_LISTENERS|Block.FORCE_STATE;
    private OverworldLandscape(){}
    public static void initialize(){ServerTickEvents.END_SERVER_TICK.register(server->{
        if(!Boolean.parseBoolean(System.getProperty("abyss.worldgen.enabled","true")))return;
        var w=server.getOverworld();if(w.getTime()%2==0)tick(w);
    });}
    private static int height(ServerWorld w,int x,int z){return w.getChunkManager().getChunkGenerator().getHeight(x,z,Heightmap.Type.OCEAN_FLOOR_WG,w,w.getChunkManager().getNoiseConfig());}
    /** World.getTopY returns bottom Y for an unloaded remote chunk. Load explicitly. */
    public static int surface(ServerWorld w,Heightmap.Type type,int x,int z){
        return w.getChunk(x>>4,z>>4).sampleHeightmap(type,x&15,z&15)+1;
    }
    public static int ceiling(int x,int z){
        var c=Terrain.column(x,z,Terrain.SEED);
        int top=(int)Math.ceil(c.roof())+4;
        if(x>=-55&&x<=83&&z>=-106&&z<=73)top=Math.max(top,82);
        return Math.min(111,Math.max(48,top))+OverworldSite.OFFSET_Y;
    }
    private static void tick(ServerWorld w){
        var s=OverworldSite.get(w);
        if(s.ready()||s.stage.equals("unavailable"))return;
        try{
            if(!(w.getChunkManager().getChunkGenerator() instanceof NoiseChunkGenerator)||w.getBottomY()>-64){
                s.stage="unavailable";s.reason="Requires a normal height overworld with noise terrain";s.markDirty();return;
            }
            if(s.stage.equals("search")){
                if(s.attempt>=4096){s.stage="unavailable";s.reason="No covered, uninhabited candidate in bounded search";s.markDirty();return;}
                var rng=new Random(w.getSeed()^0x41B155L^(s.attempt*0x9E3779B97F4A7C15L));s.attempt++;
                int x=(rng.nextInt(1024)-512)*16,z=(rng.nextInt(1024)-512)*16;
                if(Math.abs(x)<768&&Math.abs(z)<768)return;
                if(height(w,x,z)<130)return;
                for(int lx=-112;lx<=111;lx+=32)for(int lz=-176;lz<=111;lz+=32)
                    if(height(w,x+lx,z+lz)<ceiling(lx,lz)+12)return;
                s.x=x;s.z=z;s.cursor=0;s.caps.clear();s.stage="check";s.markDirty();
                System.out.println("ABYSS_SITE_CANDIDATE "+x+","+z+" attempt="+s.attempt);return;
            }
            int cx=s.x/16-7+s.cursor%14,cz=s.z/16-11+s.cursor/14;
            var chunk=w.getChunk(cx,cz);
            if(s.stage.equals("check")){
                boolean valid=chunk.getInhabitedTime()==0;
                for(int dx=0;dx<16&&valid;dx++)for(int dz=0;dz<16&&valid;dz++){
                    int wx=cx*16+dx,wz=cz*16+dz,top=ceiling(wx-s.x,wz-s.z);
                    // Exact full-footprint check after vanilla carving/surface/features, not only sampled noise heights.
                    int h=chunk.sampleHeightmap(Heightmap.Type.OCEAN_FLOOR,dx,dz)+1;
                    if(h<top+10)valid=false;
                    // The painter retains a four-block solid local roof below this cap.
                    // Existing natural caves above it are permitted; requiring every native
                    // voxel to be solid would reject otherwise well-covered mountain sites.
                }
                if(!valid){s.stage="search";s.cursor=0;s.reason="Candidate rejected before edits: coverage or existing habitation";s.markDirty();return;}
                if(s.caps.size()>s.cursor)s.caps.set(s.cursor,capHash(w,s,cx,cz));else s.caps.add(capHash(w,s,cx,cz));
                if(++s.cursor==OverworldSite.CHUNKS){
                    s.entryY=surface(w,Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,s.x,s.z+70);
                    // Dry, reachable station; the exact shaft is the sole intended surface opening.
                    if(s.entryY>240||s.entryY<100){s.stage="search";s.cursor=0;s.markDirty();return;}
                    s.stage="carve";s.cursor=0;s.reason="";s.markDirty();w.getPersistentStateManager().save();
                    System.out.println("ABYSS_SITE_PREFLIGHT_PASS "+s.x+","+s.z+" entry="+s.entryY);
                }
                s.markDirty();return;
            }
            if(s.stage.equals("carve")){
                loadTown();paint(w,s,cx,cz);
                if(++s.cursor==OverworldSite.CHUNKS){
                    stations(w,s);s.stage="ready";s.markDirty();w.getServer().save(false,true,true);
                    Journey.syncAll(w.getServer());System.out.println("ABYSS_SITE_READY "+s.x+","+s.z+" entry="+s.entryY);
                }
                s.markDirty();
            }
        }catch(Exception e){s.reason=e.toString();s.markDirty();System.err.println("ABYSS_SITE_ERROR "+s.stage+" "+s.cursor);e.printStackTrace();s.stage="unavailable";}
    }
    public static String capHash(ServerWorld w,OverworldSite s,int cx,int cz){
        try{
            var hash=java.security.MessageDigest.getInstance("SHA-256");
            var chunk=w.getChunk(cx,cz);
            for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++){
                int x=cx*16+dx,z=cz*16+dz,lx=x-s.x,lz=z-s.z;
                if(lx>=-6&&lx<=9&&lz>=64&&lz<=77)continue;
                int h=chunk.sampleHeightmap(Heightmap.Type.OCEAN_FLOOR,dx,dz)+1;
                hash.update((h+":"+w.getBlockState(new BlockPos(x,h-1,z))+":"+w.getBlockState(new BlockPos(x,h-2,z))).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                for(int y=-64;y<=-59;y++)hash.update(w.getBlockState(new BlockPos(x,y,z)).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return java.util.HexFormat.of().formatHex(hash.digest());
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void loadTown()throws Exception{
        if(town!=null)return;
        try(var in=OverworldLandscape.class.getResourceAsStream("/data/glimmeringabyss/structures/town.nbt")){
            var n=NbtIo.readCompressed(in);var p=n.getList("palette",NbtElement.COMPOUND_TYPE);
            palette=new BlockState[p.size()];for(int i=0;i<p.size();i++)palette[i]=NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(),p.getCompound(i));
            town=new short[SX*SY*SZ];Arrays.fill(town,(short)-1);var b=n.getList("blocks",NbtElement.COMPOUND_TYPE);
            for(int i=0;i<b.size();i++){var row=b.getCompound(i);var a=row.getList("pos",NbtElement.INT_TYPE);town[(a.getInt(0)*SZ+a.getInt(2))*SY+a.getInt(1)]=(short)row.getInt("state");}
        }
    }
    static BlockState townAt(int x,int y,int z){
        int dx=x+55,dy=y+63,dz=z+106;if(dx<0||dx>=SX||dy<0||dy>=SY||dz<0||dz>=SZ)return null;
        int i=town[(dx*SZ+dz)*SY+dy];return i<0?null:palette[i];
    }
    static BlockState expected(Terrain.Column c,int y){
        int x=c.x(),z=c.z();var t=townAt(x,y,z);
        BlockState state=t;
        if(state==null)state=switch(c.materialAt(y)){
            case Terrain.AIR -> Blocks.AIR.getDefaultState();
            case Terrain.FLOOR -> Blocks.POLISHED_DEEPSLATE.getDefaultState();
            case Terrain.RAIL_NS -> Blocks.IRON_BARS.getDefaultState().with(Properties.NORTH,true).with(Properties.SOUTH,true);
            case Terrain.RAIL_EW -> Blocks.IRON_BARS.getDefaultState().with(Properties.EAST,true).with(Properties.WEST,true);
            default -> Blocks.DEEPSLATE.getDefaultState();
        };
        // Add only low, irregular buttresses to the accepted shelf; walking floor/buildings/rails stay exact.
        if(y<35&&y>-62&&z>=-69&&z<=50&&x>=0&&x<=89&&c.pillarRatio()>1.4){
            double edge=22+(int)(2*Math.sin(z/13.0)+1.5*Math.sin(z/5.0));
            double foot=Math.min(14,(36-y)*.16),ripple=2*Math.sin(z*.19+y*.17)+Math.sin(y*.43+z*.11);
            if(x>=edge-foot+ripple&&x<=83+foot*.35&&z>=-65-foot*.25&&z<=46+foot*.25
                && (state.isAir()||state.isOf(Blocks.DEEPSLATE)||state.isOf(Blocks.COBBLED_DEEPSLATE)))
                state=(Math.floorMod(y+z/7,17)<3?Blocks.COBBLED_DEEPSLATE:Blocks.DEEPSLATE).getDefaultState();
        }
        return state;
    }
    private static void paint(ServerWorld w,OverworldSite s,int cx,int cz){
        var pos=new BlockPos.Mutable();
        for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++){
            int x=cx*16+dx-s.x,z=cz*16+dz-s.z;var c=Terrain.column(x,z,Terrain.SEED);
            int top=ceiling(x,z)-OverworldSite.OFFSET_Y;
            for(int y=-63;y<=top;y++){
                var state=expected(c,y);pos.set(s.x+x,y+OverworldSite.OFFSET_Y,s.z+z);
                if(!w.getBlockState(pos).isOf(Blocks.BEDROCK))w.setBlockState(pos,state,FLAGS);
            }
        }
        for(var f:Terrain.fixtures(Terrain.SEED))if((s.x+f.x())>>4==cx&&(s.z+f.z())>>4==cz)
            w.setBlockState(s.at(f.x(),f.y(),f.z()),GlimmeringAbyss.GLIMMER.getDefaultState(),FLAGS);
    }
    private static void set(ServerWorld w,OverworldSite s,int x,int y,int z,Block b){w.setBlockState(new BlockPos(s.x+x,y,s.z+z),b.getDefaultState(),Block.NOTIFY_ALL);}
    private static void stations(ServerWorld w,OverworldSite s){
        // A sealed, nine-wide shaft, opened into the existing observation balcony.
        for(int x=-4;x<=4;x++)for(int z=66;z<=74;z++)for(int y=45;y<=s.entryY+5;y++){
            if(Math.abs(x)==4||z==66||z==74){
                if(y>80)set(w,s,x,y,z,Blocks.DEEPSLATE_BRICKS);
            }else set(w,s,x,y,z,Blocks.AIR);
        }
        for(int y:new int[]{44,s.entryY}){
            for(int x=-6;x<=9;x++)for(int z=64;z<=77;z++){
                if(y==s.entryY&&x>=-3&&x<=3&&z>=67&&z<=73)continue;
                set(w,s,x,y,z,Blocks.POLISHED_DEEPSLATE);
                if(y==s.entryY){
                    for(int h=1;h<=4;h++)set(w,s,x,y+h,z,Blocks.AIR);
                    if(x==-6||x==9||z==64||z==77)set(w,s,x,y+1,z,Blocks.COBBLED_DEEPSLATE_WALL);
                }
            }
            // Guard the upper shaft. Bell remotely boards the passenger into a closed cabin.
            if(y==s.entryY)for(int x=-4;x<=4;x++)for(int z=66;z<=74;z++)
                if(Math.abs(x)==4||z==66||z==74)set(w,s,x,y+1,z,Blocks.COBBLED_DEEPSLATE_WALL);
            set(w,s,6,y+1,68,Journey.BELL);set(w,s,7,y+1,68,Blocks.LANTERN);
        }
        // Footing under the surface platform: no floating station on a sloping mountain.
        for(int x=-6;x<=9;x++)for(int z=64;z<=77;z++)for(int y=s.entryY-1;y>=s.entryY-16;y--){
            if(x>=-3&&x<=3&&z>=67&&z<=73)continue;
            var p=new BlockPos(s.x+x,y,s.z+z);if(w.getBlockState(p).isSolidBlock(w,p))break;
            set(w,s,x,y,z,Blocks.DEEPSLATE_BRICKS);
        }
    }
}
