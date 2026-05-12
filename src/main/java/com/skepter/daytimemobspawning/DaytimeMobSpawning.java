package com.skepter.daytimemobspawning;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(DaytimeMobSpawning.MOD_ID)
public final class DaytimeMobSpawning {

    public static final String MOD_ID = "daytimemobspawning";
    private static final Logger LOGGER = LogUtils.getLogger();

    public DaytimeMobSpawning(IEventBus modEventBus) {
        NeoForge.EVENT_BUS.register(MobSpawnSwapper.class);
        LOGGER.info("DaytimeMobSpawning is loading and registering spawn swap handlers.");
    }
}
