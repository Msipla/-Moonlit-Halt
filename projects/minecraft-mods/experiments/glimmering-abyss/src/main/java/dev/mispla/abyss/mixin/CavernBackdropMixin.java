package dev.mispla.abyss.mixin;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.mispla.abyss.JourneyClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Underground layout-100 background matches cavern air, while native fog distances stay intact. */
@Mixin(BackgroundRenderer.class)
public abstract class CavernBackdropMixin {
    @Shadow private static float red;
    @Shadow private static float green;
    @Shadow private static float blue;
    @Inject(method="render",at=@At("TAIL"))
    private static void abyss$backdrop(Camera camera,float delta,ClientWorld world,int distance,float skyDarkness,CallbackInfo ci){
        if(JourneyClient.layout>=100&&JourneyClient.origin()!=null&&camera.getSubmersionType()==CameraSubmersionType.NONE){
            red=.012f;green=.033f;blue=.055f;
            RenderSystem.clearColor(red,green,blue,0);
            RenderSystem.setShaderFogColor(red,green,blue);
        }
    }
}
