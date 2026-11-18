package dev.mispla.mobilebase.mixin;

import dev.mispla.mobilebase.MobileBase.ManagedBoat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.eureka.blockentity.ShipHelmBlockEntity;
import org.valkyrienskies.eureka.ship.EurekaShipControl;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

@Mixin(value = ShipHelmBlockEntity.class, remap = false)
abstract class EurekaHelmMixin {
    @Inject(method = "disassemble", at = @At("HEAD"), cancellable = true)
    private void mobilebase$requireCoreForDocking(CallbackInfo callback) {
        BlockEntity helm = (BlockEntity) (Object) this;
        if (!(helm.getLevel() instanceof ServerLevel level)) return;
        var ship = VSGameUtilsKt.getLoadedShipManagingPos(level, helm.getBlockPos());
        if (ship == null) return;
        var control = ship.getAttachment(EurekaShipControl.class);
        if (control == null || !ManagedBoat.of(control).mobilebase$isManaged()) return;

        if (control.getSeatedPlayer() != null) {
            control.getSeatedPlayer().displayClientMessage(Component.translatable("mobilebase.use_core"), true);
        }
        callback.cancel();
    }
}
