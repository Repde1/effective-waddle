package com.skepter.daytimemobspawning.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents any Monster subclass from burning in sunlight.
 * Injects at HEAD of Mob#isSunBurnTick and cancels with false if the mob is a Monster.
 */
@Mixin(Mob.class)
public abstract class MobSunBurnMixin {

    @Inject(method = "isSunBurnTick", at = @At("HEAD"), cancellable = true)
    private void daytimemobspawning$disableMonsterSunBurn(CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this instanceof Monster) {
            callback.setReturnValue(false);
        }
    }
}
