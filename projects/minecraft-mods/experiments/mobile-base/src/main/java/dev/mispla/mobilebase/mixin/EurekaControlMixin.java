package dev.mispla.mobilebase.mixin;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.mispla.mobilebase.MobileBase.ManagedBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.eureka.EurekaConfig;
import org.valkyrienskies.eureka.ship.EurekaShipControl;
import org.valkyrienskies.mod.common.util.BuoyancyHandlerAttachment;

@Mixin(value = EurekaShipControl.class, remap = false)
abstract class EurekaControlMixin implements ManagedBoat {
    @Shadow
    private LoadedServerShip ship;

    @Unique
    @JsonProperty("mobileBaseEngineOnly")
    private volatile boolean mobilebase$managed;

    @Override
    public boolean mobilebase$isManaged() {
        return mobilebase$managed;
    }

    @Override
    public void mobilebase$markManaged() {
        mobilebase$managed = true;
    }

    @Inject(method = "onServerTick", at = @At("HEAD"))
    private void mobilebase$useSurfaceBuoyancy(CallbackInfo callback) {
        if (!mobilebase$managed || ship == null) return;
        var airPocket = ship.getAttachment(BuoyancyHandlerAttachment.class);
        if (airPocket != null) ManagedBoat.of(airPocket).mobilebase$markManaged();
    }

    @Redirect(method = "physTick", at = @At(value = "INVOKE",
        target = "Lorg/valkyrienskies/eureka/EurekaConfig$Server;getDoFluidDrag()Z"))
    private boolean mobilebase$useNativeWaterDrag(EurekaConfig.Server config) {
        return mobilebase$managed || config.getDoFluidDrag();
    }

    @Redirect(method = "getPlayerForwardVel", at = @At(value = "INVOKE",
        target = "Lorg/valkyrienskies/eureka/EurekaConfig$Server;getLinearBaseSpeed()D"))
    private double mobilebase$useNativeEnginePower(EurekaConfig.Server config) {
        return mobilebase$managed ? 0.0 : config.getLinearBaseSpeed();
    }
}
