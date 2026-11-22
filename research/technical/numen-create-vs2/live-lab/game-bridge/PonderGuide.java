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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;

final class PonderGuide {
    private static final Gson JSON = new Gson();
    private static final java.util.Map<String,com.google.gson.JsonObject> CASES = loadCases();
    private static final Pattern RECIPE = Pattern.compile("[a-z_]+/[a-z0-9_]+(?:/[a-z0-9_]+)*");
    private static final Map<String,String> ALIASES = Map.of("fan", "fan/processing", "chute", "chute/smart");
    private static final java.util.Map<String,String> PORT_NOTES = java.util.Map.of(
        "chute", "Chutes move items downward only; they never lift. A chute under a machine collects its output and a chute above a machine feeds its top. For a multi-stage chain place the next machine below the previous output.",
        "smart_chute", "Smart chutes filter and move items downward only; they never lift. Place the next machine below the previous output for a gravity-fed chain.",
        "millstone", "Measured in game 2026-11-22. Power: a vertical shaft (axis=y) directly below the millstone; motor -> shaft -> millstone read 16 RPM on one network. Output: items arrive in a chute/barrel directly below when that cell is free (3 fed items measured); item capability is exposed on every face, so route output from a free face when the bottom cell carries the shaft. For a two-stage chain drive both millstones from a side cogwheel so their bottoms stay free, and put the next millstone below the output chute; chutes never lift items. For other blocks call engineering.site.ports().");
    private static final String SOURCE = "Create Fabric 6.0.8.1 build 1744 Ponder scene scripts";

    private record Step(int tick, String text) {}
    private record Scene(String id, String title, String script, String asset, List<Step> text, int demoSpeeds) {}

    private static java.util.Map<String,com.google.gson.JsonObject> loadCases() {
        var cases = new java.util.LinkedHashMap<String,com.google.gson.JsonObject>();
        try (var stream = PonderGuide.class.getResourceAsStream("/engineering/ponder/cases.json")) {
            if (stream == null) return java.util.Map.of();
            try (var reader = new java.io.InputStreamReader(stream, StandardCharsets.UTF_8)) {
                var root = JsonParser.parseReader(reader).getAsJsonObject();
                for (var entry : root.entrySet()) cases.put(entry.getKey(), entry.getValue().getAsJsonObject());
            }
        } catch (java.io.IOException ignored) {
        }
        return java.util.Map.copyOf(cases);
    }

    private static class Index {
        static final List<Scene> SCENES = load();

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
                            asset.isEmpty() ? "" : "assets/create/ponder/" + asset, List.copyOf(text), demoSpeeds));
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
            var result=new java.util.LinkedHashMap<String,Object>();
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
            var caseKey=scene.asset.replace("assets/create/ponder/","").replace(".nbt","");
            var demo=CASES.get(caseKey);
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
