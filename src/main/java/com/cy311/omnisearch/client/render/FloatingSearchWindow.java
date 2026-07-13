package com.cy311.omnisearch.client.render;

import com.cy311.omnisearch.gui.theme.OmniTheme;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Right-anchored floating window shell for the TAB search UI.
 * Handles geometry and outer chrome only; inner content is rendered by the screen.
 */
public final class FloatingSearchWindow {

    private static final int OUTER_MARGIN = 16;
    private static final int TOP_MARGIN = 20;
    private static final int MIN_WIDTH = 420;
    private static final int MAX_WIDTH = 520;
    private static final int MIN_HEIGHT = 220;
    private static final int STATUS_HEIGHT = 24;
    private static final int SECTION_GAP = 8;

    public record Bounds(int x, int y, int width, int height) {}

    public Bounds computeBounds(int screenWidth, int screenHeight) {
        int panelWidth = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, Math.round(screenWidth * 0.34f)));
        int panelHeight = Math.max(MIN_HEIGHT, screenHeight - TOP_MARGIN * 2);
        int x = screenWidth - panelWidth - OUTER_MARGIN;
        int y = TOP_MARGIN;
        return new Bounds(x, y, panelWidth, panelHeight);
    }

    public void renderShell(GuiGraphics gui, int screenWidth, int screenHeight, Bounds bounds) {
        int x = bounds.x();
        int y = bounds.y();
        int width = bounds.width();
        int height = bounds.height();

        gui.fill(0, 0, screenWidth, screenHeight, 0x22000000);
        gui.fill(x, y, x + width, y + height, OmniTheme.BG_DARK);
        gui.fill(x, y, x + width, y + height, 0xAA101010);

        gui.hLine(x, x + width - 1, y, OmniTheme.BORDER_LIGHT);
        gui.vLine(x, y, y + height - 1, OmniTheme.BORDER_LIGHT);
        gui.hLine(x, x + width - 1, y + height - 1, OmniTheme.BORDER);
        gui.vLine(x + width - 1, y, y + height - 1, OmniTheme.BORDER);

        gui.hLine(x + 1, x + width - 2, y + 1, OmniTheme.BORDER);
        gui.vLine(x + 1, y + 1, y + height - 2, OmniTheme.BORDER);
        gui.hLine(x + 1, x + width - 2, y + height - 2, OmniTheme.BORDER_LIGHT);
        gui.vLine(x + width - 2, y + 1, y + height - 2, OmniTheme.BORDER_LIGHT);
    }

    public int[] getSearchBarBounds(Bounds bounds, int searchBarHeight) {
        int x = bounds.x() + OmniTheme.PADDING;
        int y = bounds.y() + OmniTheme.PADDING;
        int width = bounds.width() - OmniTheme.PADDING * 2;
        return new int[]{x, y, width, searchBarHeight};
    }

    public int[] getBodyBounds(Bounds bounds, int searchBarHeight) {
        int x = bounds.x() + OmniTheme.PADDING;
        int y = bounds.y() + OmniTheme.PADDING + searchBarHeight + SECTION_GAP;
        int width = bounds.width() - OmniTheme.PADDING * 2;
        int height = bounds.height() - OmniTheme.PADDING * 2 - searchBarHeight - SECTION_GAP - STATUS_HEIGHT - SECTION_GAP;
        return new int[]{x, y, width, Math.max(1, height)};
    }

    public int[] getStatusBounds(Bounds bounds) {
        int x = bounds.x() + OmniTheme.PADDING;
        int width = bounds.width() - OmniTheme.PADDING * 2;
        int y = bounds.y() + bounds.height() - OmniTheme.PADDING - STATUS_HEIGHT;
        return new int[]{x, y, width, STATUS_HEIGHT};
    }
}
