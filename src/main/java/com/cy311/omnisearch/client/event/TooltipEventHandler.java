package com.cy311.omnisearch.client.event;

import com.cy311.omnisearch.OmnisearchMod;
import com.cy311.omnisearch.client.screen.OmnisearchScreen;
import com.cy311.omnisearch.data.repository.CacheLayer;
import com.cy311.omnisearch.data.repository.SearchRepository;
import com.cy311.omnisearch.data.source.McmodDataSource;
import com.cy311.omnisearch.data.client.McmodHttpClient;
import com.cy311.omnisearch.keybinds.KeyBinds;
import com.mojang.blaze3d.platform.InputConstants;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.fml.ModList;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Low-interference hover query:
 * <ul>
 *   <li>Use the registered {@link net.minecraft.client.KeyMapping} so the shortcut is fully rebindable.</li>
 *   <li>In game HUD: click the keybind to open a blank Omnisearch screen.</li>
 *   <li>Inside GUIs: hold the same key while hovering an item to open its detail page.</li>
 * </ul>
 * Tooltip stays quiet by default, but shows a session-scoped education hint
 * until the player successfully uses the hover query once.
 */
@EventBusSubscriber(modid = OmnisearchMod.MOD_ID, value = Dist.CLIENT)
public class TooltipEventHandler {
    private static final int EDUCATION_COLOR = 0x8A8A8A;
    private static final int BRAND_COLOR = 0xAFAFAF;
    private static final int CANCEL_COLOR = 0x8A8A8A;
    private static final int PROGRESS_COLD = 0xCFCFCF;
    private static final int PROGRESS_WARM = 0xB8D8B8;

    private static long tabHoldStartTime = 0;
    private static long lastTooltipSeenTime = 0;
    private static ItemStack holdTarget = ItemStack.EMPTY;
    private static boolean longPressTriggered = false;
    private static boolean guiInspectKeyHeld = false;
    private static boolean hoverEducationCompletedThisSession = false;

    private static final long HOVER_STALE_MS = 120;
    private static final long HOLD_THRESHOLD_MS = 1200;

    private static SearchRepository repository;
    private static McmodHttpClient httpClient;

    private static synchronized SearchRepository getRepository() {
        if (repository == null) {
            var mc = Minecraft.getInstance();
            if (mc == null) return null;
            var cacheDir = mc.gameDirectory.toPath().resolve(".omnisearch/cache");
            httpClient = new McmodHttpClient();
            repository = new SearchRepository(new CacheLayer(cacheDir), new McmodDataSource(httpClient));
        }
        return repository;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }

        // verified: official NeoForge key mappings docs recommend consumeClick() in ClientTickEvent.Post
        // source: https://docs.neoforged.net/docs/1.21.1/misc/keymappings/
        while (KeyBinds.openSearch.consumeClick()) {
            if (mc.screen == null) {
                openBlankSearch();
            }
        }

        if (mc.screen == null) {
            guiInspectKeyHeld = false;
        }

        if (mc.screen != null && !guiInspectKeyHeld) {
            clearHoldState();
        }

