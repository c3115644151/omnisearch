package com.cy311.omnisearch.client.render;

import com.cy311.omnisearch.gui.theme.OmniTheme;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Right-anchored floating window shell for the TAB search UI.
 * Handles geometry and outer chrome only; inner content is rendered by the screen.
 */
public final class FloatingSearchWindow {

    private static final float WIDTH_RATIO = 0.40f;
    private static final int MIN_WIDTH = 200;
    private static final int MAX_WIDTH = 400;
    private static final float HEIGHT_RATIO = 0.80f;
    private static final int MIN_HEIGHT = 200;
    private static final int MAX_HEIGHT = 560;

    public record Bounds(int x, int y, int width, int height) {}

    public Bounds computeBounds(int screenWidth, int screenHeight) {
        int panelWidth = Math.round(screenWidth * WIDTH_RATIO);
        panelWidth = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, panelWidth));
        panelWidth = Math.min(panelWidth, Math.max(MIN_WIDTH, screenWidth - OmniTheme.SIDE_MARGIN * 2));

        int panelHeight = Math.round(screenHeight * HEIGHT_RATIO);
        panelHeight = Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, panelHeight));
        panelHeight = Math.min(panelHeight, Math.max(MIN_HEIGHT, screenHeight - OmniTheme.SIDE_MARGIN * 2));

        int x = screenWidth - panelWidth - OmniTheme.SIDE_MARGIN;
        int y = Math.max(OmniTheme.SIDE_MARGIN, (screenHeight - panelHeight) / 2);
        return new Bounds(x, y, panelWidth, panelHeight);
    }

    public void renderShell(GuiGraphics gui, int screenWidth, int screenHeight, Bounds bounds) {
        int x = bounds.x();
        int y = bounds.y();
        int width = bounds.width();
        int height = bounds.height();

        // Very light dim
        gui.fill(0, 0, screenWidth, screenHeight, 0x10000000);
        // Window background
        gui.fill(x, y, x + width, y + height, 0xDD0A0A0A);
        // Single border
        gui.hLine(x, x + width - 1, y, OmniTheme.BORDER_LIGHT);
        gui.vLine(x, y, y + height - 1, OmniTheme.BORDER_LIGHT);
        gui.hLine(x, x + width - 1, y + height - 1, OmniTheme.BORDER);
        gui.vLine(x + width - 1, y, y + height - 1, OmniTheme.BORDER);
    }

    public int[] getSearchBarBounds(Bounds bounds, int searchBarHeight) {
        int x = bounds.x() + OmniTheme.PADDING;
        int y = bounds.y() + OmniTheme.PADDING;
        int width = bounds.width() - OmniTheme.PADDING * 2;
        return new int[]{x, y, width, searchBarHeight};
    }

    public int[] getBodyBounds(Bounds bounds, int searchBarHeight) {
        int x = bounds.x() + OmniTheme.PADDING;
        int y = bounds.y() + OmniTheme.PADDING + searchBarHeight + OmniTheme.SECTION_GAP;
        int width = bounds.width() - OmniTheme.PADDING * 2;
        int height = bounds.height() - OmniTheme.PADDING * 2 - searchBarHeight - OmniTheme.SECTION_GAP - OmniTheme.STATUS_HEIGHT - OmniTheme.SECTION_GAP;
        return new int[]{x, y, width, Math.max(1, height)};
    }

    public int[] getStatusBounds(Bounds bounds) {
        int x = bounds.x() + OmniTheme.PADDING;
        int width = bounds.width() - OmniTheme.PADDING * 2;
        int y = bounds.y() + bounds.height() - OmniTheme.PADDING - OmniTheme.STATUS_HEIGHT;
        return new int[]{x, y, width, OmniTheme.STATUS_HEIGHT};
    }
}
