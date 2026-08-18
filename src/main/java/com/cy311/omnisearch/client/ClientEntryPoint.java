package com.cy311.omnisearch.client;

import com.cy311.omnisearch.keybinds.KeyBinds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public class ClientEntryPoint {

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(KeyBinds::register);
        modEventBus.addListener(ClientEntryPoint::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
    }
}
