package dev.mispla.abyss.mixin;
import dev.mispla.abyss.EnvironmentClient;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
@Mixin(LightmapTextureManager.class)
public abstract class LightmapMixin {
    @ModifyArgs(method="update",at=@At(value="INVOKE",target="Lnet/minecraft/client/texture/NativeImage;setColor(III)V"))
    private void abyss$display(Args args){int block=args.get(0),sky=args.get(1),nativeColor=args.get(2);args.set(2,EnvironmentClient.lightmapColor(nativeColor,block,sky));}
}
