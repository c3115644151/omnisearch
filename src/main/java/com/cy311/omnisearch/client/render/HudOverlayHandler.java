package com.cy311.omnisearch.client.render;

import com.cy311.omnisearch.OmnisearchMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Tracks item-hover hold progress and renders a progress ring overlay near the cursor.
 * <p>
 * State is set externally by {@link com.cy311.omnisearch.client.event.TooltipEventHandler}
 * during TAB hold detection, and rendered each frame as a GUI overlay layer.
 */
public final class HudOverlayHandler {

    private static final ResourceLocation RING_BG =
        ResourceLocation.fromNamespaceAndPath(OmnisearchMod.MOD_ID, "textures/gui/progress_ring_bg");
    private static final ResourceLocation RING_FILL =
        ResourceLocation.fromNamespaceAndPath(OmnisearchMod.MOD_ID, "textures/gui/progress_ring_fill");
    private static final ResourceLocation CHECK =
        ResourceLocation.fromNamespaceAndPath(OmnisearchMod.MOD_ID, "textures/gui/search_ready");

    // ── Shared state (set by TooltipEventHandler) ──

    /** 0.0 ~ 1.0 hold progress; -1 when inactive */
    private static volatile float holdProgress = -1F;
    /** The display name of the item being held on */
    private static volatile String targetItemName = "";

    public static void setProgress(float progress, String itemName) {
        holdProgress = Math.max(0, Math.min(1, progress));
        targetItemName = itemName != null ? itemName : "";
    }

    public static void resetProgress() {
        holdProgress = -1F;
        targetItemName = "";
    }

    /** Returns the current progress for tooltip rendering (0~100, -1 if inactive). */
    public static int getProgressPercent() {
        return holdProgress < 0 ? -1 : Math.round(holdProgress * 100);
    }

    // ── Overlay registration ──

    public static void onRegister(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
            ResourceLocation.fromNamespaceAndPath(OmnisearchMod.MOD_ID, "search_progress"),
            HudOverlayHandler::render
        );
    }

    // ── Rendering ──

    private static void render(GuiGraphics gui, DeltaTracker delta) {
        float progress = holdProgress;
        if (progress < 0) return;

        var mc = Minecraft.getInstance();
        if (mc.screen != null) return; // don't render over full screens

        int mx = (int) mc.mouseHandler.xpos();
        int my = (int) mc.mouseHandler.ypos();
        // Scale mouse coordinates to GUI scale
        double scale = mc.getWindow().getGuiScale();
        int guiX = (int) (mx / scale);
        int guiY = (int) (my / scale);

        // Ring position: offset 20px right and 12px down from cursor
        int rx = guiX + 14;
        int ry = guiY + 8;

        gui.pose().pushPose();
        gui.pose().translate(0, 0, 200); // ensure on top

        // Background ring (faint)
        gui.blit(RING_BG, rx, ry, 0, 0, 32, 32, 32, 32);

        // Fill ring clipped to progress
        int fillHeight = Math.round(32 * progress);
        if (fillHeight > 0) {
            // Blit only the top portion of the fill texture to represent progress
            gui.blit(RING_FILL, rx, ry + 32 - fillHeight,
                0, 32 - fillHeight, 32, fillHeight, 32, 32);
        }

        // Completed state: show checkmark with a brief pulse
        if (progress >= 1F) {
            int alpha = (int) (160 + 95 * Math.sin(System.currentTimeMillis() * 0.008));
            gui.setColor(1, 1, 1, Math.min(1, alpha / 255F));
            gui.blit(CHECK, rx + 8, ry + 8, 0, 0, 16, 16, 16, 16);
            gui.setColor(1, 1, 1, 1);
        }

        // Item name label below the ring
        if (!targetItemName.isEmpty()) {
            var font = mc.font;
            if (font != null) {
                int tw = font.width(targetItemName);
                int tx = rx + 16 - tw / 2;
                int ty = ry + 34;
                // Shadow
                gui.drawString(font, targetItemName, tx + 1, ty + 1, 0x44000000);
                gui.drawString(font, targetItemName, tx, ty, 0x88FFFFFF);
            }
        }

        gui.pose().popPose();
    }
}
