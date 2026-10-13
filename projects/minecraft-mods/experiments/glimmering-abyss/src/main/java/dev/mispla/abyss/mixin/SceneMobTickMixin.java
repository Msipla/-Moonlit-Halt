package dev.mispla.abyss.mixin;

import dev.mispla.abyss.SceneSafety;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class SceneMobTickMixin {
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void abyss$removeEnteringMob(CallbackInfo ci){
        var mob=(MobEntity)(Object)this;
        if(mob.getWorld() instanceof ServerWorld w&&SceneSafety.protects(w,mob.getX(),mob.getY(),mob.getZ())){mob.discard();ci.cancel();}
    }
}
