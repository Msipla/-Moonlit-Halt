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
    private static short[] naturalTown;
    private static BlockState[] naturalPalette;
    private static Blueprint abyssTown,settlementTown,settlementFinish,settlementAcceptance;
    private record Blueprint(int sx,int sy,int sz,short[] blocks,BlockState[] palette,
                             java.util.Map<Long,net.minecraft.nbt.NbtCompound> blockEntities){
        BlockState at(int x,int y,int z){
            int dx=x+180,dy=y+8,dz=z+290;
            if(dx<0||dx>=sx||dy<0||dy>=sy||dz<0||dz>=sz)return null;
            int i=blocks[(dx*sz+dz)*sy+dy];return i<0?null:palette[i];
        }
    }
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
    private static int ground(ServerWorld w,int x,int z){
        var p=new BlockPos.Mutable(x,surface(w,Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,x,z)-1,z);
        while(p.getY()>w.getBottomY()){
            var b=w.getBlockState(p);
            if(b.isSolidBlock(w,p)&&!b.isIn(net.minecraft.registry.tag.BlockTags.LOGS)&&!b.isIn(net.minecraft.registry.tag.BlockTags.LEAVES))return p.getY();
            p.move(net.minecraft.util.math.Direction.DOWN);
        }
        return w.getBottomY();
    }
    private static boolean planApproach(ServerWorld w,OverworldSite s){
        s.entryY=ground(w,s.x,s.z+70)+1;
        if(s.entryY<100||s.entryY>240)return false;
        for(int length=8;length<=32;length+=4){
            int y=ground(w,s.x+9+length,s.z+70);
            if(Math.abs(y-s.entryY)<length){s.approachLength=length;s.approachY=y;return true;}
        }
        return false;
    }
    public static int ceiling(int x,int z){
        return CavernShape.ceiling(x,z)+OverworldSite.OFFSET_Y;
    }
    public static int ceiling(OverworldSite s,int x,int z){
        if(s.layout>=100)return AbyssShape.ceiling(x,z)+OverworldSite.OFFSET_Y;
        if(s.layout>=75)return ceiling(x,z);
        var c=Terrain.column(x,z,Terrain.SEED);
        int top=(int)Math.ceil(c.roof())+4;
        if(x>=-55&&x<=83&&z>=-106&&z<=73)top=Math.max(top,82);
        return Math.min(111,Math.max(48,top))+OverworldSite.OFFSET_Y;
    }
    private static boolean remote(int x,int z){return x<-112||x>111||z<-176||z>111;}
    private static int ceiling(ServerWorld w,OverworldSite s,int x,int z){
        int cap=ceiling(s,x,z);
        if(s.layout>=100&&s.columnCaps.length>0){
            int saved=s.columnCaps[s.capIndex(x,z)];
            if(saved!=Integer.MIN_VALUE)return saved;
        }
        // Compatibility for an interrupted, unshipped first sample without persisted caps.
        if(s.layout>=100&&remote(x,z))cap=Math.min(cap,height(w,s.x+x,s.z+z)-16);
        return cap;
    }
    private static int routeCap(int layout,int x,int z){
        // A sparse blueprint has no claim on the rest of the terrain column.
        var blueprint=blueprint(layout);
        if(blueprint==null)return -58;
        for(int y=blueprint.sy-1-8;y>=-8;y--)if(blueprint.at(x,y,z)!=null)return y+4+OverworldSite.OFFSET_Y;
        return -58;
    }
    private static void tick(ServerWorld w){
        var s=OverworldSite.get(w);
        if(s.ready()||s.stage.equals("unavailable"))return;
        try{
            if(!(w.getChunkManager().getChunkGenerator() instanceof NoiseChunkGenerator)||w.getBottomY()>-64){
                s.stage="unavailable";s.reason="Requires a normal height overworld with noise terrain";s.markDirty();return;
            }
            if(s.layout>=100)loadAbyssTown(s.layout);
            if(s.stage.equals("search")){
                if(s.attempt>=4096){s.stage="unavailable";s.reason="No covered, uninhabited candidate in bounded search";s.markDirty();return;}
                var rng=new Random(w.getSeed()^0x41B155L^(s.attempt*0x9E3779B97F4A7C15L));s.attempt++;
                int x=(rng.nextInt(1024)-512)*16,z=(rng.nextInt(1024)-512)*16;
                if(Math.abs(x)<768&&Math.abs(z)<768)return;
                if(height(w,x,z)<130)return;
                for(int lx=-112;lx<=111;lx+=32)for(int lz=-176;lz<=111;lz+=32)
                    if(!(s.layout>=100&&remote(lx,lz))&&height(w,x+lx,z+lz)<ceiling(s,lx,lz)+12)return;
                s.x=x;s.z=z;if(!planApproach(w,s))return;s.cursor=0;s.caps.clear();if(s.layout>=100)s.resetCaps();s.stage="check";s.markDirty();
                System.out.println("ABYSS_SITE_CANDIDATE "+x+","+z+" attempt="+s.attempt);return;
            }
            int cx=s.chunkX(s.cursor),cz=s.chunkZ(s.cursor);
            var chunk=w.getChunk(cx,cz);
            if(s.stage.equals("check")){
                boolean valid=chunk.getInhabitedTime()==0;
                if(s.layout>=100&&s.columnCaps.length==0)s.resetCaps();
                for(int dx=0;dx<16&&valid;dx++)for(int dz=0;dz<16&&valid;dz++){
                    int wx=cx*16+dx,wz=cz*16+dz,top=ceiling(s,wx-s.x,wz-s.z);
                    // Exact full-footprint check after vanilla carving/surface/features, not only sampled noise heights.
                    int h=ground(w,wx,wz)+1; // Bare ground, never count a tree canopy as mountain cover.
                    if(s.layout>=100&&remote(wx-s.x,wz-s.z))top=Math.min(top,h-16);
                    if(s.layout>=100)s.columnCaps[s.capIndex(wx-s.x,wz-s.z)]=top;
                    if(h<top+10||s.layout>=100&&remote(wx-s.x,wz-s.z)&&top<routeCap(s.layout,wx-s.x,wz-s.z))valid=false;
                    // The painter retains a four-block solid local roof below this cap.
                    // Existing natural caves above it are permitted; requiring every native
                    // voxel to be solid would reject otherwise well-covered mountain sites.
                }
                if(!valid){s.stage="search";s.cursor=0;s.reason="Candidate rejected before edits: coverage or existing habitation";s.markDirty();return;}
                if(s.caps.size()>s.cursor)s.caps.set(s.cursor,capHash(w,s,cx,cz));else s.caps.add(capHash(w,s,cx,cz));
                if(++s.cursor==s.chunks()){
                    // Dry, reachable station; the exact shaft is the sole intended surface opening.
                    if(s.entryY>240||s.entryY<100){s.stage="search";s.cursor=0;s.markDirty();return;}
                    s.stage="carve";s.cursor=0;s.reason="";s.markDirty();w.getPersistentStateManager().save();
                    System.out.println("ABYSS_SITE_PREFLIGHT_PASS "+s.x+","+s.z+" entry="+s.entryY);
                }
                s.markDirty();return;
            }
            if(s.stage.equals("carve")){
                loadTown();if(s.layout>=75)loadNaturalTown();if(s.layout>=100)loadAbyssTown(s.layout);paint(w,s,cx,cz);
                if(++s.cursor==s.chunks()){
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
                if(lx>=-6&&lx<=9&&lz>=64&&lz<=77 || lx>=10&&lx<=10+s.approachLength&&lz>=67&&lz<=73)continue;
                int h=ground(w,x,z)+1;
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
    private static void loadNaturalTown()throws Exception{
        if(naturalTown!=null)return;
        try(var in=OverworldLandscape.class.getResourceAsStream("/data/glimmeringabyss/structures/natural_town.nbt")){
            var n=NbtIo.readCompressed(in);var p=n.getList("palette",NbtElement.COMPOUND_TYPE);
            naturalPalette=new BlockState[p.size()];for(int i=0;i<p.size();i++)naturalPalette[i]=NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(),p.getCompound(i));
            naturalTown=new short[148*46*180];Arrays.fill(naturalTown,(short)-1);var blocks=n.getList("blocks",NbtElement.COMPOUND_TYPE);
            for(int i=0;i<blocks.size();i++){var row=blocks.getCompound(i);var a=row.getList("pos",NbtElement.INT_TYPE);naturalTown[(a.getInt(0)*180+a.getInt(2))*46+a.getInt(1)]=(short)row.getInt("state");}
        }
    }
    private static BlockState naturalAt(int x,int y,int z){
        int dx=x+64,dy=y-34,dz=z+106;if(dx<0||dx>=148||dy<0||dy>=46||dz<0||dz>=180)return null;
        int i=naturalTown[(dx*180+dz)*46+dy];return i<0?null:naturalPalette[i];
    }
    private static void loadAbyssTown(int layout)throws Exception{
        if(blueprint(layout)!=null)return;
        String name=layout>=103?"settlement_acceptance":layout>=102?"settlement_finish":layout>=101?"settlement_town":"abyss_town";
        try(var in=OverworldLandscape.class.getResourceAsStream("/data/glimmeringabyss/structures/"+name+".nbt")){
            var n=NbtIo.readCompressed(in);var p=n.getList("palette",NbtElement.COMPOUND_TYPE);var size=n.getList("size",NbtElement.INT_TYPE);
            int sx=size.getInt(0),sy=size.getInt(1),sz=size.getInt(2);var palette=new BlockState[p.size()];
            for(int i=0;i<p.size();i++)palette[i]=NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(),p.getCompound(i));
            var blocks=new short[sx*sy*sz];Arrays.fill(blocks,(short)-1);var rows=n.getList("blocks",NbtElement.COMPOUND_TYPE);
            var entities=new java.util.HashMap<Long,net.minecraft.nbt.NbtCompound>();
            for(int i=0;i<rows.size();i++){
                var row=rows.getCompound(i);var a=row.getList("pos",NbtElement.INT_TYPE);
                blocks[(a.getInt(0)*sz+a.getInt(2))*sy+a.getInt(1)]=(short)row.getInt("state");
                if(row.contains("nbt"))entities.put(new BlockPos(a.getInt(0)-180,a.getInt(1)-8,a.getInt(2)-290).asLong(),row.getCompound("nbt"));
            }
            var blueprint=new Blueprint(sx,sy,sz,blocks,palette,entities);
            if(layout>=103)settlementAcceptance=blueprint;else if(layout>=102)settlementFinish=blueprint;else if(layout>=101)settlementTown=blueprint;else abyssTown=blueprint;
        }
    }
    private static Blueprint blueprint(int layout){return layout>=103?settlementAcceptance:layout>=102?settlementFinish:layout>=101?settlementTown:abyssTown;}
    private static BlockState abyssExpected(AbyssShape.Column c,int y){
        var t=blueprint(c.layout()).at(c.x(),y,c.z());if(t!=null)return t;
        if(!c.rock(y))return Blocks.AIR.getDefaultState();
        if(c.layout()>=103){
            double fold=Terrain.noise((c.x()+.27*y)/19.0,(c.z()-.16*y)/23.0,41049);
            double seam=y+.24*c.z()+.15*c.x()+8*fold;
            if(Math.abs(seam-34)<1.8&&fold>-.2||Math.abs(seam+13)<2.1&&fold>.25)return Blocks.TUFF.getDefaultState();
            var axis=fold>.24?net.minecraft.util.math.Direction.Axis.X:fold<-.18?net.minecraft.util.math.Direction.Axis.Z:net.minecraft.util.math.Direction.Axis.Y;
            return Blocks.DEEPSLATE.getDefaultState().with(Properties.AXIS,axis);
        }
        double stratum=y+.16*c.x()+4*Math.sin(c.z()*.021);
        if(c.layout()>=102&&c.wallWeight()>0){
            double fold=Terrain.noise(c.z()/22.0,y/18.0,41028);
            double seam=y+.31*c.z()+.12*c.x()+6*fold;
            if(Math.abs(seam-49)<2.2&&Terrain.noise(c.z()/27.0,c.x()/18.0,41029)>-.25
                ||Math.abs(seam-12)<1.6&&fold>.05)return Blocks.TUFF.getDefaultState();
            var axis=fold>.15?net.minecraft.util.math.Direction.Axis.X:fold<-.25?net.minecraft.util.math.Direction.Axis.Z:net.minecraft.util.math.Direction.Axis.Y;
            return Blocks.DEEPSLATE.getDefaultState().with(Properties.AXIS,axis);
        }
        if(c.layout()>=101&&(c.x()>75||c.x()>50&&c.z()>0)){
            stratum+=.25*c.z()+5*Math.sin(c.x()*.044+c.z()*.036);
            if(Math.floorMod((int)Math.floor(stratum),29)<3)return Blocks.TUFF.getDefaultState();
            var axis=Math.sin(c.z()*.033+y*.021)>.35?net.minecraft.util.math.Direction.Axis.X:net.minecraft.util.math.Direction.Axis.Y;
            return Blocks.DEEPSLATE.getDefaultState().with(Properties.AXIS,axis);
        }
        return (Math.floorMod((int)Math.floor(stratum),29)<2?Blocks.TUFF:Blocks.DEEPSLATE).getDefaultState();
    }
    private static BlockState naturalExpected(CavernShape.Column c,int y){
        var t=naturalAt(c.x(),y,c.z());if(t!=null)return t;
        // Retain the proven entry balcony/walkway geometry, away from the geological shelf.
        if(Math.abs(c.x())<=22&&c.z()>=62&&c.z()<=77&&y>=38&&y<=46){
            int m=Terrain.materialAt(c.x(),y,c.z(),Terrain.SEED);
            if(m==Terrain.FLOOR)return Blocks.POLISHED_DEEPSLATE.getDefaultState();
            if(m==Terrain.AIR)return Blocks.AIR.getDefaultState();
            if(m==Terrain.RAIL_NS)return Blocks.IRON_BARS.getDefaultState().with(Properties.NORTH,true).with(Properties.SOUTH,true);
            if(m==Terrain.RAIL_EW)return Blocks.IRON_BARS.getDefaultState().with(Properties.EAST,true).with(Properties.WEST,true);
        }
        if(!c.rock(y))return Blocks.AIR.getDefaultState();
        // Strata follow the rock, not an ornamental covering on the old box.
        double stratum=y+3*Math.sin(c.x()*.043)+2*Math.sin(c.z()*.057);
        return (Math.floorMod((int)Math.floor(stratum),23)<2?Blocks.TUFF:Blocks.DEEPSLATE).getDefaultState();
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
            int x=cx*16+dx-s.x,z=cz*16+dz-s.z;var c=Terrain.column(x,z,Terrain.SEED);var natural=CavernShape.column(x,z);
            int top=ceiling(w,s,x,z)-OverworldSite.OFFSET_Y;var abyss=AbyssShape.column(x,z,top,s.layout);
            for(int y=-63;y<=top;y++){
                var state=s.layout>=100?abyssExpected(abyss,y):s.layout>=75?naturalExpected(natural,y):expected(c,y);pos.set(s.x+x,y+OverworldSite.OFFSET_Y,s.z+z);
                if(!w.getBlockState(pos).isOf(Blocks.BEDROCK))w.setBlockState(pos,state,FLAGS);
            }
            if(s.layout>=103)for(var entry:blueprint(s.layout).blockEntities.entrySet()){
                var local=BlockPos.fromLong(entry.getKey());
                if(local.getX()!=x||local.getZ()!=z)continue;
                var actual=s.at(x,local.getY(),z);var entity=w.getBlockEntity(actual);
                if(entity==null)throw new IllegalStateException("Missing authored block entity at "+actual);
                var tag=entry.getValue().copy();tag.putInt("x",actual.getX());tag.putInt("y",actual.getY());tag.putInt("z",actual.getZ());
                entity.readNbt(tag);entity.markDirty();w.updateListeners(actual,w.getBlockState(actual),w.getBlockState(actual),Block.NOTIFY_LISTENERS);
            }
        }
        for(var f:Terrain.fixtures(Terrain.SEED))if((s.x+f.x())>>4==cx&&(s.z+f.z())>>4==cz
                &&(s.layout<103||fixtureFits(s,f)))
            w.setBlockState(s.at(f.x(),f.y(),f.z()),GlimmeringAbyss.GLIMMER.getDefaultState(),FLAGS);
    }
    /** Historical calibration coordinates may now be empty space or a house.
     * New sites embed emitters only in connected natural rock, never over architecture. */
    static boolean fixtureFits(OverworldSite s,Terrain.Fixture f){
        if(blueprint(s.layout)==null)try{loadAbyssTown(s.layout);}catch(Exception e){throw new IllegalStateException(e);}
        var c=AbyssShape.column(f.x(),f.z(),AbyssShape.ceiling(f.x(),f.z()),s.layout);
        var state=abyssExpected(c,f.y());
        if(!state.isOf(Blocks.DEEPSLATE)&&!state.isOf(Blocks.TUFF))return false;
        if(blueprint(s.layout).at(f.x(),f.y(),f.z())!=null)return false;
        int joined=0;
        for(var d:net.minecraft.util.math.Direction.values()){
            int x=f.x()+d.getOffsetX(),y=f.y()+d.getOffsetY(),z=f.z()+d.getOffsetZ();
            if(AbyssShape.column(x,z,AbyssShape.ceiling(x,z),s.layout).rock(y))joined++;
        }
        return joined>=3;
    }
    private static void set(ServerWorld w,OverworldSite s,int x,int y,int z,Block b){w.setBlockState(new BlockPos(s.x+x,y,s.z+z),b.getDefaultState(),Block.NOTIFY_ALL);}
    private static void stations(ServerWorld w,OverworldSite s){
        // A sealed, nine-wide shaft, opened into the existing observation balcony.
        for(int x=-4;x<=4;x++)for(int z=66;z<=74;z++)for(int y=45;y<=s.entryY+5;y++){
            if(Math.abs(x)==4||z==66||z==74){
                if(y>(s.layout>=75?89:80))set(w,s,x,y,z,Blocks.DEEPSLATE_BRICKS);
            }else set(w,s,x,y,z,Blocks.AIR);
        }
        for(int y:new int[]{44,s.entryY}){
            int ox=y==44?s.dockX():0,oz=y==44?s.dockZ()-70:0;
            for(int x=-6;x<=9;x++)for(int z=64;z<=77;z++){
                if(y==s.entryY&&x>=-3&&x<=3&&z>=67&&z<=73)continue;
                set(w,s,x+ox,y,z+oz,Blocks.POLISHED_DEEPSLATE);
                if(y==44&&s.layout>=100)for(int h=1;h<=8;h++)set(w,s,x+ox,y+h,z+oz,Blocks.AIR);
                if(y==s.entryY){
                    for(int h=1;h<=4;h++)set(w,s,x,y+h,z,Blocks.AIR);
                    if(x==-6||x==9||z==64||z==77)set(w,s,x,y+1,z,Blocks.COBBLED_DEEPSLATE_WALL);
                }
            }
            // Guard the upper shaft. Bell remotely boards the passenger into a closed cabin.
            if(y==s.entryY)for(int x=-4;x<=4;x++)for(int z=66;z<=74;z++)
                if(Math.abs(x)==4||z==66||z==74)set(w,s,x,y+1,z,Blocks.COBBLED_DEEPSLATE_WALL);
            set(w,s,6+ox,y+1,68+oz,Journey.BELL);set(w,s,7+ox,y+1,68+oz,Blocks.LANTERN);
        }
        // A short guarded staircase meets actual native ground, with a three-wide opening in the outer rail.
        for(int step=0;step<=s.approachLength;step++){
            int x=9+step,y=s.entryY+(int)Math.round((s.approachY-s.entryY)*step/(double)s.approachLength);
            int next=step==s.approachLength?y:s.entryY+(int)Math.round((s.approachY-s.entryY)*(step+1)/(double)s.approachLength);
            int base=y+(next>y?1:0);
            for(int z=68;z<=72;z++){
                for(int h=1;h<=4;h++)set(w,s,x,base+h,z,Blocks.AIR);
                for(int h=base-1;h>=base-16;h--){var p=new BlockPos(s.x+x,h,s.z+z);if(w.getBlockState(p).isSolidBlock(w,p))break;set(w,s,x,h,z,Blocks.DEEPSLATE_BRICKS);}
                if(z==68||z==72){set(w,s,x,base,z,Blocks.POLISHED_DEEPSLATE);set(w,s,x,base+1,z,Blocks.COBBLED_DEEPSLATE_WALL);}
                else{
                    var floor=next==y?Blocks.POLISHED_DEEPSLATE.getDefaultState():Blocks.POLISHED_DEEPSLATE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING,next>y?net.minecraft.util.math.Direction.EAST:net.minecraft.util.math.Direction.WEST);
                    w.setBlockState(new BlockPos(s.x+x,base,s.z+z),floor,Block.NOTIFY_ALL);
                }
            }
        }
        // Footing under the surface platform: no floating station on a sloping mountain.
        for(int x=-6;x<=9;x++)for(int z=64;z<=77;z++)for(int y=s.entryY-1;y>=s.entryY-16;y--){
            if(x>=-3&&x<=3&&z>=67&&z<=73)continue;
            var p=new BlockPos(s.x+x,y,s.z+z);if(w.getBlockState(p).isSolidBlock(w,p))break;
            set(w,s,x,y,z,Blocks.DEEPSLATE_BRICKS);
        }
    }
}
