package com.cy311.omnisearch.client.event;

import com.cy311.omnisearch.OmnisearchMod;
import com.cy311.omnisearch.client.render.HudOverlayHandler;
import com.cy311.omnisearch.client.screen.OmnisearchScreen;
import com.cy311.omnisearch.data.repository.CacheLayer;
import com.cy311.omnisearch.data.repository.SearchRepository;
import com.cy311.omnisearch.data.source.McmodDataSource;
import com.cy311.omnisearch.data.client.McmodHttpClient;
import com.cy311.omnisearch.keybinds.KeyBinds;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = OmnisearchMod.MOD_ID, value = Dist.CLIENT)
public class TooltipEventHandler {

    private static long tabHoldStartTime = 0;
    private static ItemStack lastHoveredStack = ItemStack.EMPTY;
    private static boolean longPressTriggered = false;

    private static final long HOLD_THRESHOLD_MS = 2000;

    private static SearchRepository repository;
    private static McmodHttpClient httpClient;

    private static synchronized SearchRepository getRepository() {
        if (repository == null) {
            var mc = Minecraft.getInstance();
            var cacheDir = mc.gameDirectory.toPath().resolve(".omnisearch/cache");
            httpClient = new McmodHttpClient();
            repository = new SearchRepository(new CacheLayer(cacheDir), new McmodDataSource(httpClient));
        }
        return repository;
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (KeyBinds.openSearch.consumeClick()) {
            Minecraft.getInstance().tell(() ->
                Minecraft.getInstance().setScreen(
                    new OmnisearchScreen(getRepository(), httpClient != null ? httpClient::downloadImageBytes : null))
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
            float progress = Math.min(1, (float) holdTime / HOLD_THRESHOLD_MS);

            // Update overlay progress
            String itemName = stack.getHoverName().getString();
            HudOverlayHandler.setProgress(progress, itemName);

            // Add hint text to tooltip
            var tooltip = event.getToolTip();
            if (tooltip != null && !tooltip.isEmpty()) {
                tooltip.add(net.minecraft.network.chat.Component.literal(""));

                if (progress < 0.3f) {
                    tooltip.add(net.minecraft.network.chat.Component.literal("  \u2318 按住TAB查阅该物品")
                        .withColor(0x66AAAAAA));
                } else if (progress < 0.7f) {
                    int dots = (int)(System.currentTimeMillis() / 300 % 4);
                    String loading = "\u25D4\u25D8\u25D5".substring(0, Math.min(3, dots + 1));
                    tooltip.add(net.minecraft.network.chat.Component.literal("  " + loading + " 查询中 " + Math.round(progress * 100) + "%")
                        .withColor(0x88FFFF55));
                } else {
                    int dots = (int)(System.currentTimeMillis() / 200 % 4);
                    String loading = "\u25D4\u25D8\u25D5".substring(0, Math.min(3, dots + 1));
                    tooltip.add(net.minecraft.network.chat.Component.literal("  " + loading + " 即将打开 " + Math.round(progress * 100) + "%")
                        .withColor(0xAA55FF55));
                }
            }

            if (holdTime >= HOLD_THRESHOLD_MS && !longPressTriggered) {
                longPressTriggered = true;
                // Play sound
                var mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.MASTER, 0.8f, 1.5f);
                }
                mc.tell(() ->
                    mc.setScreen(
                        new OmnisearchScreen(getRepository(), httpClient != null ? httpClient::downloadImageBytes : null, itemName))
                );
            }
        } else {
            if (!lastHoveredStack.isEmpty()) {
                // Reset overlay when TAB is released
                HudOverlayHandler.resetProgress();
                lastHoveredStack = ItemStack.EMPTY;
                tabHoldStartTime = 0;
                longPressTriggered = false;
            }
        }
    }
}
