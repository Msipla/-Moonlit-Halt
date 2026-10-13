package dev.mispla.abyss;

/** One gentle scenic excursion below the shaft; endpoints retain both proven docks. */
public final class CableRoute {
    private CableRoute(){}
    public record Point(double x,double y,double z){}
    private static double smooth(double t){return t*t*(3-2*t);}
    public static Point at(double descent,int upperY,int layout){
        double t=Math.max(0,Math.min(1,descent));
        if(layout<75)return new Point(.5,upperY+(45-upperY)*smooth(t),70.5);
        if(t<.55)return new Point(.5,upperY+(82-upperY)*smooth(t/.55),70.5);
        double u=(t-.55)/.45,s=smooth(u),swing=Math.pow(Math.sin(Math.PI*u),2);
        return new Point(.5-28*swing,82-37*s,70.5-25*swing);
    }
}
