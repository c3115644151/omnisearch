package com.cy311.omnisearch.client.render;

import com.cy311.omnisearch.gui.theme.OmniTheme;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Shared scrollbar used by both results list and detail content pane.
 * Supports click-to-position and drag tracking.
 */
public final class ScrollbarWidget {

    /**
     * Renders the scrollbar track and thumb.
     *
     * @param scrollFraction current scroll position [0, 1]
     * @param thumbRatio     visible content / total content ratio [0, 1]
     */
    public void render(GuiGraphics gui, int x, int y, int height, float scrollFraction, float thumbRatio) {
        if (height <= 0) return;
        int width = OmniTheme.SCROLLBAR_WIDTH;
        // Track
        gui.fill(x, y, x + width, y + height, OmniTheme.BG_SCROLLBAR_TRACK);
        // Thumb
        int thumbH = calcThumbHeight(height, thumbRatio);
        int maxThumbTravel = height - thumbH;
        int thumbY = y + (int) (maxThumbTravel * Math.max(0, Math.min(1, scrollFraction)));
        gui.fill(x + 1, thumbY, x + width - 1, thumbY + thumbH, OmniTheme.BG_SCROLLBAR_THUMB);
    }

    /**
     * Converts a click Y position to a scroll fraction [0, 1].
     * Uses the same thumbRatio as render() for consistent thumb positioning.
     */
    public float clickToFraction(int clickY, int barY, int barHeight, float thumbRatio) {
        if (barHeight <= 0) return 0;
        int thumbH = calcThumbHeight(barHeight, thumbRatio);
        int maxTravel = barHeight - thumbH;
        if (maxTravel <= 0) return 0;
        float frac = (float) (clickY - barY - thumbH / 2f) / maxTravel;
        return Math.max(0, Math.min(1, frac));
    }

    /**
     * Calculates thumb height consistently between render and click detection.
     */
    private static int calcThumbHeight(int barHeight, float thumbRatio) {
        return Math.max(8, (int) (barHeight * Math.min(1, Math.max(0.1f, thumbRatio))));
    }
}
