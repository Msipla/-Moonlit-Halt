package local.ship;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.GsonBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.class_1268;
import net.minecraft.class_1799;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3218;
import net.minecraft.class_3222;
import net.minecraft.class_3965;
import net.minecraft.server.MinecraftServer;

/**
 * Task-only ship probe: server-side assembly through the native ShipAssembler,
 * native helm mount through the block interaction, then native control input.
 */
public final class ShipTest implements ClientModInitializer {
    private static final Set<String> HULL = Set.of(
        "minecraft:oak_planks", "minecraft:oak_fence", "vs_eureka:oak_ship_helm",
        "vs_eureka:anchor", "vs_eureka:balloon", "vs_eureka:white_balloon", "vs_eureka:floater");

    private int ticks;
    private int state;
    private int phaseStart;
    private long stamp = -1;
    private class_2338 helm;
    private class_2338 helmShip;
    private Object ship;
    private long shipId = -1;
    private class_3222 pilot;
    private final List<Map<String, Object>> samples = new ArrayList<>();
    private final Map<String, Object> result = new LinkedHashMap<>();

    @Override
    public void onInitializeClient() {
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
    }

    private void tick(MinecraftServer server) {
        if (!Boolean.getBoolean("shiptest.enabled") || state == 99) return;
        ticks++;
        try {
            run(server);
        } catch (Throwable error) {
            result.put("error", String.valueOf(error));
            try { java.io.StringWriter sw = new java.io.StringWriter(); error.printStackTrace(new java.io.PrintWriter(sw)); Files.writeString(server.method_3831().toPath().resolve("shiptest-error.txt"), sw.toString()); } catch (Throwable ignored) {}
            state = 99;
            try { write(server); } catch (Throwable ignored) {}
        }
    }

