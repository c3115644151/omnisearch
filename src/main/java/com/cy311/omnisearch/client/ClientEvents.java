package com.cy311.omnisearch.client;

import com.cy311.omnisearch.KeyBinds;
import com.cy311.omnisearch.client.gui.OmnisearchScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.minecraft.client.Minecraft;

@EventBusSubscriber(modid = "omnisearch", value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void onKeyRegister(RegisterKeyMappingsEvent event) {
        event.register(KeyBinds.openOmnisearch);
    }

    @EventBusSubscriber(modid = "omnisearch", value = Dist.CLIENT)
    public static class ForgeEvents {
        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            try {
                if (KeyBinds.openOmnisearch.consumeClick()) {
                    Minecraft.getInstance().tell(() -> {
                        Minecraft.getInstance().setScreen(new OmnisearchScreen());
                    });
                }
            } catch (Throwable t) {
                System.err.println("Failed to open Omnisearch screen!");
                t.printStackTrace();
            }
        }
    }
}