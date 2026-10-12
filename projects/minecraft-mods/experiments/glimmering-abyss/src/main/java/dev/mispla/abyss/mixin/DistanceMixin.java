package dev.mispla.abyss.mixin;
import dev.mispla.abyss.EnvironmentClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(BackgroundRenderer.class)
public abstract class DistanceMixin {
    @Inject(method="applyFog",at=@At("TAIL"))
    private static void abyss$distance(Camera camera,BackgroundRenderer.FogType type,float distance,boolean thick,float delta,CallbackInfo ci){
        if(EnvironmentClient.customActive()&&type==BackgroundRenderer.FogType.FOG_TERRAIN&&camera.getSubmersionType()==CameraSubmersionType.NONE)EnvironmentClient.applyDistance();
        if(type==BackgroundRenderer.FogType.FOG_TERRAIN)EnvironmentClient.recordTerrainDistance();
    }
}