    private void run(MinecraftServer server) throws Exception {
        Path root = server.method_3831().toPath();
        Path request = root.resolve("shiptest.request");
        if (!Files.exists(request) || server.method_3760().method_14571().isEmpty()) return;
        class_3218 level = server.method_30002();

        if (state == 0) {
            long modified = Files.getLastModifiedTime(request).toMillis();
            if (modified == stamp) return;
            stamp = modified;
            String[] p = Files.readString(request).trim().split("\\s+");
            helm = new class_2338(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
            if (!level.method_8320(helm).method_26204().getClass().getName().contains("ShipHelmBlock"))
                throw new IllegalStateException("not a helm at " + helm);
            pilot = (class_3222) server.method_3760().method_14571().get(0);
            Object assembled = assemble(level, helm);
            if (assembled == null) throw new IllegalStateException("native assembly rejected the hull");
            shipId = ((Number) call(assembled, "org.valkyrienskies.core.api.ships.Ship", "getId")).longValue();
            result.put("ship_id", shipId);
            phaseStart = ticks;
            state = 1;
            return;
        }

        if (state == 1) {
            if (ticks - phaseStart < 10) return;
            if (ship == null) {
                ship = loadedShip(server, shipId);
                if (ship == null) {
                    if (ticks - phaseStart > 120) throw new IllegalStateException("ship never became a loaded ship");
                    return;
                }
                result.put("initial_position", position(ship));
                helmShip = findHelm(ship, level);
                if (helmShip == null) throw new IllegalStateException("no helm block inside the assembled ship");
                result.put("helm_ship", helmShip.toString());
            }
            try { interact(level); } catch (Throwable ignored) {}
            try {
                Object helmBe = level.method_8321(helm);
                result.put("helm_be", helmBe == null ? "null" : helmBe.getClass().getName());
                if (helmBe != null) {
                    Class<?> beClass = Class.forName("org.valkyrienskies.eureka.blockentity.ShipHelmBlockEntity");
                    Object ok = beClass.getMethod("sit$default", beClass, Class.forName("net.minecraft.class_1657"), boolean.class, int.class, Object.class).invoke(null, helmBe, pilot, false, 2, null);
                    result.put("sit_default", ok);
                }
            } catch (Throwable sitError) { result.put("sit_error", String.valueOf(sitError)); }
            boolean riding = pilot.method_5765();
            result.put("mounted_through_native_helm", riding);
            if (!riding) throw new IllegalStateException("native helm did not mount rider");
            Object control0 = ship.getClass().getMethod("getAttachment", Class.class)
                .invoke(ship, Class.forName("org.valkyrienskies.eureka.ship.EurekaShipControl"));
            control0.getClass()
                .getMethod("setSeatedPlayer", Class.forName("net.minecraft.class_1657"))
                .invoke(control0, pilot);
            result.put("control_null", control0 == null);
            result.put("static_before", ship.getClass().getMethod("isStatic").invoke(ship));
            ship.getClass().getMethod("setStatic", boolean.class).invoke(ship, false);
            control0.getClass().getMethod("setAnchorsActive", int.class).invoke(control0, 0);
            result.put("static_after", ship.getClass().getMethod("isStatic").invoke(ship));
            result.put("seated_player_set", true);
            phaseStart = ticks;
            state = 2;
            return;
        }

        Object control = ship.getClass().getMethod("getAttachment", Class.class)
            .invoke(ship, Class.forName("org.valkyrienskies.eureka.ship.EurekaShipControl"));

        if (state == 2) {
            setInput(class_2350.field_11043, 1f, 0f);
            sample("forward");
            if (ticks - phaseStart > 100) { phaseStart = ticks; state = 3; }
            return;
        }
        if (state == 3) {
            setInput(class_2350.field_11034, 1f, 0f);
            sample("turn");
            if (ticks - phaseStart > 80) { phaseStart = ticks; state = 4; }
            return;
        }
        if (state == 4) {
            removeInput();
            pilot.method_5848();
            control.getClass().getMethod("setAnchorsActive", int.class).invoke(control, 1);
            sample("stop");
            if (ticks - phaseStart > 200) { state = 5; write(server); }
        }
    }

    private Object assemble(class_3218 level, class_2338 pos) throws Exception {
        Class<?> function = Class.forName("kotlin.jvm.functions.Function1");
        Object predicate = Proxy.newProxyInstance(function.getClassLoader(), new Class<?>[]{function},
            (proxy, method, args) -> {
                if (method.getName().equals("invoke")) {
                    String id = net.minecraft.class_7923.field_41175
                        .method_10221(((class_2680) args[0]).method_26204()).toString();
                    return HULL.contains(id);
                }
                return null;
            });
        Object assembler = Class.forName("org.valkyrienskies.eureka.util.ShipAssembler").getField("INSTANCE").get(null);
        return assembler.getClass()
            .getMethod("collectBlocks", class_3218.class, class_2338.class, function)
            .invoke(assembler, level, pos, predicate);
    }

    private Object loadedShip(MinecraftServer server, long wanted) throws Exception {
        Object provider = Class.forName("org.valkyrienskies.mod.common.IShipObjectWorldServerProvider")
            .getMethod("getShipObjectWorld").invoke(server.method_30002());
        for (Object candidate : (Iterable<?>) provider.getClass().getMethod("getLoadedShips").invoke(provider)) {
            long id = ((Number) call(candidate, "org.valkyrienskies.core.api.ships.Ship", "getId")).longValue();
            if (id == wanted) return candidate;
        }
        return null;
    }

    private static int bound(Object box, String name) throws Exception {
        return ((Number) Class.forName("org.joml.primitives.AABBic").getMethod(name).invoke(box)).intValue();
    }

    private class_2338 findHelm(Object ship, class_3218 level) throws Exception {
        Object box = call(ship, "org.valkyrienskies.core.api.ships.Ship", "getShipAABB");
        int minX = bound(box, "minX"), minY = bound(box, "minY"), minZ = bound(box, "minZ");
        int maxX = bound(box, "maxX"), maxY = bound(box, "maxY"), maxZ = bound(box, "maxZ");
        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
            class_2338 pos = new class_2338(x, y, z);
            if (net.minecraft.class_7923.field_41175.method_10221(level.method_8320(pos).method_26204()).toString().equals("vs_eureka:oak_ship_helm")) return pos;
        }
        return null;
    }

