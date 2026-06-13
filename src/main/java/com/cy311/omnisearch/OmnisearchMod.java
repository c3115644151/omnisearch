package com.cy311.omnisearch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

@Mod(OmnisearchMod.MOD_ID)
public class OmnisearchMod {
    public static final String MOD_ID = "omnisearch";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public OmnisearchMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Omnisearch v2 initialized");
    }
}
