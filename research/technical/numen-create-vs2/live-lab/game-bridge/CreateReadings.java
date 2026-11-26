package lab;

import com.google.gson.Gson;
import com.dwinovo.numen.api.sdk.Pending;
import com.dwinovo.numen.script.ApiError;
import com.dwinovo.numen.script.ErrorKind;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.class_2168;
import net.minecraft.class_2259;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_2561;
import net.minecraft.class_2680;
import net.minecraft.class_2769;
import net.minecraft.class_2960;
import net.minecraft.class_3218;
import net.minecraft.class_7923;

final class CreateReadings {
    private static final Gson JSON = new Gson();

    static final int SETTLE_TICKS=4;
    private static final List<TickRead> READS=new ArrayList<>();
    private record TickRead(class_3218 level,long due,Runnable read) {}

    static void installSettling() {
        ServerTickEvents.END_WORLD_TICK.register(level->{
            var due=new ArrayList<TickRead>();
            READS.removeIf(read->{
                if(read.level()!=level || read.due()>level.method_8510()) return false;
                due.add(read);
                return true;
            });
            for(var read:due) read.read().run();
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED
            .register(server->READS.clear());
    }

    static <R> Pending<R> afterTicks(class_3218 level,Supplier<R> read) {
        var pending=Pending.<R>create();
        READS.add(new TickRead(level,level.method_8510()+SETTLE_TICKS,()->{
            R value;
            try { value=read.get(); }
            catch(RuntimeException error) {
                try {
                    pending.fail(error instanceof ApiError api ? api : new ApiError(
                        ErrorKind.BAD_ARGUMENT,"power read unavailable after write: "+error.getMessage(),null));
                } catch(IllegalStateException cancelled) {
                    // The Numen script may already have been interrupted.
                }
                return;
            }
            try { pending.complete(value); }
            catch(IllegalStateException cancelled) {
                // The Numen script may already have been interrupted.
            }
        }));
        return pending;
    }

    public record Power(class_2338 pos,String status,Optional<Double> rpm,Optional<Double> raw_rpm,
                        Optional<String> network) {}

    static Power power(class_3218 level,class_2338 at) {
        if(!(level.method_8321(at) instanceof KineticBlockEntity kinetic))
            return new Power(at,"no_device",Optional.empty(),Optional.empty(),Optional.empty());
        String status=powerStatus(level,at);
        return new Power(at,status,Optional.of(status.equals("running")?(double)kinetic.getSpeed():0.0),
            Optional.of((double)kinetic.getTheoreticalSpeed()),Optional.ofNullable(kinetic.network).map(Object::toString));
    }

    static String powerStatus(class_3218 level,class_2338 at) {
        return powerStatus(level,at,source->EngineeringSite.Site.inSiteCell(level,source));
    }

    static String powerStatus(class_3218 level,class_2338 at,java.util.function.Predicate<class_2338> allowedSource) {
        if(!(level.method_8321(at) instanceof KineticBlockEntity first)) return "no_device";
        if(first.getTheoreticalSpeed()==0) return "disconnected";
        var visited=new HashSet<class_2338>();
        var current=first;
        var position=at;
        for(int depth=0;depth<32;depth++){
            if(!visited.add(position)) return "stale_source";
            if(current.source==null) {
                if(!current.isSource() || current.network==null) return "stale_source";
                return first.isOverStressed()?"overstressed":"running";
            }
            var source=current.source;
            if(!allowedSource.test(source)) return "source_unverified";
            if(!(level.method_8321(source) instanceof KineticBlockEntity next) ||
                next.getTheoreticalSpeed()==0 || current.network==null || !Objects.equals(current.network,next.network) ||
                !(RotationPropagator.isConnected(current,next) || RotationPropagator.isConnected(next,current)))
                return "stale_source";
            current=next;
            position=source;
        }
        return "source_unverified";
    }

    static List<String> faces(class_3218 level, class_2338 pos, class_2680 state) {
        if (!(state.method_26204() instanceof IRotate rotation)) return List.of();
        var faces = new ArrayList<String>();
        for (var side : class_2350.values()) {
            if (rotation.hasShaftTowards(level, pos, state, side)) faces.add(side.method_10151());
        }
        return faces;
    }

    private static List<Integer> xyz(class_2338 pos) {
        return List.of(pos.method_10263(), pos.method_10264(), pos.method_10260());
    }

    private static Map<String, Object> fieldReading(class_3218 level, class_2338 at,
                                                      KineticBlockEntity kinetic) {
        var result = new LinkedHashMap<String, Object>();
        var state = level.method_8320(at);
        result.put("at", xyz(at));
        result.put("faces", faces(level, at, state));
        result.put("rpm", kinetic.getSpeed());
        if (kinetic.network != null) result.put("network", kinetic.network.toString());
        if (kinetic.source != null) result.put("source", xyz(kinetic.source));
        if (kinetic instanceof EncasedFanBlockEntity fan && fan.getAirFlowDirection()!=null) {
            result.put("air", Map.of("front",fan.getAirflowOriginSide().method_10151(),
                "movement",fan.getAirFlowDirection().method_10151(),"range",fan.getAirCurrent().maxDistance));
        }
        if (kinetic.isOverStressed()) result.put("overstressed", true);
        var edges = new ArrayList<List<Integer>>();
        for (var side : class_2350.values()) {
            var neighbor = at.method_10093(side);
            if (!level.method_37118(neighbor)) continue;
            if (level.method_8321(neighbor) instanceof KineticBlockEntity next
                    && (RotationPropagator.isConnected(kinetic, next)
                        || RotationPropagator.isConnected(next, kinetic))) edges.add(xyz(neighbor));
        }
        if (!edges.isEmpty()) result.put("adjacentLinks", edges);
        return result;
    }

    private static List<Map<String, Object>> site(class_3218 level) {
        var observed = new ArrayList<Map<String, Object>>();
        // The launcher enables this fixture only in a new world. It matches the direct runner's fixed scope.
        for (var at : class_2338.method_10094(-7, -60, -7, 7, -49, 7)) {
            if (level.method_37118(at) && level.method_8321(at) instanceof KineticBlockEntity kinetic) {
                observed.add(fieldReading(level, at, kinetic));
            }
        }
        return observed;
    }

    static void registerLabProbe() {
        if (!Boolean.getBoolean("engineering.createLab")) return;
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
            dispatcher.register(LiteralArgumentBuilder.<class_2168>literal("engineering_probe")
                .requires(source -> source.method_9259(4) && source.method_9228() == null)
                .then(LiteralArgumentBuilder.<class_2168>literal("site")
                    .executes(c -> reply(c.getSource(), site(c.getSource().method_9225()))))
                .then(LiteralArgumentBuilder.<class_2168>literal("guide")
                    .then(RequiredArgumentBuilder.<class_2168, String>argument("topic", StringArgumentType.greedyString())
                        .executes(c -> {
                            c.getSource().method_9213(class_2561.method_43470(PonderGuide.query(StringArgumentType.getString(c, "topic"))));
                            return 1;
                        })))
                .then(LiteralArgumentBuilder.<class_2168>literal("schema")
                    .then(RequiredArgumentBuilder.<class_2168, String>argument("ids", StringArgumentType.greedyString())
                        .executes(c -> reply(c.getSource(), schema(StringArgumentType.getString(c, "ids"))))))
                .then(LiteralArgumentBuilder.<class_2168>literal("orient")
                    .then(RequiredArgumentBuilder.<class_2168, String>argument("entries", StringArgumentType.greedyString())
                        .executes(c -> reply(c.getSource(), orientations(c.getSource().method_9225(),
                            StringArgumentType.getString(c, "entries"))))))
                .then(LiteralArgumentBuilder.<class_2168>literal("reorient_hopper")
                    .then(RequiredArgumentBuilder.<class_2168, String>argument("edit", StringArgumentType.greedyString())
                        .executes(c -> reply(c.getSource(), reorientHopper(c.getSource().method_9225(),
                            StringArgumentType.getString(c, "edit"))))))
                .then(LiteralArgumentBuilder.<class_2168>literal("states")
                    .then(RequiredArgumentBuilder.<class_2168, String>argument("states", StringArgumentType.greedyString())
                        .executes(c -> reply(c.getSource(), errors(StringArgumentType.getString(c, "states"))))))));
    }

    private static Map<String, Object> schema(String ids) {
        var result = new LinkedHashMap<String, Object>();
        for (var id : ids.split(" ", 65)) {
            var block = class_7923.field_41175.method_10223(new class_2960(id));
            var properties = new LinkedHashMap<String, List<String>>();
            block.method_9595().method_11659().forEach(p -> properties.put(p.method_11899(), values(p)));
            if (!properties.isEmpty()) result.put(id, properties);
        }
        return result;
    }

    private static <T extends Comparable<T>> List<String> values(class_2769<T> property) {
        return property.method_11898().stream().map(property::method_11901).toList();
    }

    static class_2680 orient(class_3218 level, class_2338 at, String spec, List<String> wanted)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var parsed = class_2259.method_41957(class_7923.field_41175.method_46771(), spec, false);
        var state = parsed.comp_622();
        if (wanted.isEmpty() || faces(level, at, state).containsAll(wanted)) return state;
        var shapes = new java.util.LinkedHashSet<List<String>>();
        for (var candidate : state.method_26204().method_9595().method_11662()) {
            // Auto-orient only properties that the caller left unspecified.
            var allowed = state.method_11656().entrySet().stream().allMatch(e ->
                (List.of("axis", "facing").contains(e.getKey().method_11899())
                    && !parsed.comp_623().containsKey(e.getKey()))
                || e.getValue().equals(candidate.method_11656().get(e.getKey())));
            if (!allowed) continue;
            var have=faces(level, at, candidate);
            if (have.containsAll(wanted)) return candidate;
            shapes.add(have);
        }
        throw new IllegalArgumentException(spec+" cannot expose "+wanted+"; actual possible shaft faces: "+shapes);
    }