    private static class_3218 LEVEL;

    private static class_3218 level() { return LEVEL; }

    private void interact(class_3218 level) throws Exception {
        Object transform = Class.forName("org.valkyrienskies.core.api.ships.Ship")
            .getMethod("getShipToWorld").invoke(ship);
        Object vec = Class.forName("org.joml.Vector3d")
            .getConstructor(double.class, double.class, double.class)
            .newInstance(helm.method_10263() + 0.5, helm.method_10264() + 1.5, helm.method_10260() + 0.5);
        Object world = transform.getClass().getMethod("transformPosition", Class.forName("org.joml.Vector3d"))
            .invoke(transform, vec);
        double wx = num(world, "x"), wy = num(world, "y"), wz = num(world, "z");
        class_2338 target = class_2338.method_49637(wx, wy, wz);
        pilot.method_14251(level, wx, wy, wz, 0f, 0f);
        pilot.method_5660(false);
        pilot.field_13974.method_14262(pilot, level, class_1799.field_8037, class_1268.field_5808,
            new class_3965(new class_243(helmShip.method_10263() + 0.5, helmShip.method_10264() + 0.5, helmShip.method_10260() + 0.5), class_2350.field_11036, helmShip, false));
    }

    private static Object call(Object target, String type, String method) throws Exception {
        return Class.forName(type).getMethod(method).invoke(target);
    }

    private static List<Double> position(Object ship) throws Exception {
        Object transform = call(ship, "org.valkyrienskies.core.api.ships.Ship", "getTransform");
        Object pos = call(transform, "org.valkyrienskies.core.api.bodies.properties.BodyTransform", "getPosition");
        return List.of(num(pos, "x"), num(pos, "y"), num(pos, "z"));
    }

    private static double num(Object vector, String axis) throws Exception {
        return ((Number) vector.getClass().getMethod(axis).invoke(vector)).doubleValue();
    }

    private void setInput(class_2350 direction, float forward, float up) throws Exception {
        Class<?> type = Class.forName("org.valkyrienskies.mod.api.SeatedControllingPlayer");
        Object input = type.getConstructor(class_2350.class).newInstance(direction);
        type.getMethod("setForwardImpulse", float.class).invoke(input, forward);
        type.getMethod("setUpImpulse", float.class).invoke(input, up);
        Class.forName("org.valkyrienskies.core.api.attachment.AttachmentHolder")
            .getMethod("setAttachment", Object.class).invoke(ship, input);
    }

    private void removeInput() throws Exception {
        Class.forName("org.valkyrienskies.core.api.attachment.AttachmentHolder")
            .getMethod("removeAttachment", Class.class)
            .invoke(ship, Class.forName("org.valkyrienskies.mod.api.SeatedControllingPlayer"));
    }

    private void sample(String phase) throws Exception {
        if ((ticks - phaseStart) % 10 != 0) return;
        Object transform = call(ship, "org.valkyrienskies.core.api.ships.Ship", "getTransform");
        Object pos = call(transform, "org.valkyrienskies.core.api.bodies.properties.BodyTransform", "getPosition");
        Object vel = call(ship, "org.valkyrienskies.core.api.ships.Ship", "getVelocity");
        Object q = transform.getClass().getMethod("getShipToWorldRotation").invoke(transform);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("phase", phase);
        row.put("tick", ticks - phaseStart);
        row.put("p", List.of(num(pos, "x"), num(pos, "y"), num(pos, "z")));
        row.put("v", List.of(num(vel, "x"), num(vel, "y"), num(vel, "z")));
        row.put("q", List.of(num(q, "x"), num(q, "y"), num(q, "z"), num(q, "w")));
        samples.add(row);
    }

    private void write(MinecraftServer server) throws Exception {
        result.put("samples", samples);
        Files.writeString(server.method_3831().toPath().resolve("shiptest-result.json"),
            new GsonBuilder().setPrettyPrinting().create().toJson(result));
    }
}
