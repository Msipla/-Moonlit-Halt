package lab;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_2487;
import net.minecraft.class_2507;

final class PonderGuide {
    private static final Gson JSON = new Gson();
    private static final Pattern RECIPE = Pattern.compile("[a-z_]+/[a-z0-9_]+(?:/[a-z0-9_]+)*");
    private static final Map<String,String> ALIASES = Map.of("fan", "fan/processing", "chute", "chute/smart");
    private static final java.util.Map<String,String> PORT_NOTES = java.util.Map.of(
        "chute", "Chutes move items downward only; they never lift. A chute under a machine collects its output and a chute above a machine feeds its top. For a multi-stage chain place the next machine below the previous output.",
        "smart_chute", "Smart chutes filter and move items downward only; they never lift. Place the next machine below the previous output for a gravity-fed chain.",
        "millstone", "Measured in game 2026-11-22. Power: a vertical shaft (axis=y) directly below the millstone; motor -> shaft -> millstone read 16 RPM on one network. Output: items arrive in a chute/barrel directly below when that cell is free (3 fed items measured); item capability is exposed on every face, so route output from a free face when the bottom cell carries the shaft. For a two-stage chain drive both millstones from a side cogwheel so their bottoms stay free, and put the next millstone below the output chute; chutes never lift items. Measured 2026-11-22: a cogwheel[axis=y] on the millstone side is driven by a vertical shaft directly under that cogwheel (motor -> shaft[axis=y] -> cogwheel[axis=y]); power the cogwheel along its own axis, not from a shaft crossing its face. Verified 2026-11-22 without a model: two millstones stacked vertically as millstone -> chute -> millstone -> chute -> barrel, each driven by a side cogwheel[axis=y] whose vertical shaft sits directly below it, with the two power columns on opposite sides, turned 3 fed cobblestone into 3 flint in one barrel and 3 more on a second feed. For other blocks call engineering.site.ports().");
    private static final String SOURCE = "Create Fabric 6.0.8.1 build 1744 Ponder scene scripts";
    private static final String ASSET_ROOT = "assets/create/ponder/";
    private static final List<String> CASES = List.of("millstone", "mechanical_press/pressing",
        "mechanical_press/compacting", "fan/processing", "chute/smart", "mechanical_mixer/mixing");
    private static final List<String> ANCHORS = List.of("millstone", "mechanical_press",
        "mechanical_mixer", "encased_fan", "chute", "smart_chute");
    private static final Set<String> DEVICES = Set.of("millstone", "mechanical_press", "mechanical_mixer",
        "encased_fan", "chute", "smart_chute", "funnel", "andesite_funnel", "brass_funnel",
        "andesite_belt_funnel", "brass_belt_funnel", "shaft", "cogwheel", "large_cogwheel", "gearbox",
        "creative_motor", "depot", "basin", "belt", "barrel");

    private record Step(int tick, String text) {}
    private record Scene(String id, String title, String script, String asset, List<Step> text, int demoSpeeds) {}
    private record Device(String id, List<Integer> pos, Map<String,String> properties) {}

    private static Map<String,Map<String,Object>> loadCases() {
        var installed = FabricLoader.getInstance().getModContainer("create").orElseThrow();
        var cases = new LinkedHashMap<String,Map<String,Object>>();
        for (var name : CASES) {
            var asset = ASSET_ROOT + name + ".nbt";
            var file = installed.findPath(asset);
            if (file.isEmpty()) continue;
            try (var stream = Files.newInputStream(file.get())) {
                var sample = readCase(name, class_2507.method_10629(stream));
                if (!sample.isEmpty()) cases.put(name, sample);
            } catch (IOException | RuntimeException error) {
                System.err.println("[Numen Engineering] Could not read Create Ponder case " + asset + ": " + error);
            }
        }
        return Map.copyOf(cases);
    }

