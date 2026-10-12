package dev.mispla.abyss.mixin;
import dev.mispla.abyss.SpaceLighting;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(GameRenderer.class)
public abstract class SpaceShaderMixin {
    @Inject(method="getRenderTypeSolidProgram",at=@At("HEAD"),cancellable=true)
    private static void abyss$solid(CallbackInfoReturnable<ShaderProgram> ci){var shader=SpaceLighting.terrain(0);if(shader!=null)ci.setReturnValue(shader);}
    @Inject(method={"getRenderTypeCutoutProgram","getRenderTypeCutoutMippedProgram"},at=@At("HEAD"),cancellable=true)
    private static void abyss$cutout(CallbackInfoReturnable<ShaderProgram> ci){var shader=SpaceLighting.terrain(.1f);if(shader!=null)ci.setReturnValue(shader);}
}
