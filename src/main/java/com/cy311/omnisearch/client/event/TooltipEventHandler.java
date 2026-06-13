package com.cy311.omnisearch.client.event;

import com.cy311.omnisearch.OmnisearchMod;
import com.cy311.omnisearch.client.screen.OmnisearchScreen;
import com.cy311.omnisearch.data.repository.CacheLayer;
import com.cy311.omnisearch.data.repository.SearchRepository;
import com.cy311.omnisearch.data.source.McmodDataSource;
import com.cy311.omnisearch.keybinds.KeyBinds;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

// verified: InputEvent.Key from NeoForge GitHub 1.21.1 branch 2026-06-14
// verified: ItemTooltipEvent from NeoForge GitHub 1.21.1 branch 2026-06-14
// verified: @EventBusSubscriber from FancyModLoader 1.21.1 branch 2026-06-14
@EventBusSubscriber(modid = OmnisearchMod.MOD_ID, value = Dist.CLIENT)
public class TooltipEventHandler {

    private static long tabHoldStartTime = 0;
    private static ItemStack lastHoveredStack = ItemStack.EMPTY;
    private static boolean longPressTriggered = false;

    private static final long HOLD_THRESHOLD_MS = 1000; // 1 second hold

    private static SearchRepository repository;

    private static synchronized SearchRepository getRepository() {
        if (repository == null) {
            var mc = Minecraft.getInstance();
            var cacheDir = mc.gameDirectory.toPath().resolve(".omnisearch/cache");
            repository = new SearchRepository(new CacheLayer(cacheDir), new McmodDataSource());
        }
        return repository;
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (KeyBinds.openSearch.consumeClick()) {
            Minecraft.getInstance().tell(() ->
                Minecraft.getInstance().setScreen(new OmnisearchScreen(getRepository()))
            );
        }
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        boolean isTabDown = InputConstants.isKeyDown(
                Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_TAB);

        if (isTabDown) {
            if (lastHoveredStack.isEmpty() || !ItemStack.isSameItemSameComponents(lastHoveredStack, stack)) {
                lastHoveredStack = stack.copy();
                tabHoldStartTime = System.currentTimeMillis();
                longPressTriggered = false;
            }

            long holdTime = System.currentTimeMillis() - tabHoldStartTime;
            if (holdTime >= HOLD_THRESHOLD_MS && !longPressTriggered) {
                longPressTriggered = true;
                String itemName = stack.getHoverName().getString();
                Minecraft.getInstance().tell(() ->
                    Minecraft.getInstance().setScreen(new OmnisearchScreen(getRepository()))
                );
            }
        } else {
            lastHoveredStack = ItemStack.EMPTY;
            tabHoldStartTime = 0;
            longPressTriggered = false;
        }
    }
}

