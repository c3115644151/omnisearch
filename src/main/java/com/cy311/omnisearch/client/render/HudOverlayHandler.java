package com.cy311.omnisearch.client.render;

import com.cy311.omnisearch.OmnisearchMod;
import com.cy311.omnisearch.keybinds.KeyBinds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Renders a low-interference hover query affordance:
 * <ul>
 *   <li>A once-only education pill when the player first hovers an item in a GUI.</li>
 *   <li>A small Omnisearch bar while the query key is held.</li>
 * </ul>
 */
@EventBusSubscriber(modid = OmnisearchMod.MOD_ID, value = Dist.CLIENT)
public final class HudOverlayHandler {
    private HudOverlayHandler() {
    }

    private static final long HOVER_STALE_MS = 120;
    private static final long EDUCATION_DELAY_MS = 450;
    private static final long EDUCATION_DURATION_MS = 2200;

    private static final String STATE_FILE = "client-ui.properties";
    private static final String KEY_EDUCATION_SEEN = "hoverEducationSeen";

    private static volatile float holdProgress = -1F;
    private static ItemStack hoveredStack = ItemStack.EMPTY;
    private static long hoverStartTime = 0L;
    private static long lastHoverSeenTime = 0L;
    private static long educationVisibleSince = 0L;
    private static boolean educationLoaded = false;
    private static boolean educationCompleted = false;

    public static void noteHoveredItem(ItemStack stack, long now) {
        if (stack == null || stack.isEmpty()) {
            clearHover();
            return;
        }
        if (hoveredStack.isEmpty() || !ItemStack.isSameItemSameComponents(hoveredStack, stack)) {
            hoveredStack = stack.copy();
            hoverStartTime = now;
            educationVisibleSince = 0L;
        }
        lastHoverSeenTime = now;
    }

    public static void clearHover() {
        hoveredStack = ItemStack.EMPTY;
        hoverStartTime = 0L;
        lastHoverSeenTime = 0L;
        educationVisibleSince = 0L;
        resetProgress();
    }

    public static void setProgress(float progress) {
        holdProgress = Math.max(0, Math.min(1, progress));
    }

    public static void resetProgress() {
        holdProgress = -1F;
    }

    public static void markEducationCompleted() {
        ensureEducationStateLoaded();
        if (!educationCompleted) {
            educationCompleted = true;
            saveEducationState();
        }
    }

    @SubscribeEvent
    public static void onContainerForeground(ContainerScreenEvent.Render.Foreground event) {
        var mc = Minecraft.getInstance();
        if (mc.getWindow() == null || mc.font == null) return;
        ensureEducationStateLoaded();
        clearStaleHoverState();

        Screen screen = mc.screen;
        boolean hasHover = !hoveredStack.isEmpty();

        if (!hasHover && holdProgress < 0F) {
            return;
        }

        GuiGraphics gui = event.getGuiGraphics();
        gui.pose().pushPose();
        gui.pose().translate(0, 0, 200);

        if (!educationCompleted && screen != null && hasHover && holdProgress < 0F) {
            renderEducation(gui, mc, event.getMouseX(), event.getMouseY(), System.currentTimeMillis());
        }

        float progress = holdProgress;
        if (progress >= 0F && screen != null && hasHover) {
            renderHoldBar(gui, mc, event.getMouseX(), event.getMouseY(), progress);
        }

        gui.pose().popPose();
    }

    private static void renderHoldBar(GuiGraphics gui, Minecraft mc, int mx, int my, float progress) {
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int barWidth = 38;
        int barHeight = 4;
        int boxPad = 2;

        int x = clamp(mx + 12, 6, screenWidth - (barWidth + boxPad * 2) - 6);
        int y = clamp(my + 18, 6, screenHeight - (barHeight + boxPad * 2) - 6);
        int fillWidth = Math.max(1, Math.round(barWidth * progress));
        int fillColor = progress < 0.5F ? 0xFFB8B28A : (progress < 0.85F ? 0xFF9DBBB2 : 0xFF98B2E8);

        gui.fill(x, y, x + barWidth + boxPad * 2, y + barHeight + boxPad * 2, 0xC0101010);
        gui.hLine(x, x + barWidth + boxPad * 2 - 1, y, 0xFF5A5A5A);
        gui.vLine(x, y, y + barHeight + boxPad * 2 - 1, 0xFF5A5A5A);
        gui.hLine(x, x + barWidth + boxPad * 2 - 1, y + barHeight + boxPad * 2 - 1, 0xFFF0F0F0);
        gui.vLine(x + barWidth + boxPad * 2 - 1, y, y + barHeight + boxPad * 2 - 1, 0xFFF0F0F0);
        gui.fill(x + boxPad, y + boxPad, x + boxPad + barWidth, y + boxPad + barHeight, 0xFF2A2A2A);
        gui.fill(x + boxPad, y + boxPad, x + boxPad + fillWidth, y + boxPad + barHeight, fillColor);
    }

