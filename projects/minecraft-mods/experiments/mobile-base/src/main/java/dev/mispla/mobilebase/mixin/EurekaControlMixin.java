package dev.mispla.mobilebase.mixin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.mispla.mobilebase.MobileBase.ManagedBoat;
import dev.mispla.mobilebase.MobileBase.SurfaceBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.PhysShip;
import org.valkyrienskies.core.api.world.PhysLevel;
import org.valkyrienskies.eureka.EurekaConfig;
import org.valkyrienskies.eureka.ship.EurekaShipControl;
import org.valkyrienskies.mod.common.util.BuoyancyHandlerAttachment;

@Mixin(value = EurekaShipControl.class, remap = false)
abstract class EurekaControlMixin implements SurfaceBoat {
    @Shadow
    private LoadedServerShip ship;

    @Shadow
    private int floaters;

    @Shadow
    public abstract int getAnchorsActive();

    @Unique
    @JsonProperty("mobileBaseEngineOnly")
    private volatile boolean mobilebase$managed;

    @Unique
    @JsonProperty("mobileBaseWetColumns")
    private volatile int mobilebase$wetColumns;

    @Unique
    @JsonProperty("mobileBasePlannedFloaters")
    private volatile int mobilebase$plannedFloaters;

    @Unique
    @JsonProperty("mobileBaseEngineScale")
    private volatile double mobilebase$engineScale = 1;

    @Unique
    @JsonIgnore
    private volatile boolean mobilebase$anchored;

    @Override
    public void mobilebase$adapt(int wetColumns, int plannedFloaters, double engineScale) {
        mobilebase$plannedFloaters = plannedFloaters;
        mobilebase$engineScale = engineScale;
        mobilebase$wetColumns = wetColumns;
        mobilebase$markManaged();
    }

    @Override
    public boolean mobilebase$isManaged() {
        return mobilebase$managed;
    }

    @Override
    public boolean mobilebase$isAdaptedBoatAnchored() {
        return mobilebase$anchored;
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

    @Inject(method = "physTick", at = @At("RETURN"))
    private void mobilebase$observeAnchor(PhysShip body, PhysLevel level, CallbackInfo callback) {
        if (mobilebase$wetColumns > 0) mobilebase$anchored = getAnchorsActive() > 0 && body.isStatic();
    }

    @Redirect(method = "getPlayerForwardVel", at = @At(value = "INVOKE",
        target = "Lorg/valkyrienskies/eureka/EurekaConfig$Server;getLinearBaseSpeed()D"))
    private double mobilebase$useNativeEnginePower(EurekaConfig.Server config) {
        return mobilebase$managed ? 0.0 : config.getLinearBaseSpeed();
    }

    @Inject(method = "getFloaterFactor", at = @At("RETURN"), cancellable = true)
    private void mobilebase$adaptHullLift(double mass, CallbackInfoReturnable<Double> result) {
        if (!mobilebase$managed || mobilebase$wetColumns <= 0 || mobilebase$plannedFloaters <= 0 || floaters <= 0) return;
        // Water displacement remains in VS. One rated column targets about half a block of draft.
        double working = Math.min(1, floaters / (15.0 * mobilebase$plannedFloaters));
        double rated = 1 + Math.max(0, mass / (mobilebase$wetColumns * 520.0) - 1) * working;
        result.setReturnValue(Math.max(result.getReturnValue(), rated));
    }

    @ModifyArg(method = "getPlayerForwardVel", at = @At(value = "INVOKE", ordinal = 1,
        target = "Lorg/valkyrienskies/eureka/ship/EurekaShipControl;smoothingATanMax(DD)D"), index = 1)
    private double mobilebase$adaptReversePower(double nativeBoost) {
        return mobilebase$engineBoost(nativeBoost);
    }

    @ModifyArg(method = "getPlayerForwardVel", at = @At(value = "INVOKE", ordinal = 2,
        target = "Lorg/valkyrienskies/eureka/ship/EurekaShipControl;smoothingATanMax(DD)D"), index = 1)
    private double mobilebase$adaptForwardPower(double nativeBoost) {
        return mobilebase$engineBoost(nativeBoost);
    }

    @Unique
    private double mobilebase$engineBoost(double nativeBoost) {
        return mobilebase$managed ? nativeBoost * mobilebase$engineScale : nativeBoost;
    }

}
