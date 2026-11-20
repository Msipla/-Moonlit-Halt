package lab.scope;

import java.util.Arrays;
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
    private static final int[] createlab$box = createlab$readBox();

    private static int[] createlab$readBox() {
        String configured = System.getProperty("createlab.scope");
        if (configured == null) return null;
        int[] box = Arrays.stream(configured.split(",", -1)).mapToInt(Integer::parseInt).toArray();
        if (box.length != 6 || box[0] > box[3] || box[1] > box[4] || box[2] > box[5]) {
            throw new IllegalArgumentException("createlab.scope requires minX,minY,minZ,maxX,maxY,maxZ");
        }
        return box;
    }

    // World.setBlockState(BlockPos, BlockState, flags, remainingUpdateDepth) on Fabric 1.20.1.
    @Inject(method = "method_30092", at = @At("HEAD"), cancellable = true, remap = false)
    private void createlab$checkWrite(class_2338 pos, class_2680 state, int flags, int depth,
                                       CallbackInfoReturnable<Boolean> result) {
        if (createlab$box == null || !((Object)this instanceof class_3218 level)) return;
        // The disposable lab is in the overworld. Runtime block writes in other dimensions are denied.
        int x = pos.method_10263(), y = pos.method_10264(), z = pos.method_10260();
        if (!class_1937.field_25179.equals(level.method_27983()) ||
                x < createlab$box[0] || x > createlab$box[3] ||
                y < createlab$box[1] || y > createlab$box[4] ||
                z < createlab$box[2] || z > createlab$box[5]) {
            result.setReturnValue(false);
        }
    }
}