    private static void renderEducation(GuiGraphics gui, Minecraft mc, int mx, int my, long now) {
        if (hoverStartTime == 0L || now - hoverStartTime < EDUCATION_DELAY_MS) {
            educationVisibleSince = 0L;
            return;
        }

        if (educationVisibleSince == 0L) {
            educationVisibleSince = now;
        }

        long age = now - educationVisibleSince;
        if (age >= EDUCATION_DURATION_MS) {
            markEducationCompleted();
            return;
        }

        float fadeIn = Math.min(1F, age / 180F);
        float fadeOut = age > EDUCATION_DURATION_MS - 240 ? Math.max(0F, (EDUCATION_DURATION_MS - age) / 240F) : 1F;
        float alpha = fadeIn * fadeOut;

        String keyName = KeyBinds.inspectHoveredItem.getTranslatedKeyMessage().getString();
        String text = Component.translatable("hint.omnisearch.education", keyName).getString();
        int textWidth = mc.font.width(text);
        int padX = 8;
        int padY = 5;
        int boxWidth = textWidth + padX * 2;
        int boxHeight = mc.font.lineHeight + padY * 2;
        int x = clamp(mx + 14, 6, mc.getWindow().getGuiScaledWidth() - boxWidth - 6);
        int y = clamp(my + 18, 6, mc.getWindow().getGuiScaledHeight() - boxHeight - 6);
        int bg = withAlpha(0xE611141A, alpha);
        int border = withAlpha(0x66D7E2FF, alpha);
        int textColor = withAlpha(0xFFDCE5F6, alpha);

        gui.fill(x, y, x + boxWidth, y + boxHeight, bg);
        gui.hLine(x, x + boxWidth - 1, y, border);
        gui.hLine(x, x + boxWidth - 1, y + boxHeight - 1, border);
        gui.vLine(x, y, y + boxHeight - 1, border);
        gui.vLine(x + boxWidth - 1, y, y + boxHeight - 1, border);
        gui.drawString(mc.font, text, x + padX, y + padY, textColor, false);
    }

    private static void clearStaleHoverState() {
        if (!hoveredStack.isEmpty() && System.currentTimeMillis() - lastHoverSeenTime > HOVER_STALE_MS) {
            clearHover();
        }
    }

    private static void ensureEducationStateLoaded() {
        if (educationLoaded) {
            return;
        }

        educationLoaded = true;
        Path path = educationStatePath();
        if (!Files.exists(path)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
            educationCompleted = Boolean.parseBoolean(properties.getProperty(KEY_EDUCATION_SEEN, "false"));
        } catch (IOException e) {
            OmnisearchMod.LOGGER.debug("[HudOverlay] Failed to load client UI state", e);
        }
    }

    private static void saveEducationState() {
        Path path = educationStatePath();
        try {
            Files.createDirectories(path.getParent());
            Properties properties = new Properties();
            properties.setProperty(KEY_EDUCATION_SEEN, Boolean.toString(educationCompleted));
            try (OutputStream out = Files.newOutputStream(path)) {
                properties.store(out, "Omnisearch local client state");
            }
        } catch (IOException e) {
            OmnisearchMod.LOGGER.debug("[HudOverlay] Failed to save client UI state", e);
        }
    }

    private static Path educationStatePath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(".omnisearch").resolve(STATE_FILE);
    }

    private static int withAlpha(int argb, float alpha) {
        int a = Math.round(((argb >> 24) & 0xFF) * Math.max(0F, Math.min(1F, alpha)));
        return (argb & 0x00FFFFFF) | (a << 24);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
