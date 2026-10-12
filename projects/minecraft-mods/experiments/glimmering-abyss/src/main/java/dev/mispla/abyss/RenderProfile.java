package dev.mispla.abyss;

/** Display controls only; never changes world light propagation. */
public record RenderProfile(String name, float floorR, float floorG, float floorB,
        float start, float end, float farR, float farG, float farB, float maxMix, float glow) {
    public static final RenderProfile A = new RenderProfile("a",0,0,0,144,192,0,0,0,1,0);
    public static final RenderProfile B = new RenderProfile("b",.36f,.40f,.44f,24,320,.006f,.009f,.013f,.90f,.48f);
    public static final RenderProfile C = new RenderProfile("c",.36f,.40f,.44f,36,360,.012f,.018f,.026f,.64f,.48f);
    public static final RenderProfile VEIL = new RenderProfile("veil",.58f,.64f,.70f,8,112,.12f,.18f,.22f,1,.48f);
    public static RenderProfile named(String name) {
        return switch(name.toLowerCase(java.util.Locale.ROOT)) {
            case "a","vanilla" -> A; case "b","natural" -> B;
            case "c","silhouette" -> C; case "veil" -> VEIL; default -> null;
        };
    }
    public int lightmapColor(int packed,int light) {
        float w=1-Math.max(0,Math.min(15,light))/15f;
        int r=lift(packed&255,floorR*w),g=lift(packed>>>8&255,floorG*w),b=lift(packed>>>16&255,floorB*w);
        return packed&0xff000000 | b<<16 | g<<8 | r;
    }
    private static int lift(int value,float contribution) {return Math.max(0,Math.min(255,Math.round(value+(255-value)*contribution)));}
    public float distanceMix(float distance) {
        float t=Math.max(0,Math.min(1,(distance-start)/(end-start)));return t*t*(3-2*t)*maxMix;
    }
    public static RenderProfile blend(RenderProfile a,RenderProfile b,float t) {
        return new RenderProfile(b.name,l(a.floorR,b.floorR,t),l(a.floorG,b.floorG,t),l(a.floorB,b.floorB,t),
                l(a.start,b.start,t),l(a.end,b.end,t),l(a.farR,b.farR,t),l(a.farG,b.farG,t),l(a.farB,b.farB,t),l(a.maxMix,b.maxMix,t),l(a.glow,b.glow,t));
    }
    private static float l(float a,float b,float t){return a+(b-a)*t;}
}
