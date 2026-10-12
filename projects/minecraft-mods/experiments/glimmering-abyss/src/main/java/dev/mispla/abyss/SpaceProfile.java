package dev.mispla.abyss;

/** Scene irradiance parameters; neither world light nor the lightmap is modified. */
public record SpaceProfile(String name, float[] color, float base, float[] anchor,
        float radius, float radiance, float[] direction, float[] airColor,
        float airStart, float airEnd, float airMax) {
    public SpaceProfile {
        vector(color, "color", true); vector(anchor, "anchor", false);
        vector(direction, "direction", false); vector(airColor, "airColor", true);
        if (!Float.isFinite(base) || base<0 || base>1 || !Float.isFinite(radius) || radius<=0
                || !Float.isFinite(radiance) || radiance<0 || radiance>2
                || !Float.isFinite(airStart) || airStart<0 || !Float.isFinite(airEnd) || airEnd<=airStart
                || !Float.isFinite(airMax) || airMax<0 || airMax>.35f)
            throw new IllegalArgumentException("Invalid spatial lighting range: "+name);
        if (direction[0]*direction[0]+direction[1]*direction[1]+direction[2]*direction[2]<.001f)
            throw new IllegalArgumentException("Zero environment direction");
        color=color.clone();anchor=anchor.clone();direction=direction.clone();airColor=airColor.clone();
    }
    private static void vector(float[] values,String label,boolean unit) {
        if(values==null||values.length!=3)throw new IllegalArgumentException(label+" needs RGB/XYZ");
        for(float v:values)if(!Float.isFinite(v)||(unit&&(v<0||v>1)))throw new IllegalArgumentException(label);
    }
    public float field(float x,float y,float z) {
        double dx=(x-anchor[0])/radius,dy=(y-anchor[1])/radius,dz=(z-anchor[2])/radius;
        return (float)Math.exp(-.5*(dx*dx+dy*dy+dz*dz));
    }
    public float airMix(float distance) {
        float t=Math.max(0,Math.min(1,(distance-airStart)/(airEnd-airStart)));
        return t*t*(3-2*t)*airMax;
    }
    public static float nativeLightProtection(float blockLight) {
        float t=Math.max(0,Math.min(1,(blockLight/15f-.3f)/.7f));
        return 1-t*t*(3-2*t);
    }
}
