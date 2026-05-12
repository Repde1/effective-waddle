package com.skepter.daytimemobspawning.mixin;

import com.skepter.daytimemobspawning.MobSpawnSwapper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Iterator;

/**
 * Intercepts NaturalSpawner#spawnMobsForChunkGeneration to replace eligible passives
 * with daytime hostiles at chunk generation time.
 *
 * In 1.21.1 the target method is:
 *   NaturalSpawner#spawnMobsForChunkGeneration(ServerLevelAccessor, MobCategory, StructureManager, ChunkGenerator, RandomSource, BlockPos)
 *
 * We inject after the mob entity has been created and position-set, just before it is
 * added to the level, capturing the local Mob variable.
 */
@Mixin(net.minecraft.world.level.NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {

    @Inject(
        method = "spawnMobsForChunkGeneration",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/ServerLevelAccessor;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V"
        ),
        locals = LocalCapture.CAPTURE_FAILSOFT,
        cancellable = true
    )
    private static void daytimemobspawning$swapChunkGenPassive(
            ServerLevelAccessor level,
            net.minecraft.world.level.chunk.LevelChunk chunk,
            net.minecraft.world.entity.MobCategory category,
            net.minecraft.util.RandomSource random,
            BlockPos pos,
            CallbackInfo ci,
            // locals captured after injection point - mob is the one about to be spawned
            Object... locals) {

        if (!(level instanceof ServerLevel serverLevel)) return;

        // Find the Mob instance in captured locals
        Mob mob = null;
        for (Object local : locals) {
            if (local instanceof Mob m) {
                mob = m;
                break;
            }
        }
        if (mob == null) return;

        EntityType<?> originalType = mob.getType();

        boolean isDesert = serverLevel.getBiome(pos).is(Tags.Biomes.IS_DESERT);
        boolean isSnowy  = serverLevel.getBiome(pos).is(Tags.Biomes.IS_SNOWY);

        EntityType<? extends Mob> replacementType =
            MobSpawnSwapper.getChunkGenerationReplacementType(originalType, random, isDesert, isSnowy);
        if (replacementType == null) return;

        net.minecraft.world.entity.Entity created = replacementType.create(serverLevel, EntitySpawnReason.SPAWN_ITEM_USE);
        if (!(created instanceof Mob replacement)) return;

        replacement.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());

        if (!EventHooks.checkSpawnPosition(replacement, serverLevel, EntitySpawnReason.SPAWN_ITEM_USE)) return;

        EventHooks.finalizeMobSpawn(replacement, serverLevel, serverLevel.getCurrentDifficultyAt(pos), EntitySpawnReason.SPAWN_ITEM_USE, (SpawnGroupData) null);
        replacement.setPersistenceRequired();

        if (replacement.isRemoved()) return;

        MobSpawnSwapper.debugChunkGenerationAdd(replacement);

        ci.cancel();
        level.addFreshEntityWithPassengers(replacement);
    }
}
