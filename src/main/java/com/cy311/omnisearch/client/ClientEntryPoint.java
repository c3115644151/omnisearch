package com.cy311.omnisearch.client;

import com.cy311.omnisearch.keybinds.KeyBinds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

// verified: FMLClientSetupEvent from FancyModLoader 1.21.1 branch 2026-06-14
// verified: RegisterKeyMappingsEvent is IModBusEvent (fires on mod event bus) from NeoForge GitHub 1.21.1 2026-06-14
public class ClientEntryPoint {

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(KeyBinds::register);
        modEventBus.addListener(ClientEntryPoint::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        // Subsequent Wave: register overlay and Screen here
    }
}
