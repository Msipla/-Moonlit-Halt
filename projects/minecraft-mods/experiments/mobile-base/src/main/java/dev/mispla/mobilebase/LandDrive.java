package dev.mispla.mobilebase;

import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.mispla.mobilebase.BoatPlan.Rejected;
import dev.mispla.mobilebase.MobileBase.SurfaceBoat;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.eureka.ship.EurekaShipControl;
import org.valkyrienskies.mod.api.SeatedControllingPlayer;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

public final class LandDrive {
    public record Setup(long core, long[] motors, int heading, int rpm) {}

    private LandDrive() {}

    static void register() {
        ServerTickEvents.END_WORLD_TICK.register(LandDrive::tick);
    }

    static ServerShip launch(ServerLevel level, LandPlan plan) throws Rejected {
        return BoatAssembly.launch(level, plan.hull(), plan.parts(), () -> {
            plan.hull().entrySet().stream().filter(e -> LandPlan.isMotor(e.getValue()))
                .forEach(e -> setMotor(level, e.getKey(), 0));
            plan.parts().entrySet().stream().filter(e -> LandPlan.isMotor(e.getValue()))
                .forEach(e -> setMotor(level, e.getKey(), 0));
        }, (ship, target) -> {
            long core = target.entrySet().stream().filter(e -> e.getValue().state().is(MobileBase.LAND_CORE))
                .findFirst().orElseThrow().getKey().asLong();
            long[] motors = target.entrySet().stream().filter(e -> LandPlan.isMotor(e.getValue().state()))
                .mapToLong(e -> e.getKey().asLong()).toArray();
            if (motors.length != 4) throw new IllegalStateException("Invalid land driveline");
            var setup = new Setup(core, motors, plan.heading().get3DDataValue(), plan.rpm());
            EurekaShipControl.Companion.deferUntilLoaded(ship, control -> ((SurfaceBoat) (Object) control).mobilebase$fitLand(setup));
        });
    }

    private static void tick(ServerLevel level) {
        var dimension = VSGameUtilsKt.getDimensionId(level);
        for (var ship : VSGameUtilsKt.getShipObjectWorld(level).getLoadedShips()) {
            if (!dimension.equals(ship.getChunkClaimDimension())) continue;
            var control = ship.getAttachment(EurekaShipControl.class);
            if (control == null) continue;
            var equipped = (SurfaceBoat) (Object) control;
            var setup = equipped.mobilebase$land();
            if (setup == null) continue;
            var core = BlockPos.of(setup.core());
            if (!level.hasChunkAt(core)) continue;
            var state = level.getBlockState(core);
            var pilot = ship.getAttachment(SeatedControllingPlayer.class);
            boolean stopped = !state.is(MobileBase.LAND_CORE) || control.getHelms() == 0 || control.getAnchorsActive() != 0;
            if (!equipped.mobilebase$landCruiseArmed(pilot != null) && control.isCruising()) {
                control.setCruising(false);
            }
            if (!stopped && control.isCruising()) continue;
            float throttle = pilot == null || stopped ? 0 : pilot.getForwardImpulse();
            float turn = pilot == null || stopped ? 0 : pilot.getLeftImpulse();
            var heading = Direction.from3DDataValue(setup.heading());
            var left = heading.getCounterClockWise();
            int rpmSign = heading.getStepZ() - heading.getStepX();
            for (long packed : setup.motors()) {
                var pos = BlockPos.of(packed);
                if (!level.hasChunkAt(pos) || !LandPlan.isMotor(level.getBlockState(pos))) continue;
                var outward = level.getBlockState(pos).getValue(BlockStateProperties.FACING);
                float side = outward == left ? -1 : 1;
                int requested = Math.round(setup.rpm() * Mth.clamp(throttle + side * turn * 0.5f, -1f, 1f));
                setMotor(level, pos, requested * rpmSign * outward.getAxisDirection().getStep());
            }
        }
    }

    private static void setMotor(ServerLevel level, BlockPos pos, int rpm) {
        if (level.getBlockEntity(pos) instanceof CreativeMotorBlockEntity motor) {
            motor.generatedSpeed.setValue(rpm);
        }
    }
}