    static boolean matchesOrientation(class_2680 expected,class_2680 actual) {
        return expected.method_11656().entrySet().stream()
            .filter(e -> List.of("axis","facing").contains(e.getKey().method_11899()))
            .allMatch(e -> e.getValue().equals(actual.method_11656().get(e.getKey())));
    }

    static String commandState(class_2680 state) {
        var id = class_7923.field_41175.method_10221(state.method_26204()).toString();
        var properties = new StringJoiner(",", "[", "]");
        for (var property : state.method_28501()) properties.add(stateValue(state, property));
        return properties.length()>2 ? id+properties : id;
    }

    private static <T extends Comparable<T>> String stateValue(class_2680 state, class_2769<T> prop) {
        return prop.method_11899()+"="+prop.method_11901(state.method_11654(prop));
    }

    private static List<Map<String, String>> orientations(class_3218 level, String entries) {
        var result = new ArrayList<Map<String, String>>();
        for (var entry : entries.split(" ", 65)) {
            var parts = entry.split("\\|", 3);
            var xyz = parts[0].split(",", 3);
            try {
                var at = new class_2338(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
                if (!inLab(level,at))
                    throw new IllegalArgumentException("orientation outside loaded workspace");
                result.add(Map.of("state", commandState(orient(level, at, parts[1], List.of(parts[2].split(","))))));
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | IllegalArgumentException error) {
                result.add(Map.of("error", error.getMessage()));
            }
        }
        return result;
    }

    private static boolean inLab(class_3218 level,class_2338 at) {
        return Math.abs(at.method_10263())<=7 && Math.abs(at.method_10260())<=7
            && at.method_10264()>=-60 && at.method_10264()<=-49 && level.method_37118(at);
    }

    private static Map<String, Object> reorientHopper(class_3218 level, String edit) {
        var parts=edit.split(" ",4);
        if (parts.length!=4) return Map.of("error","expected a hopper and coordinate");
        try {
            var at=new class_2338(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),Integer.parseInt(parts[2]));
            var hopper=class_7923.field_41175.method_10223(new class_2960("minecraft:hopper"));
            if(!inLab(level,at)) return Map.of("error","hopper cell outside the loaded workspace");
            var before=level.method_8320(at);
            if(before.method_26204()!=hopper) return Map.of("existing",false);
            var next=class_2259.method_41957(class_7923.field_41175.method_46771(),parts[3],false).comp_622();
            if(next.method_26204()!=hopper) return Map.of("error","expected a native hopper state");
            var inventory=level.method_8321(at);
            boolean changed=before!=next && level.method_8652(at,next,3);
            return Map.of("existing",true,"changed",changed,"sameInventory",inventory!=null&&level.method_8321(at)==inventory);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | IllegalArgumentException error) {
            return Map.of("error",error.getMessage());
        }
    }

    private static Map<String, String> errors(String states) {
        var result = new LinkedHashMap<String, String>();
        for (var state : states.split(" ", 65)) {
            try {
                class_2259.method_41957(class_7923.field_41175.method_46771(), state, false);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) {
                result.put(state, error.getRawMessage().getString());
            }
        }
        return result;
    }

    private static int reply(class_2168 source, Object result) {
        source.method_9213(class_2561.method_43470(JSON.toJson(result)));
        return 1;
    }
}
