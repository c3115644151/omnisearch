package com.cy311.omnisearch.client.render;

import net.minecraft.client.gui.Font;

/**
 * Shared text utility for truncating text with ellipsis.
 * Used by ResultListWidget, DetailPanelWidget, OmnisearchScreen, etc.
 */
public final class TextUtils {

    private TextUtils() {}

    /**
     * Truncates text to fit within maxWidth, appending "..." if truncated.
     * Uses binary search for the longest prefix that fits.
     */
    public static String truncateWithEllipsis(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        if (maxWidth <= ellipsisWidth) return ellipsis;
        int lo = 0, hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (font.width(text.substring(0, mid)) + ellipsisWidth <= maxWidth) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return text.substring(0, lo) + ellipsis;
    }
}