        if (System.currentTimeMillis() - lastTooltipSeenTime > HOVER_STALE_MS
                && !(mc.screen != null && guiInspectKeyHeld && !holdTarget.isEmpty())) {
            clearHoverState();
        }
    }

    @SubscribeEvent
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        var key = InputConstants.getKey(event.getKeyCode(), event.getScanCode());
        if (!KeyBinds.inspectHoveredItem.isActiveAndMatches(key)) {
            return;
        }

        // verified: ScreenEvent.KeyPressed.Pre is cancellable and bypasses normal screen handling
        // source: ScreenEvent.KeyPressed.Pre Javadoc 1.21.x, checked 2026-08-17
        if (hasRecentHover()) {
            guiInspectKeyHeld = true;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenKeyReleased(ScreenEvent.KeyReleased.Pre event) {
        var key = InputConstants.getKey(event.getKeyCode(), event.getScanCode());
        if (!KeyBinds.inspectHoveredItem.isActiveAndMatches(key)) {
            return;
        }

        if (guiInspectKeyHeld) {
            guiInspectKeyHeld = false;
            clearHoldState();
            event.setCanceled(true);
        }
    }

    private static void openBlankSearch() {
        Minecraft.getInstance().tell(() -> {
            var mc = Minecraft.getInstance();
            if (mc == null) return;
            mc.setScreen(new OmnisearchScreen(getRepository(),
                httpClient != null ? httpClient::downloadImageBytes : null));
        });
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public static void onItemTooltip(ItemTooltipEvent event) {
        var mc = Minecraft.getInstance();
        ItemStack stack = event.getItemStack();
        if (mc == null || stack == null || stack.isEmpty()) {
            return;
        }
        var tooltip = event.getToolTip();
        if (tooltip == null) {
            return;
        }

        long now = System.currentTimeMillis();
        lastTooltipSeenTime = now;

        if (mc.screen == null) {
            clearHoldState();
            return;
        }

        if (!guiInspectKeyHeld) {
            clearHoldState();
            appendEducationHint(tooltip);
            return;
        }

        if (holdTarget.isEmpty() || !ItemStack.isSameItemSameComponents(holdTarget, stack)) {
            holdTarget = stack.copy();
            tabHoldStartTime = System.currentTimeMillis();
            longPressTriggered = false;
        }

        if (tabHoldStartTime == 0L) {
            tabHoldStartTime = now;
        }

        long holdTime = now - tabHoldStartTime;
        float progress = Math.min(1f, (float) holdTime / HOLD_THRESHOLD_MS);
        appendHoldFeedback(tooltip, progress);

        if (holdTime >= HOLD_THRESHOLD_MS && !longPressTriggered) {
            longPressTriggered = true;
            clearHoldState();
            performHoverSearch(stack, stack.getHoverName().getString());
        }
    }

    private static void clearHoldState() {
        holdTarget = ItemStack.EMPTY;
        tabHoldStartTime = 0;
        longPressTriggered = false;
    }

    private static void clearHoverState() {
        clearHoldState();
    }

    private static boolean hasRecentHover() {
        return !holdTarget.isEmpty() || System.currentTimeMillis() - lastTooltipSeenTime <= HOVER_STALE_MS;
    }

    private static void appendHoldFeedback(java.util.List<Component> tooltip, float progress) {
        int segments = 12;
        int filled = Math.max(0, Math.min(segments, Math.round(segments * progress)));
        int pct = Math.round(progress * 100F);

        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < segments; i++) {
            bar.append(i < filled ? '=' : '-');
        }
        bar.append("] ").append(pct).append('%');

        tooltip.add(Component.empty());
        tooltip.add(Component.literal("Omnisearch").withColor(BRAND_COLOR));
        tooltip.add(Component.literal(bar.toString()).withColor(progress < 0.75F ? PROGRESS_COLD : PROGRESS_WARM));
        if (progress < 1F) {
            tooltip.add(Component.literal("松开取消").withColor(CANCEL_COLOR));
        }
    }

    private static void appendEducationHint(java.util.List<Component> tooltip) {
        if (hoverEducationCompletedThisSession) {
            return;
        }

        String keyName = KeyBinds.inspectHoveredItem.getTranslatedKeyMessage().getString();
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("hint.omnisearch.education", keyName).withColor(EDUCATION_COLOR));
    }

    private static void performHoverSearch(ItemStack stack, String displayName) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;

        hoverEducationCompletedThisSession = true;
        mc.player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.MASTER, 0.8f, 1.5f);
        String modFilter = resolveSourceModName(stack);

        mc.tell(() -> {
            var screen = new OmnisearchScreen(
                getRepository(),
                httpClient != null ? httpClient::downloadImageBytes : null,
                displayName,
                modFilter
            );
            mc.setScreen(screen);
        });
    }

    @Nullable
    private static String resolveSourceModName(ItemStack stack) {
        try {
            var mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) return null;
            var itemRegistry = mc.player.registryAccess().registryOrThrow(Registries.ITEM);
            ResourceLocation itemId = itemRegistry.getKey(stack.getItem());
            if (itemId != null && !itemId.getNamespace().equals("minecraft")) {
                var container = ModList.get().getModContainerById(itemId.getNamespace());
                if (container.isPresent()) {
                    return container.get().getModInfo().getDisplayName();
                }
            }
        } catch (Exception e) {
            OmnisearchMod.LOGGER.debug("[HoverSearch] mod lookup failed for {}", stack.getHoverName().getString(), e);
        }
        return null;
    }
}
