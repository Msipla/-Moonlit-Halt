package dev.mispla.abyss.mixin;

import dev.mispla.abyss.SceneSafety;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerWorld.class)
public abstract class SceneSpawnMixin {
    @Inject(method="spawnEntity",at=@At("HEAD"),cancellable=true)
    private void abyss$excludeSceneMobs(Entity entity,CallbackInfoReturnable<Boolean> result){
        if(entity instanceof MobEntity&&SceneSafety.protects((ServerWorld)(Object)this))result.setReturnValue(false);
    }
}
