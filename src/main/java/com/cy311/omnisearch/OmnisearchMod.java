package com.cy311.omnisearch;

import com.cy311.omnisearch.client.ClientEntryPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLEnvironment;

// verified: FMLEnvironment.dist from FancyModLoader 1.21.1 branch 2026-06-14
@Mod(OmnisearchMod.MOD_ID)
public class OmnisearchMod {
    public static final String MOD_ID = "omnisearch";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public OmnisearchMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Omnisearch v2 initialized");
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientEntryPoint.init(modEventBus);
        }
    }
}
