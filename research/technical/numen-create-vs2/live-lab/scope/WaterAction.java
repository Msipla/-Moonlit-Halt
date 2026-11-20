package lab.createlab;

import com.dwinovo.numen.api.NumenPlugins;
import com.dwinovo.numen.api.permission.Action;
import com.dwinovo.numen.api.sdk.Doc;
import com.dwinovo.numen.api.sdk.Fn;
import com.dwinovo.numen.api.sdk.Pending;
import com.dwinovo.numen.api.sdk.ServerCall;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlock;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import java.util.Optional;
import net.fabricmc.api.ModInitializer;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2680;

public final class WaterAction implements ModInitializer {
    @Override
    public void onInitialize() {
        if (Boolean.getBoolean("createlab.water") && ConstructionBounds.enabled()) {
            NumenPlugins.register("createlab_water", api ->
                api.api("source", "Place one water source beside a small Create water wheel in the creative lab.", Source.class));
        }
    }

    public static final class Source {
        public record Args(@Doc("Exact empty cell above a horizontal wheel rim.") class_2338 cell,
                           @Doc("Center of the nearby small water wheel.") class_2338 wheel) {}
        public record Result(boolean placed, String reason, Optional<class_2338> landedAt,
                             class_2338 wheel, Optional<Double> rpmNow, Optional<Integer> flowScoreNow) {}

        @Fn("Place a single water source in the specified empty cell. The RPM and flow score are live samples at return; inspect again after the stream settles.")
        public static Pending<Result> place(ServerCall call, Args args) {
            String rejection = reject(call, args);
            if (rejection != null) return Pending.of(receipt(args, rejection, null, null));

            var level = call.her().method_51469();
            return call.authorize(Action.place(args.cell(), level.method_8320(args.cell()), class_1802.field_8705))
                    .then(allowed -> {
                        // Owner consent can be asynchronous. Recheck scope, mode and cells before writing.
                        String changed = reject(call, args);
                        if (changed != null) return receipt(args, changed, null, null);
                        var current = call.her().method_51469();
                        class_2680 water = class_2246.field_10382.method_9564(); // Water's default level=0 state.
                        boolean written = current.method_8652(args.cell(), water, 3);
                        var wheel = current.method_8321(args.wheel()) instanceof WaterWheelBlockEntity actual ? actual : null;
                        if (!written || current.method_8320(args.cell()) != water) {
                            return receipt(args, "source_not_written", null, wheel);
                        }
                        return receipt(args, "placed", args.cell(), wheel);
                    });
        }

        private static String reject(ServerCall call, Args args) {
            var player = call.her();
            var level = player.method_51469();
            if (!player.method_7337()) return "creative_required";
            if (!ConstructionBounds.contains(level, args.cell()) || !ConstructionBounds.contains(level, args.wheel())) {
                return "outside_construction_scope";
            }
            if (!level.method_37118(args.cell()) || !level.method_37118(args.wheel())) return "unloaded";
            class_2680 wheelState = level.method_8320(args.wheel());
            if (!(wheelState.method_26204() instanceof WaterWheelBlock wheel)
                    || !(level.method_8321(args.wheel()) instanceof WaterWheelBlockEntity)) return "small_water_wheel_required";
            int dx = Math.abs(args.cell().method_10263() - args.wheel().method_10263());
            int dy = args.cell().method_10264() - args.wheel().method_10264();
            int dz = Math.abs(args.cell().method_10260() - args.wheel().method_10260());
            String axis = wheel.getRotationAxis(wheelState).method_10174();
            if (dy != 1 || !(("x".equals(axis) && dx == 0 && dz == 1) ||
                    ("z".equals(axis) && dx == 1 && dz == 0))) return "source_must_be_above_rim";
            if (!level.method_8320(args.cell()).method_26215()) return "target_not_air";
            return null;
        }

        private static Result receipt(Args args, String reason, class_2338 landedAt, WaterWheelBlockEntity wheel) {
            return new Result(landedAt != null, reason, Optional.ofNullable(landedAt), args.wheel(),
                    wheel == null ? Optional.empty() : Optional.of((double) wheel.getSpeed()),
                    wheel == null ? Optional.empty() : Optional.of(wheel.flowScore));
        }
    }
}
