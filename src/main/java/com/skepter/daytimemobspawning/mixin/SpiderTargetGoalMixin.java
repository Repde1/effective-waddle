package com.skepter.daytimemobspawning.mixin;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Redirects the getLightLevelDependentMagicValue() call inside Spider$SpiderTargetGoal#canUse
 * to always return 0.0, making spiders target players regardless of light level during the day.
 */
@Mixin(targets = "net.minecraft.world.entity.monster.Spider$SpiderTargetGoal")
public abstract class SpiderTargetGoalMixin {

    @Redirect(
        method = "canUse",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;getLightLevelDependentMagicValue()F"
        )
    )
    private float daytimemobspawning$ignoreLightWhenChoosingSpiderTarget(Mob mob) {
        return 0.0f;
    }
}
