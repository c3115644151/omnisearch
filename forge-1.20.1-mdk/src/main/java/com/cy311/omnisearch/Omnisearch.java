package com.cy311.omnisearch;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.common.MinecraftForge;

@Mod(Omnisearch.MODID)
public class Omnisearch {
    public static final String MODID = "omnisearch";

    public Omnisearch() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        modBus.addListener(com.cy311.omnisearch.client.ClientEvents::onKeyRegister);

        MinecraftForge.EVENT_BUS.addListener(com.cy311.omnisearch.client.ClientEvents::onKeyInput);
        MinecraftForge.EVENT_BUS.addListener(com.cy311.omnisearch.client.ClientEvents::onItemTooltip);
        MinecraftForge.EVENT_BUS.addListener(com.cy311.omnisearch.client.ClientEvents::onMouseScroll);
        MinecraftForge.EVENT_BUS.addListener(com.cy311.omnisearch.client.ClientEvents::onScreenMouseScroll);
    }
}