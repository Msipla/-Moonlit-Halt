package dev.mispla.abyss;

/** Local, continuous rock geometry. Historical Terrain and its calibration scene stay intact. */
public final class CavernShape {
    private static final double[][] PILLARS={{-48,-43,11},{-24,-92,10},{36,-78,12},{69,-32,16},{-72,10,15},{12,7,10},{-5,-38,9}};
    private CavernShape(){}
    private static double sq(double v){return v*v;}
    public record Column(int x,int z,double q,double floor,double roof,double roughness){
        public boolean rock(int y){
            if(x<-108||x>107||z<-172||z>107)return true;
            if(y<=floor||y>=roof)return true;
            // A height-dependent outline avoids extruding a plan-view ellipse into a shaft.
            double wx=x+7*Math.sin(z*.038+y*.035)+4*Math.sin(y*.082+z*.017);
            double wz=z+43+8*Math.sin(x*.035-y*.041);
            double edge=sq(wx/101)+sq(wz/128);
            double arch=sq((y-22)/91.0)*.22;
            boolean air=edge+arch<1+roughness;
            // Overlapping side chambers pinch into the main room instead of a rectangular excavation.
            air|=ellipsoid(x,y,z,-76,25,-72,27,35,42)<1;
            air|=ellipsoid(x,y,z,42,35,-124,37,29,40)<1;
            air|=ellipsoid(x,y,z,-68,38,33,30,30,35)<1;
            air|=ellipsoid(x,y,z,0,60,68,25,26,28)<1;
            if(!air)return true;
            for(int i=0;i<PILLARS.length;i++){
                var p=PILLARS[i];
                double cx=p[0]+2.8*Math.sin(y*.041+i),cz=p[1]+2.5*Math.sin(y*.035+i*1.7);
                double r=p[2]*(.89+.13*Math.sin(y*.065+i)+.045*Math.sin(y*.24+i));
                // Broad roots and ceiling junctions blend the columns into their host rock.
                r+=Math.max(0,(-16-y)*.18)+Math.max(0,(y-69)*.2);
                if(sq((x-cx)/r)+sq((z-cz)/(r*.93))<1)return true;
            }
            // Occupied rock terrace: compact near the houses, widening into irregular talus below.
            int top=38+(z<=-26?1:0)+(z<=-43?1:0);
            double depth=Math.max(0,top-y),rx=60+depth*.34,rz=91+depth*.27;
            double sx=x-62-3*Math.sin(z*.052+y*.053),sz=z+11+4*Math.sin(x*.04+y*.036);
            double shelf=sq(sx/rx)+sq(sz/rz);
            if(y<=top&&shelf<1+.06*Math.sin(z*.19+y*.12))return true;
            // The opposite bridge landing occupies a distinct natural ledge, not steel stilts.
            double ledge=sq((x+49)/(25+depth*.18))+sq((z-35)/(22+depth*.15));
            return y<=38&&ledge<1+.07*Math.sin(y*.16+x*.12);
        }
    }
    public static Column column(int x,int z){
        double q=sq((x+4)/103.0)+sq((z+43)/128.0);
        double n=Math.sin(x*.048+Math.sin(z*.031))*Math.cos(z*.057)+.35*Math.sin(x*.139+z*.081);
        double floor=-48+18*q*q+6*n+5*Math.sin(z*.034-x*.021);
        double roof=101-27*q+7*n;
        // Side chambers retain a solid roof and never touch the generation footprint boundary.
        if(ellipsoid(x,25,z,-76,25,-72,27,35,42)<1)roof=Math.max(roof,61);
        if(ellipsoid(x,35,z,42,35,-124,37,29,40)<1)roof=Math.max(roof,67);
        if(ellipsoid(x,38,z,-68,38,33,30,30,35)<1)roof=Math.max(roof,71);
        if(Math.abs(x)<=25&&Math.abs(z-68)<=28)roof=Math.max(roof,87);
        return new Column(x,z,q,Math.max(-57,floor),Math.min(ceiling(x,z)-4,roof),.065*n);
    }
    public static int ceiling(int x,int z){
        // Keep the already verified Phase 7 cap. New shapes fit underneath it,
        // rather than increasing the mountain requirements or weakening cover.
        int top=(int)Math.ceil(Terrain.column(x,z,Terrain.SEED).roof())+4;
        if(x>=-55&&x<=83&&z>=-106&&z<=73)top=Math.max(top,82);
        return Math.min(111,Math.max(48,top));
    }
    private static double ellipsoid(double x,double y,double z,double cx,double cy,double cz,double rx,double ry,double rz){
        return sq((x-cx)/rx)+sq((y-cy)/ry)+sq((z-cz)/rz);
    }
}
