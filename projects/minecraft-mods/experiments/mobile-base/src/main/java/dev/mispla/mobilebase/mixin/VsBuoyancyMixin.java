package dev.mispla.mobilebase.mixin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.mispla.mobilebase.MobileBase.ManagedBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.core.api.ships.PhysShip;
import org.valkyrienskies.core.api.world.PhysLevel;
import org.valkyrienskies.mod.common.util.BuoyancyHandlerAttachment;

@Mixin(value = BuoyancyHandlerAttachment.class, remap = false)
abstract class VsBuoyancyMixin implements ManagedBoat {
    @Unique
    @JsonIgnore
    private volatile boolean mobilebase$surfaceBoat;

    @Override
    public boolean mobilebase$isManaged() {
        return mobilebase$surfaceBoat;
    }

    @Override
    public void mobilebase$markManaged() {
        mobilebase$surfaceBoat = true;
    }

    @Inject(method = "physTick", at = @At("HEAD"), cancellable = true)
    private void mobilebase$preferHullAndFloaters(PhysShip ship, PhysLevel level, CallbackInfo callback) {
        // VS 2.4.10 can attribute shipyard air to an open hull as a sealed pocket.
        if (mobilebase$surfaceBoat) callback.cancel();
    }
}
