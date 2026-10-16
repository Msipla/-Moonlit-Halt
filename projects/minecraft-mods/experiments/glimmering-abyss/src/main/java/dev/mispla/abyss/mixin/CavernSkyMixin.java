package dev.mispla.abyss.mixin;

import dev.mispla.abyss.JourneyClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Do not draw the overworld sky dome through distant underground chunk edges. */
@Mixin(WorldRenderer.class)
public abstract class CavernSkyMixin {
    @Inject(method="renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V", at=@At("HEAD"), cancellable=true)
    private void abyss$sky(MatrixStack matrices, Matrix4f projection, float delta,
                           Camera camera, boolean thickFog, Runnable fogCallback, CallbackInfo ci) {
        if (JourneyClient.cavernBackdropActive(camera)) {
            fogCallback.run();
            ci.cancel();
        }
    }
}
