package dev.mispla.abyss;

import com.google.gson.*;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import java.nio.file.*;
import java.util.*;

/** Opt-in, study-dimension solid/cutout shader. No vanilla resource is replaced. */
public final class SpaceLighting {
    private static final Map<String,SpaceProfile> profiles=new LinkedHashMap<>();
    private static final Map<String,float[]> depthProfiles=new LinkedHashMap<>();
    private static SpaceProfile selected;
    private static ShaderProgram shader;
    private static long draws;
    private static String configStatus="not loaded";
    public static void initialize() {
        reload();
        CoreShaderRegistrationCallback.EVENT.register(context->context.register(
                new Identifier("glimmeringabyss","space_terrain"),VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL,
                program->{shader=program;System.out.println("ABYSS_SPACE_SHADER_READY "+program.getName());}));
    }
    public static void reload() {
        try {
            Path file=FabricLoader.getInstance().getConfigDir().resolve("glimmeringabyss-visuals.json");
            Files.createDirectories(file.getParent());
            if(!Files.exists(file))try(var in=SpaceLighting.class.getResourceAsStream("/assets/glimmeringabyss/visual-profiles.json")){
                if(in==null)throw new IllegalStateException("Missing default profiles");Files.copy(in,file);
            }
            var user=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            try(var in=SpaceLighting.class.getResourceAsStream("/assets/glimmeringabyss/visual-profiles.json")) {
                var defaults=JsonParser.parseString(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                boolean migrated=false;
                for(String added:new String[]{"blue95","blue95-fog"})if(!user.getAsJsonObject("profiles").has(added)){
                    user.getAsJsonObject("profiles").add(added,defaults.getAsJsonObject("profiles").get(added));migrated=true;
                }
                if(migrated)Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(user));
            }
            Map<String,SpaceProfile> next=parse(Files.readString(file));
            var nextDepth=new LinkedHashMap<String,float[]>();
            var root=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for(var entry:root.getAsJsonObject("profiles").entrySet()) {
                var o=entry.getValue().getAsJsonObject();
                if(o.has("depth")) {
                    var a=o.getAsJsonArray("depth");if(a.size()!=4)throw new IllegalArgumentException("depth needs top/bottom/max/distance");
                    float[] v={a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat(),a.get(3).getAsFloat()};
                    for(float value:v)if(!Float.isFinite(value))throw new IllegalArgumentException("Nonfinite depth");
                    if(v[0]<=v[1]||v[2]<0||v[2]>.72f||v[3]<0)throw new IllegalArgumentException("Depth range");
                    nextDepth.put(entry.getKey(),v);
                }
            }
            depthProfiles.clear();depthProfiles.putAll(nextDepth);
            String name=selected==null?null:selected.name();profiles.clear();profiles.putAll(next);
            selected=name==null?null:profiles.get(name);configStatus="loaded";
        } catch(Exception e) {
            configStatus="ERROR: "+e.getMessage();System.err.println("ABYSS_SPACE_CONFIG "+configStatus);
            // Retain the last valid configuration. Initial failure leaves the mode unavailable.
        }
    }
    public static Map<String,SpaceProfile> parse(String source) {
        JsonObject root=JsonParser.parseString(source).getAsJsonObject();
        if(root.get("schema").getAsInt()!=1)throw new IllegalArgumentException("Unknown visual schema");
        Map<String,SpaceProfile> result=new LinkedHashMap<>();
        for(var entry:root.getAsJsonObject("profiles").entrySet()) {
            String name=entry.getKey();if(!name.matches("[a-z][a-z0-9_-]{1,31}"))throw new IllegalArgumentException("Profile name");
            JsonObject o=entry.getValue().getAsJsonObject();
            result.put(name,new SpaceProfile(name,vec(o,"color"),number(o,"base"),vec(o,"anchor"),number(o,"radius"),
                    number(o,"radiance"),vec(o,"direction"),vec(o,"airColor"),number(o,"airStart"),number(o,"airEnd"),number(o,"airMax")));
        }
        if(result.isEmpty())throw new IllegalArgumentException("No visual profiles");return result;
    }
    private static float number(JsonObject o,String n){return o.get(n).getAsFloat();}
    private static float[] vec(JsonObject o,String n){var a=o.getAsJsonArray(n);if(a.size()!=3)throw new IllegalArgumentException(n);return new float[]{a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()};}
    public static boolean select(String name){SpaceProfile p=profiles.get(name);if(p==null)return false;selected=p;return true;}
    public static void disable(){selected=null;}
    public static String selectedName(){return selected==null?null:selected.name();}
    private static SpaceProfile effective(){return JourneyClient.origin()!=null?profiles.get("blue"):selected;}
    public static boolean active(){
        MinecraftClient c=MinecraftClient.getInstance();return effective()!=null&&shader!=null&&(EnvironmentClient.studyActive()||JourneyClient.origin()!=null)
                &&c.gameRenderer.getCamera().getSubmersionType()==CameraSubmersionType.NONE;
    }
    public static ShaderProgram terrain(float cutoff) {
        if(!active())return null;
        SpaceProfile p=effective();var camera=MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        var origin=JourneyClient.origin();
        shader.getUniformOrDefault("EnvCamera").set((float)(camera.x-(origin==null?0:origin.getX())),(float)(camera.y-(origin==null?0:origin.getY())),(float)(camera.z-(origin==null?0:origin.getZ())));
        shader.getUniformOrDefault("EnvScoped").set(origin==null?0f:1f);
        vecUniform("EnvColor",p.color());vecUniform("EnvDirection",p.direction());vecUniform("EnvAirColor",p.airColor());
        shader.getUniformOrDefault("EnvBase").set(p.base());shader.getUniformOrDefault("EnvRadiance").set(p.radiance());
        float[] a=p.anchor();shader.getUniformOrDefault("EnvAnchor").set(a[0],a[1],a[2],p.radius());
        shader.getUniformOrDefault("EnvAirRange").set(p.airStart(),p.airEnd());shader.getUniformOrDefault("EnvAirMax").set(p.airMax());
        float[] depth=depthProfiles.getOrDefault(p.name(),new float[]{12,-58,0,28});
        shader.getUniformOrDefault("EnvDepth").set(depth[0],depth[1],depth[2],depth[3]);
        shader.getUniformOrDefault("AlphaCutoff").set(cutoff);draws++;return shader;
    }
    private static void vecUniform(String name,float[] v){shader.getUniformOrDefault(name).set(v[0],v[1],v[2]);}
    public static JsonObject measurements() {
        var o=new JsonObject();o.addProperty("profile",selectedName());o.addProperty("active",active());
        o.addProperty("shader_ready",shader!=null);o.addProperty("terrain_shader_selections",draws);o.addProperty("config_status",configStatus);
        if(selected!=null){o.add("parameters",new Gson().toJsonTree(selected));o.add("depth_parameters",new Gson().toJsonTree(depthProfiles.getOrDefault(selected.name(),new float[]{12,-58,0,28})));}return o;
    }
}
