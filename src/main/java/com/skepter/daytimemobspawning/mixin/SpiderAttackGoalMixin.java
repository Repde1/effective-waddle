package com.skepter.daytimemobspawning.mixin;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Redirects the getLightLevelDependentMagicValue() call inside Spider$SpiderAttackGoal#canContinueToUse
 * to always return 0.0, making spiders continue attacking regardless of light level during the day.
 */
@Mixin(targets = "net.minecraft.world.entity.monster.Spider$SpiderAttackGoal")
public abstract class SpiderAttackGoalMixin {

    @Redirect(
        method = "canContinueToUse",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;getLightLevelDependentMagicValue()F"
        )
    )
    private float daytimemobspawning$ignoreLightWhenContinuingSpiderAttack(Mob mob) {
        return 0.0f;
    }
}
