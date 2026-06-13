package com.cy311.omnisearch;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(value = Omnisearch.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Omnisearch.MODID, value = Dist.CLIENT)
public class OmnisearchClient {

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        Omnisearch.LOGGER.info("Omnisearch client initialized");
    }
}
