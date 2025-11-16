package com.cy311.omnisearch;

import com.cy311.omnisearch.client.ClientEvents;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(Omnisearch.MODID)
public class Omnisearch {
    public static final String MODID = "omnisearch";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Omnisearch(IEventBus modEventBus) {
        // Register the onKeyRegister method to the mod event bus
        modEventBus.register(ClientEvents.class);

        // Register the ForgeEvents inner class to the forge event bus
        NeoForge.EVENT_BUS.register(ClientEvents.ForgeEvents.class);
    }
}