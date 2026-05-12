package com.skepter.daytimemobspawning;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class MobSpawnSwapper {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicInteger DEBUG_LOG_COUNT = new AtomicInteger();

    /** ThreadLocal flag to prevent recursive swapping when we add the replacement entity */
    static final ThreadLocal<Boolean> REPLACING_SPAWN = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** Passive mob types eligible to be swapped out for a hostile */
    static final Set<EntityType<?>> ELIGIBLE_PASSIVE_TYPES = Set.of(
        EntityType.ARMADILLO,
        EntityType.CAMEL,
        EntityType.CAT,
        EntityType.CHICKEN,
        EntityType.COW,
        EntityType.DONKEY,
        EntityType.FOX,
        EntityType.FROG,
        EntityType.GOAT,
        EntityType.HORSE,
        EntityType.LLAMA,
        EntityType.MOOSHROOM,
        EntityType.MULE,
        EntityType.OCELOT,
        EntityType.PARROT,
        EntityType.PIG,
        EntityType.POLAR_BEAR,
        EntityType.RABBIT,
        EntityType.SHEEP,
        EntityType.SNIFFER,
        EntityType.TURTLE,
        EntityType.WOLF
    );

    /** Weighted pool for standard biomes */
    private static final List<WeightedHostile> GENERIC_HOSTILE_POOL = List.of(
        new WeightedHostile(EntityType.CREEPER,         100),
        new WeightedHostile(EntityType.ENDERMAN,         10),
        new WeightedHostile(EntityType.SKELETON,        100),
        new WeightedHostile(EntityType.SPIDER,          100),
        new WeightedHostile(EntityType.WITCH,             5),
        new WeightedHostile(EntityType.ZOMBIE,           95),
        new WeightedHostile(EntityType.ZOMBIE_VILLAGER,   5)
    );

    record WeightedHostile(EntityType<? extends Mob> type, int weight) {}

    // -------------------------------------------------------------------------
    // Event handler: swap passives that join the level via natural spawning
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (REPLACING_SPAWN.get()) return;
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (event.loadedFromDisk()) return;

        MobSpawnType spawnReason = mob.getSpawnType();
        if (spawnReason == MobSpawnType.LOAD) return;

        EntityType<?> originalType = mob.getType();
        if (!ELIGIBLE_PASSIVE_TYPES.contains(originalType)) return;

        // 50 % chance to swap
        if (serverLevel.random.nextFloat() >= 0.5f) {
            debug("Keeping {} because swap chance failed", originalType);
            return;
        }

        BlockPos pos = BlockPos.containing(mob.position());
        boolean isDesert = isBiome(serverLevel, pos, Tags.Biomes.IS_DESERT);
        boolean isSnowy  = isBiome(serverLevel, pos, Tags.Biomes.IS_SNOWY);

        EntityType<? extends Mob> replacementType = pickReplacementType(serverLevel.random, isDesert, isSnowy);

        net.minecraft.world.entity.Entity created = replacementType.create(serverLevel, getReplacementSpawnReason(spawnReason));
        if (!(created instanceof Mob replacement)) {
            debug("Skipping {} because replacement {} could not be created", originalType, replacementType);
            return;
        }

        copySpawnContext(mob, replacement);

        replacement.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(pos), getReplacementSpawnReason(spawnReason), (SpawnGroupData) null);

        if (replacement.isRemoved()) {
            debug("Skipping {} because replacement {} was removed during finalizeSpawn", originalType, replacementType);
            return;
        }

        debug("Swapping {} -> {}", originalType, replacementType);
        event.setCanceled(true);

        REPLACING_SPAWN.set(Boolean.TRUE);
        try {
            serverLevel.addFreshEntity(replacement);
        } finally {
            REPLACING_SPAWN.set(Boolean.FALSE);
        }
    }

    // -------------------------------------------------------------------------
    // Called from NaturalSpawnerMixin during chunk generation
    // -------------------------------------------------------------------------

    public static EntityType<? extends Mob> getChunkGenerationReplacementType(
            EntityType<?> entityType,
            net.minecraft.util.RandomSource random,
            boolean isDesert,
            boolean isSnowy) {

        if (!ELIGIBLE_PASSIVE_TYPES.contains(entityType)) return null;
        return pickReplacementType(random, isDesert, isSnowy);
    }

    public static boolean isHostileReplacementType(EntityType<?> type) {
        return GENERIC_HOSTILE_POOL.stream().anyMatch(w -> w.type() == type)
            || type == EntityType.HUSK
            || type == EntityType.STRAY;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static EntityType<? extends Mob> pickReplacementType(
            net.minecraft.util.RandomSource random,
            boolean isDesert,
            boolean isSnowy) {

        List<WeightedHostile> pool = new ArrayList<>(GENERIC_HOSTILE_POOL);

        if (isDesert) {
            pool.add(new WeightedHostile(EntityType.HUSK,  80));
            pool.add(new WeightedHostile(EntityType.STRAY, 80));
        } else if (isSnowy) {
            pool.add(new WeightedHostile(EntityType.STRAY, 80));
        }

        int totalWeight = pool.stream().mapToInt(WeightedHostile::weight).sum();
        int roll = (int)(random.nextFloat() * totalWeight);

        for (WeightedHostile entry : pool) {
            roll -= entry.weight();
            if (roll < 0) return entry.type();
        }
        // Fallback (should not happen)
        return EntityType.ZOMBIE;
    }

    private static MobSpawnType getReplacementSpawnReason(MobSpawnType original) {
        return switch (original) {
            case NATURAL, CHUNK_GENERATION -> MobSpawnType.NATURAL;
            default -> MobSpawnType.SPAWN_ITEM_USE;
        };
    }

    private static void copySpawnContext(Mob from, Mob to) {
        to.moveTo(from.position().x, from.position().y, from.position().z, from.getYRot(), from.getXRot());
    }

    private static boolean isBiome(ServerLevel level, BlockPos pos, net.minecraft.tags.TagKey<net.minecraft.world.level.biome.Biome> tag) {
        return level.getBiome(pos).is(tag);
    }

    public static void debugChunkGenerationAdd(Mob mob) {
        debug("Swapping chunk-generation {} -> {}", mob.getType(), mob.getType());
    }

    private static void debug(String msg, Object... args) {
        if (DEBUG_LOG_COUNT.getAndIncrement() < 2000) {
            LOGGER.debug(msg, args);
        }
    }
}
