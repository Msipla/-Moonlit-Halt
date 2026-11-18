package dev.mispla.mobilebase.mixin;

import dev.mispla.mobilebase.BoatAssembly;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;

@Mixin(value = ShipAssembler.class, remap = false)
abstract class VsAssemblyMixin {
    @Inject(method = "moveBlocksFromTo", at = @At("HEAD"))
    private static void mobilebase$captureRollbackTarget(ServerLevel level, Set<BlockPos> blocks,
        ServerShip from, ServerShip to, BlockPos min, BlockPos max, Vector3i destination,
        boolean removeOriginal, CallbackInfoReturnable<ShipAssembler.MoveContext> callback) {
        BoatAssembly.captureTarget(level, blocks, from, to, min, max, destination);
    }
}
