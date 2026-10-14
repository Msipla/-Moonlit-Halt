package dev.mispla.abyss;

/** Layout 100: a branching fault valley, sheltered settlement and folded west gallery.
 * The plan follows connected fractures; rock shoulders interrupt long sightlines.
 * Historical layout 70/75 geometry is deliberately kept in its own recipes. */
public final class AbyssShape {
    private AbyssShape(){}
    private static double sq(double v){return v*v;}
    private static double smooth(double v){double t=Math.max(0,Math.min(1,v));return t*t*(3-2*t);}
    // x,z,radius,roof,floor. Narrow necks separate the distant vaults.
    private static final double[][] SPINE={{0,64,36,90,-35},{-25,3,72,103,-54},
        {-42,-77,65,96,-55},{-79,-147,39,81,-47},{-78,-222,59,77,-52},{-122,-268,33,58,-39}};
    private static final double[][] WEST={{-56,-102,32,76,-40},{-126,-143,36,68,-35},
        {-167,-187,39,73,-45},{-153,-252,31,57,-32}};
    private static final double[][] LEDGES={{47,40,-60},{47,40,-84},{22,37,-84},{22,37,-100},{-28,31,-100},{-28,31,-124},{-78,27,-124},{-78,25,-148},{-126,24,-148},{-126,20,-184},{-151,20,-184},{-151,16,-214},{-90,16,-214},{-90,22,-176},{-66,22,-176},{-66,27,-124},{-78,27,-124}};
    public record Column(int x,int z,double floor,double roof,double q,double rough,double ledgeDistance,double ledgeTop,int layout,double wallWeight,double terraceTop){
        public boolean rock(int y){
            if(y<=floor)return true;
            double localRoof=roof,boundary=q;
            // Only the east wall changes. Cache the column weight and keep the
            // historical recipe's hot path free of new geological calculations.
            if(wallWeight>0&&layout==101){
                localRoof-=wallWeight*(4+3*Math.sin(z*.055+x*.028)+4*Math.exp(-sq((z+28)/28.0)));
                double bedding=y+.27*z+.14*x;
                double recess=13*Math.exp(-sq((bedding-54)/12.0))+9*Math.exp(-sq((bedding-17)/8.0));
                double shoulder=10*Math.exp(-sq((z-22)/22.0))*Math.exp(-sq((y-58)/20.0));
                double sway=5*Math.sin(z*.044+y*.017)+3*Math.sin(y*.07-z*.035);
                boundary-=wallWeight*(recess-shoulder+sway)/43;
                // Recesses end in solid rock before the unchanged footprint edge.
                boundary+=wallWeight*.60*smooth((x-109)/17.0);
                double side=smooth((x-50)/28.0)*(1-smooth((Math.abs(z-66)-44)/32.0));
                localRoof-=side*(5*Math.exp(-sq((z-65)/18.0))+3*Math.sin(x*.079-z*.047));
                boundary+=side*(11*Math.exp(-sq((z-66)/20.0))*Math.exp(-sq((y-63)/24.0))
                    -8*Math.exp(-sq((z-38)/16.0))*Math.exp(-sq((y-49)/13.0)))/43;
            }
            if(wallWeight>0&&layout>=102){
                // Finite, oblique breaks with different lengths and depths, rather
                // than a repeated height contour extruded around the east wall.
                double broad=Terrain.noise((z+.24*y)/23.0,y/19.0,41021);
                double broken=Terrain.noise((z-.38*y)/8.0,(y+.19*z)/11.0,41022);
                double grain=Terrain.noise((z+.5*y)/4.7,y/5.3,41023);
                double bedding=y+.32*z+.10*x-3*Terrain.noise(z/31.0,x/24.0,41024);
                double recess=12*Math.exp(-sq((bedding-48)/8.0))*Math.exp(-sq((z+31)/57.0));
                double shoulder=15*Math.exp(-sq((z-42+.22*y)/23.0))*Math.exp(-sq((y-56)/26.0));
                boundary-=wallWeight*(7*broad+4*broken+1.8*grain+recess-shoulder)/43;
                boundary+=wallWeight*.65*smooth((x-111)/15.0);
                // Carve downward into the cover, never raise the historical roof.
                // Rock noses and roof pockets blend in three dimensions below it.
                double roofBreak=8+6*Terrain.noise((x+.3*z)/18.0,z/21.0,41025)
                    +3*Terrain.noise(x/7.0,(z-.2*x)/9.0,41026);
                localRoof-=wallWeight*Math.max(1,roofBreak);
            }
            if(layout>=103&&!(x>=-3&&x<=28&&z>=55&&z<=74&&y>=36)){
                // The old q is a plan-view section extruded through most of the
                // height. Break only its exposed skin, with finite oblique erosion
                // at three scales. Keep the occupied shelf, transit clearance,
                // floor and cover intact.
                if(q>.52&&q<1.45){
                    double broad=Terrain.noise((x+.31*y)/26.0,(z-.23*y)/31.0,41040);
                    double joint=Terrain.noise((x-.46*y)/10.0,(z+.37*y)/13.0,41041);
                    double grain=Terrain.noise((x+.61*y)/4.0,(z-.51*y)/5.0,41042);
                    boundary+=.13*broad+.075*joint+.025*grain;
                }
                localRoof-=Math.max(0,3+3*Terrain.noise(x/17.0,z/21.0,41043)
                    +1.5*Terrain.noise((x+.2*z)/7.0,z/9.0,41044));
            }
            if(y>=localRoof)return true;
            double mid=(localRoof+floor)*.5,half=(localRoof-floor)*.5;
            if(boundary+.12*Math.pow((y-mid)/half,4)>1+rough)return true;
            // Occupied shelves widen into talus and merge with the native floor below.
            if(y<=ledgeTop&&ledgeDistance<7+(ledgeTop-y)*.55)return true;
            // Large attached rock shoulders, not a repeated forest of cylinders.
            double fault=x+23+.25*(z+75)+7*Math.sin(z*.024+y*.031)+4*Math.sin(y*.068);
            double width=(17+4*Math.sin(y*.055+z*.027)+Math.max(0,y-50)*.15)*Math.max(0,Math.sin(Math.PI*(z+143)/87));
            if(layout>=103){
                fault+=5*Terrain.noise((z+.35*y)/16.0,y/22.0,41045)
                    +2*Terrain.noise((z-.47*y)/5.5,y/7.0,41050);
                width+=3.5*Terrain.noise((z+.27*y)/12.0,y/13.0,41051)*Math.max(0,Math.sin(Math.PI*(z+143)/87));
            }
            if(z<-56&&z>-143&&fault>0&&fault<width&&y>18+10*Math.sin(z*.032))return true;
            double fold=x+111-.38*(z+178)+6*Math.sin(z*.043+y*.04)+3*Math.sin(y*.08);
            double foldWidth=15+3*Math.sin(y*.047);
            if(layout>=103){
                fold+=5*Terrain.noise((z-.25*y)/16.0,y/19.0,41046)
                    +2*Terrain.noise((z+.41*y)/6.0,y/8.0,41052);
                foldWidth+=3.5*Terrain.noise((z+.36*y)/15.0,y/11.0,41053);
            }
            if(z<-158&&z>-254&&fold>0&&fold<foldWidth&&y>-13)return true;
            // Quiet inhabited terrace narrows at the lip, broadens at its foot.
            double depth=Math.max(0,terraceTop-y);
            double skin=layout>=103&&depth>3
                ?smooth((depth-3)/12.0)*(3.5*Terrain.noise((z+.32*y)/13.0,y/16.0,41047)
                    +1.5*Terrain.noise((x-.42*y)/5.0,z/7.0,41048)):0;
            if(y<=terraceTop&&sq((x-62-4*Math.sin(z*.035))/(59+depth*.27))
                +sq((z+11+5*Math.sin(x*.03))/(90+depth*.19))<1+skin/45)return true;
            return y<=38&&sq((x+49)/(25+depth*.17))+sq((z-35)/(22+depth*.13))<1+skin/35;
        }
    }
    public static Column column(int x,int z){return column(x,z,ceiling(x,z));}
    public static Column column(int x,int z,int cap){return column(x,z,cap,100);}
    public static Column column(int x,int z,int cap,int layout){
        double n=Math.sin(x*.034+Math.sin(z*.019))*Math.cos(z*.039)+.25*Math.sin(x*.103-z*.071);
        double[] v={9,-57,45};
        corridor(v,x+4*n,z+3*n,SPINE);corridor(v,x+3*n,z-4*n,WEST);
        // The town sits in a broad side recess, out of the central fracture.
        double q=sq((x-47)/79.0)+sq((z+15)/104.0);
        if(q<v[0]){v[0]=q;v[1]=-46+12*q+3*n;v[2]=103-20*q+4*n;}
        double distance=1e9,ledgeTop=0;
        double wx=x+2*Math.sin(z*.06),wz=z+2*Math.sin(x*.05);
        for(int i=1;i<LEDGES.length;i++){
            var a=LEDGES[i-1];var b=LEDGES[i];double dx=b[0]-a[0],dz=b[2]-a[2];
            double t=Math.max(0,Math.min(1,((wx-a[0])*dx+(wz-a[2])*dz)/(dx*dx+dz*dz)));
            double d=Math.hypot(wx-a[0]-dx*t,wz-a[2]-dz*t);
            if(d<distance){distance=d;ledgeTop=a[1]+(b[1]-a[1])*t-2+Math.sin(x*.06+z*.07);}
        }
        double weight=layout>=101?Math.max(smooth((x-72)/40.0)*(1-smooth((Math.abs(z+10)-106)/34.0)),smooth((x-50)/28.0)*(1-smooth((Math.abs(z-66)-44)/32.0))):0;
        double geologicalCap=cap;
        if(weight>0&&x>111){
            int innerCap=CavernShape.ceiling(111,z);
            geologicalCap=Math.min(cap,innerCap+(cap-innerCap)*smooth((x-111)/16.0));
        }
        if(layout>=102&&weight>0){
            // Ease the inside of the old covered-town cap into the lower outer
            // rock. The saved cap and native surface remain untouched.
            if(x>70&&x<84&&z>=-106&&z<=73){
                double start=70+2*Terrain.noise(z/23.0,4,41030);
                int outer=CavernShape.ceiling(84,z);
                geologicalCap=Math.min(geologicalCap,cap+(outer-cap)*smooth((x-start)/(84-start)));
            }
            if(z>58&&z<74&&x>=50&&x<=83){
                double start=58+2*Terrain.noise(x/22.0,7,41031);
                int outer=CavernShape.ceiling(x,74);
                geologicalCap=Math.min(geologicalCap,cap+(outer-cap)*smooth((z-start)/(74-start)));
            }
        }
        double terraceTop=38+(z<=-26?1:0)+(z<=-43?1:0);
        if(layout>=102&&x>81&&z>-76&&z<53){
            // Leave living rock in the gaps between the relocated additions.
            // Sparse house footings and paths override only their own footprints.
            double relief=5*Math.exp(-sq((x-86)/10.0)-sq((z+47)/15.0))
                +7*Math.exp(-sq((x-91)/9.0)-sq((z-3)/13.0))
                +4*Math.exp(-sq((x-87)/11.0)-sq((z-39)/12.0));
            terraceTop+=smooth((x-81)/6.0)*(relief+1.5*Terrain.noise(x/8.0,z/10.0,41027));
        }
        return new Column(x,z,Math.max(-57,v[1]+3*n),Math.min(geologicalCap-4,v[2]+3*n),v[0],.05*n,distance,ledgeTop,layout,weight,terraceTop);
    }
    private static void corridor(double[] best,double x,double z,double[][] points){
        for(int i=1;i<points.length;i++){
            var a=points[i-1];var b=points[i];double dx=b[0]-a[0],dz=b[1]-a[1];
            double t=Math.max(0,Math.min(1,((x-a[0])*dx+(z-a[1])*dz)/(dx*dx+dz*dz)));
            double r=a[2]+(b[2]-a[2])*t,q=(sq(x-a[0]-dx*t)+sq(z-a[1]-dz*t))/(r*r);
            if(q<best[0]){best[0]=q;best[1]=a[4]+(b[4]-a[4])*t+12*q;best[2]=a[3]+(b[3]-a[3])*t-22*q;}
        }
    }
    public static int ceiling(int x,int z){
        if(x>=-112&&x<=111&&z>=-176&&z<=111)return CavernShape.ceiling(x,z);
        // Low remote vaults fit beneath ordinary hill terrain; never remove the native cap.
        double[] v={9,-57,45};corridor(v,x,z,SPINE);corridor(v,x,z,WEST);
        return Math.max(48,Math.min(88,(int)Math.ceil(v[2]+8)));
    }
}
