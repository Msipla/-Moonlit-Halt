package dev.mispla.abyss;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class EnvironmentClient implements ClientModInitializer {
    private static RenderProfile selected=RenderProfile.A,from=RenderProfile.A,current=RenderProfile.A;
    private static long changed,writes;
    private static final int[] nativeTable=new int[16],displayTable=new int[16];
    private static float measuredStart,measuredEnd;
    private static float[] measuredFog=new float[4];
    @Override public void onInitializeClient() {
        SpaceLighting.initialize();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->{
            var root=literal("abyssrender").executes(c->{c.getSource().sendFeedback(Text.literal("A vanilla | B natural | C silhouette; current="+selectedName()));return 1;});
            for(String name:new String[]{"a","b","c","veil","blue","copper","blue-clear","blue-no-field"})root.then(literal(name).executes(c->{select(name);c.getSource().sendFeedback(Text.literal("[Abyss render] "+name+" / study only / world light unchanged"));return 1;}));
            root.then(literal("reload").executes(c->{SpaceLighting.reload();c.getSource().sendFeedback(Text.literal("[Abyss visuals] reload requested; check config status/log"));return 1;}));
            dispatcher.register(root);
        });
        WorldRenderEvents.START.register(c->update());
        WorldRenderEvents.AFTER_ENTITIES.register(GlowMarkers::render);
    }
    public static void select(String name) {
        if(SpaceLighting.select(name)){selected=RenderProfile.A;current=selected;System.out.println("ABYSS_SPACE_PROFILE "+name);return;}
        var next=RenderProfile.named(name);if(next==null)throw new IllegalArgumentException(name);SpaceLighting.disable();
        update();from=current;selected=next;changed=System.nanoTime();if(next==RenderProfile.A)current=next;
        System.out.println("ABYSS_RENDER_PROFILE "+name);
    }
    private static void update() {
        if(selected==RenderProfile.A){current=selected;return;}
        float t=Math.min(1,(System.nanoTime()-changed)/800_000_000f);current=RenderProfile.blend(from,selected,t*t*(3-2*t));
    }
    public static boolean studyActive(){var w=MinecraftClient.getInstance().world;return w!=null&&w.getRegistryKey().equals(GlimmeringAbyss.STUDY);}
    public static boolean customActive(){return studyActive()&&selected!=RenderProfile.A;}
    public static RenderProfile profile(){return current;}
    public static String selectedName(){return SpaceLighting.selectedName()!=null?SpaceLighting.selectedName():selected.name();}
    public static int lightmapColor(int packed,int block,int sky) {
        int result=customActive()?current.lightmapColor(packed,block):packed;
        if(sky==0){nativeTable[block]=packed;displayTable[block]=result;writes++;}return result;
    }
    public static void applyDistance(){RenderSystem.setShaderFogStart(current.start());RenderSystem.setShaderFogEnd(current.end());RenderSystem.setShaderFogColor(current.farR(),current.farG(),current.farB(),current.maxMix());}
    public static void recordTerrainDistance(){measuredStart=RenderSystem.getShaderFogStart();measuredEnd=RenderSystem.getShaderFogEnd();measuredFog=RenderSystem.getShaderFogColor().clone();}
    private static JsonArray rgb(int value){var a=new JsonArray();a.add(value&255);a.add(value>>>8&255);a.add(value>>>16&255);return a;}
    public static JsonObject measurements() {
        var r=new JsonObject();r.addProperty("profile",selectedName());r.add("space_lighting",SpaceLighting.measurements());r.addProperty("custom_active",customActive());
        r.addProperty("world",MinecraftClient.getInstance().world.getRegistryKey().getValue().toString());r.addProperty("observed_lightmap_writes",writes);
        r.add("native_lightmap_0_rgb",rgb(nativeTable[0]));r.add("display_lightmap_0_rgb",rgb(displayTable[0]));
        r.add("native_lightmap_5_rgb",rgb(nativeTable[5]));r.add("display_lightmap_5_rgb",rgb(displayTable[5]));
        var n=new JsonArray();var d=new JsonArray();for(int i=0;i<16;i++){n.add(rgb(nativeTable[i]));d.add(rgb(displayTable[i]));}r.add("native_lightmap_rgb",n);r.add("display_lightmap_rgb",d);
        r.addProperty("distance_start",measuredStart);r.addProperty("distance_end",measuredEnd);var fog=new JsonArray();for(float v:measuredFog)fog.add(v);r.add("distance_target_rgba",fog);
        r.addProperty("surface_microglow",customActive()?current.glow():0);r.addProperty("glow_faces_drawn_last_frame",GlowMarkers.facesDrawn);return r;
    }
}
