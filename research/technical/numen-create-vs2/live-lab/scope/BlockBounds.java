package lab.scope;

import lab.createlab.ConstructionBounds;
import net.minecraft.class_1937;
import net.minecraft.class_2338;
import net.minecraft.class_2680;
import net.minecraft.class_3218;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = class_1937.class, remap = false)
abstract class BlockBounds {
    // World.setBlockState(BlockPos, BlockState, flags, remainingUpdateDepth) on Fabric 1.20.1.
    @Inject(method = "method_30092", at = @At("HEAD"), cancellable = true, remap = false)
    private void createlab$checkWrite(class_2338 pos, class_2680 state, int flags, int depth,
                                       CallbackInfoReturnable<Boolean> result) {
        if ((Object)this instanceof class_3218 level
                && ConstructionBounds.enabled() && !ConstructionBounds.contains(level, pos)) {
            result.setReturnValue(false);
        }
    }
}