    private static Map<String,Object> readCase(String name, class_2487 structure) {
        var palette = structure.method_10554("palette", 10);
        var blocks = structure.method_10554("blocks", 10);
        var devices = new ArrayList<Device>();
        for (int i = 0; i < blocks.size(); i++) {
            var block = blocks.method_10602(i);
            var state = palette.method_10602(block.method_10550("state"));
            var id = state.method_10558("Name");
            if (!id.equals("minecraft:barrel") && !(id.startsWith("create:") &&
                DEVICES.stream().anyMatch(id::endsWith))) continue;
            var properties = new LinkedHashMap<String,String>();
            var nbt = state.method_10562("Properties");
            for (var key : nbt.method_10541()) properties.put(key, nbt.method_10558(key));
            devices.add(new Device(id, vector(block, "pos"), properties));
        }
        if (devices.isEmpty()) return Map.of();
        var anchor = ANCHORS.stream().flatMap(id -> devices.stream().filter(device -> device.id.equals("create:" + id)))
            .findFirst().orElse(devices.get(0));
        var compact = devices.stream().sorted(Comparator.comparingInt((Device device) -> device.pos.get(1))
            .thenComparingInt(device -> device.pos.get(0)).thenComparingInt(device -> device.pos.get(2)))
            .limit(24).map(device -> {
                var row = new LinkedHashMap<String,Object>();
                row.put("p", List.of(device.pos.get(0) - anchor.pos.get(0),
                    device.pos.get(1) - anchor.pos.get(1), device.pos.get(2) - anchor.pos.get(2)));
                row.put("id", device.id);
                if (!device.properties.isEmpty()) row.put("props", device.properties);
                return row;
            }).toList();
        return Map.of("scene", name, "anchor", anchor.id, "size", vector(structure, "size"),
            "devices", compact, "source", "Installed Create: " + ASSET_ROOT + name + ".nbt");
    }

    private static List<Integer> vector(class_2487 nbt, String key) {
        var pos = nbt.method_10554(key, 3);
        return List.of(pos.method_10600(0), pos.method_10600(1), pos.method_10600(2));
    }

    private static class Index {
        static final List<Scene> SCENES = load();
        static final Map<String,Map<String,Object>> CASES = loadCases();

