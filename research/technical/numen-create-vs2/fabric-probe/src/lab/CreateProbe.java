package lab;

import com.dwinovo.numen.api.NumenPlugins;
import com.dwinovo.numen.api.sdk.Doc;
import com.dwinovo.numen.api.sdk.Fn;
import com.dwinovo.numen.api.sdk.ServerCall;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlock;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import java.util.Optional;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_1937;
import net.minecraft.class_2338;
import net.minecraft.class_2487;
import net.minecraft.class_2586;

public final class CreateProbe implements ModInitializer {
    @Override
    public void onInitialize() {
        NumenPlugins.register("createlab", api -> api.api("machine", "Inspect live Create kinetics.", Machine.class));
        if (FabricLoader.getInstance().isModLoaded("valkyrienskies") && FabricLoader.getInstance().isModLoaded("vs_eureka")) {
            NumenPlugins.register("vslab", api -> api.api("ship", "Read VS2 ship motion.", Ships.class));
        }
    }

    public static final class Machine {
        public record Args(@Doc("A loaded test cell.") class_2338 cell) {}
        public record Kinetics(double rpm, double theoreticalRpm, double generatedRpm,
                               boolean overstressed, double stress, double capacity,
                               Optional<Integer> flowScore) {}
        public record Crusher(boolean valid, String facing, double processingSpeed, boolean occupied) {}
        public record Sample(String kind, Optional<Kinetics> kinetics, Optional<Crusher> crusher) {}

        @Fn("Verify the registered trial Skill can be listed and loaded by Numen.")
        public static boolean skillAvailable(ServerCall call) {
            var skills = com.dwinovo.numen.api.agent.skill.SkillRegistry.of(call.companion());
            return skills.formatXml().contains("<name>create_kinetics</name>")
                && skills.get("create_kinetics")
                    .map(info -> info.content().contains("verify machine behavior separately"))
                    .orElse(false);
        }

        @Fn("Report signed RPM, stress and capacity, or the crusher controller's live state.")
        public static Sample inspect(ServerCall call, Args args) {
            class_1937 level = call.her().method_51469(); // ServerPlayer.serverLevel()
            class_2586 entity = level.method_8321(args.cell()); // Level.getBlockEntity()
            if (entity instanceof KineticBlockEntity kinetic) {
                class_2487 network = kinetic.method_38243().method_10562("Network"); // saveWithoutMetadata().getCompound()
                Optional<Integer> flow = kinetic instanceof WaterWheelBlockEntity wheel
                    ? Optional.of(wheel.flowScore) : Optional.empty();
                return new Sample("kinetic", Optional.of(new Kinetics(
                    kinetic.getSpeed(), kinetic.getTheoreticalSpeed(), kinetic.getGeneratedSpeed(), kinetic.isOverStressed(),
                    network.method_10583("Stress"), network.method_10583("Capacity"), flow)), Optional.empty());
            }
            if (entity instanceof CrushingWheelControllerBlockEntity crusher) {
                var state = level.method_8320(args.cell()); // Level.getBlockState()
                var valid = state.method_11654(CrushingWheelControllerBlock.VALID); // BlockState.getValue()
                var facing = state.method_11654(net.minecraft.class_2318.field_10927);
                return new Sample("crusher", Optional.empty(), Optional.of(new Crusher(
                    valid, facing.toString(), crusher.crushingspeed, crusher.isOccupied())));
            }
            return new Sample("missing", Optional.empty(), Optional.empty());
        }
    }
}
