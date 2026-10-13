package dev.mispla.abyss;

/** Layout 100: a branching fault valley, sheltered settlement and folded west gallery.
 * The plan follows connected fractures; rock shoulders interrupt long sightlines.
 * Historical layout 70/75 geometry is deliberately kept in its own recipes. */
public final class AbyssShape {
    private AbyssShape(){}
    private static double sq(double v){return v*v;}
    // x,z,radius,roof,floor. Narrow necks separate the distant vaults.
    private static final double[][] SPINE={{0,64,36,90,-35},{-25,3,72,103,-54},
        {-42,-77,65,96,-55},{-79,-147,39,81,-47},{-78,-222,59,77,-52},{-122,-268,33,58,-39}};
    private static final double[][] WEST={{-56,-102,32,76,-40},{-126,-143,36,68,-35},
        {-167,-187,39,73,-45},{-153,-252,31,57,-32}};
    public record Column(int x,int z,double floor,double roof,double q,double rough){
        public boolean rock(int y){
            if(y<=floor||y>=roof)return true;
            double mid=(roof+floor)*.5,half=(roof-floor)*.5;
            if(q+.12*Math.pow((y-mid)/half,4)>1+rough)return true;
            // Large attached rock shoulders, not a repeated forest of cylinders.
            double fault=x+23+.25*(z+75)+7*Math.sin(z*.024);
            if(z<-56&&z>-143&&fault>0&&fault<24&&y>18+10*Math.sin(z*.032))return true;
            double fold=x+111-.38*(z+178)+6*Math.sin(z*.043);
            if(z<-158&&z>-254&&fold>0&&fold<17&&y>-13)return true;
            // Quiet inhabited terrace narrows at the lip, broadens at its foot.
            int top=38+(z<=-26?1:0)+(z<=-43?1:0);
            double depth=Math.max(0,top-y);
            if(y<=top&&sq((x-62-4*Math.sin(z*.035))/(59+depth*.27))
                +sq((z+11+5*Math.sin(x*.03))/(90+depth*.19))<1)return true;
            return y<=38&&sq((x+49)/(25+depth*.17))+sq((z-35)/(22+depth*.13))<1;
        }
    }
    public static Column column(int x,int z){return column(x,z,ceiling(x,z));}
    public static Column column(int x,int z,int cap){
        double n=Math.sin(x*.034+Math.sin(z*.019))*Math.cos(z*.039)+.25*Math.sin(x*.103-z*.071);
        double[] v={9,-57,45};
        corridor(v,x+4*n,z+3*n,SPINE);corridor(v,x+3*n,z-4*n,WEST);
        // The town sits in a broad side recess, out of the central fracture.
        double q=sq((x-47)/79.0)+sq((z+15)/104.0);
        if(q<v[0]){v[0]=q;v[1]=-46+12*q+3*n;v[2]=103-20*q+4*n;}
        return new Column(x,z,Math.max(-57,v[1]+3*n),Math.min(cap-4,v[2]+3*n),v[0],.05*n);
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