        private static List<Scene> load() {
            var scenes = new ArrayList<Scene>();
            try (var stream = PonderGuide.class.getResourceAsStream("/engineering/ponder/scenes.jsonl")) {
                if (stream == null) throw new IOException("missing packaged index");
                try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    for (String line; (line = reader.readLine()) != null;) {
                        var row = JsonParser.parseString(line).getAsJsonObject();
                        var text = new ArrayList<Step>();
                        int tick = 0;
                        int demoSpeeds = 0;
                        for (var entry : row.getAsJsonArray("steps")) {
                            var step = entry.getAsJsonObject();
                            switch (string(step, "kind")) {
                                case "idle" -> tick += step.get("ticks").getAsInt();
                                case "text" -> {
                                    var value = string(step, "text");
                                    if (!value.isBlank()) text.add(new Step(tick, value));
                                }
                                case "simulated_speed" -> demoSpeeds++;
                                default -> { }
                            }
                        }
                        var asset = string(row, "nbt");
                        scenes.add(new Scene(string(row, "id"), string(row, "title"),
                            string(row, "class") + "." + string(row, "method"),
                            asset.isEmpty() ? "" : ASSET_ROOT + asset, List.copyOf(text), demoSpeeds));
                    }
                }
            } catch (IOException error) {
                throw new java.io.UncheckedIOException(error);
            }
            return List.copyOf(scenes);
        }
    }

    static String query(String input) {
        var query = input.strip().toLowerCase(Locale.ROOT);
        if (query.isEmpty() || query.length() > 96) return JSON.toJson(Map.of("error", "expected a scene, keyword or recipe path of 1..96 characters"));
        if (RECIPE.matcher(query).matches()) {
            var entry = "data/create/recipes/" + query + ".json";
            var file = FabricLoader.getInstance().getModContainer("create").orElseThrow().findPath(entry);
            if (file.isPresent()) {
                try (var reader = Files.newBufferedReader(file.get(), StandardCharsets.UTF_8)) {
                    var found = JsonParser.parseReader(reader).getAsJsonObject();
                    found.addProperty("source", entry);
                    found.addProperty("scope", "bundled Create recipe; active datapacks may override it");
                    if (found.toString().length() <= 5500) return JSON.toJson(found);
                    return JSON.toJson(Map.of("source", entry, "truncated", true, "error", "recipe exceeds the tool response limit"));
                } catch (IOException error) {
                    return JSON.toJson(Map.of("error", "could not read installed recipe", "source", entry));
                }
            }
        }
        var name = query.startsWith("create:") ? query.substring(7) : query;
        var normalized = name.replace(' ', '_');
        var key = ALIASES.getOrDefault(normalized,normalized);
        var matches = Index.SCENES.stream().filter(scene -> score(scene, key) > 0)
            .sorted(Comparator.comparingInt((Scene scene) -> score(scene, key)).reversed().thenComparing(Scene::id))
            .toList();
        var exact = matches.stream().filter(scene -> score(scene, key) == 4).findFirst();
        if (exact.isPresent()) {
            var scene = exact.get();
            var installed = FabricLoader.getInstance().getModContainer("create").orElseThrow();
            var assets = !scene.asset.isEmpty() && installed.findPath(scene.asset).isPresent()
                ? List.of(scene.asset) : List.of();
            var text = scene.text.stream().limit(8).map(step -> Map.of("tick", step.tick, "text",
                step.text.substring(0, Math.min(180, step.text.length())),
                "truncated", step.text.length() > 180)).toList();
            var result = new LinkedHashMap<String,Object>();
            result.put("topic", scene.id);
            result.put("script", scene.script);
            result.put("title", scene.title);
            result.put("assets", assets);
            result.put("steps", text);
            result.put("matched", scene.text.size());
            result.put("returned", text.size());
            result.put("truncated", text.size() < scene.text.size() || scene.text.stream().anyMatch(step -> step.text.length() > 180));
            result.put("source", SOURCE);
            result.put("note", "Teaching, not live physics. Asset matches are candidates; " + scene.demoSpeeds + " simulated speed changes omitted.");
            var caseKey=scene.asset.replace(ASSET_ROOT,"").replace(".nbt","");
            var demo=Index.CASES.get(caseKey);
            if(demo!=null) result.put("case", demo);
            var ports=PORT_NOTES.get(scene.id);
            if(ports==null) for(var entry:PORT_NOTES.entrySet()) if(scene.id.contains(entry.getKey())||entry.getKey().contains(scene.id)) { ports=entry.getValue(); break; }
            if(ports!=null) result.put("device_ports", ports);
            return JSON.toJson(result);
        }
        var hits = matches.stream().limit(5).map(scene ->
            Map.of("id", scene.id, "title", scene.title, "script", scene.script)).toList();
        return JSON.toJson(Map.of("topic", query, "hits", hits, "source", SOURCE,
            "matched", matches.size(), "returned", hits.size(), "truncated", matches.size() > hits.size()));
    }

    private static int score(Scene scene, String key) {
        if (scene.id.equals(key) || (!scene.asset.isEmpty() && scene.asset.equals("assets/create/ponder/" + key + ".nbt"))) return 4;
        if (scene.id.contains(key)) return 3;
        if (scene.title.toLowerCase(Locale.ROOT).contains(key.replace('_', ' '))) return 2;
        return scene.text.stream().anyMatch(step -> step.text.toLowerCase(Locale.ROOT).contains(key.replace('_', ' '))) ? 1 : 0;
    }

    private static String string(JsonObject object, String key) {
        var value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
