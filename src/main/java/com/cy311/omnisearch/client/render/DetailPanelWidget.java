package com.cy311.omnisearch.client.render;

import com.cy311.omnisearch.data.model.ItemPage;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

// verified: GuiGraphics fill/hLine/vLine/drawString signatures from lexxie.dev NeoForge 1.21.1 javadoc 2026-06-14
// verified: Font.width(String) from lexxie.dev NeoForge 1.21.1 2026-06-14

/**
 * Full-screen detail panel for viewing an {@link ItemPage}.
 * <p>
 * Renders a semi-transparent dark background with:
 * <ul>
 *   <li>Back button + title bar at top</li>
 *   <li>Metadata section (source mod, URL)</li>
 *   <li>Content area reserved for {@code DocumentRenderer} output</li>
 * </ul>
 * <p>
 * This widget owns layout only; content rendering is delegated to DocumentRenderer.
 */
public class DetailPanelWidget {

    private static final int BG_ALPHA = 0xCC000000;
    private static final int HEADER_BG = 0xAA1A1A1A;
    private static final int TITLE_COLOR = 0xFFFFAA00;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFFAAAAAA;
    private static final int TEXT_DIM = 0xFF666666;
    private static final int BACK_BUTTON_COLOR = 0xFFFFAA00;
    private static final int DIVIDER_COLOR = 0xFF555555;

    private static final int HEADER_HEIGHT = 30;
    private static final int METADATA_HEIGHT = 50;
    private static final int BACK_BUTTON_SIZE = 20;
    private static final int PADDING = 6;

    private final Font font;

    public DetailPanelWidget(Font font) {
        this.font = font;
    }

    /**
     * Renders the detail panel.
     *
     * @param gui    the GuiGraphics instance
     * @param x      left edge of the panel
     * @param y      top edge of the panel
     * @param width  width of the panel
     * @param height height of the panel
     * @param page   the item page to display
     */
    public void render(GuiGraphics gui, int x, int y, int width, int height, ItemPage page) {
        // ---- Semi-transparent background ----
        gui.fill(x, y, x + width, y + height, BG_ALPHA);

        // ---- Header bar ----
        gui.fill(x, y, x + width, y + HEADER_HEIGHT, HEADER_BG);
        gui.hLine(x, x + width - 1, y + HEADER_HEIGHT, DIVIDER_COLOR); // verified: hLine(int,int,int,int) from lexxie.dev 2026-06-14

        // Back button (arrow)
        int backX = x + PADDING;
        int backY = y + (HEADER_HEIGHT - BACK_BUTTON_SIZE) / 2;
        drawBackButton(gui, backX, backY, BACK_BUTTON_SIZE, BACK_BUTTON_SIZE);

        // Title
        int titleX = backX + BACK_BUTTON_SIZE + PADDING;
        int titleY = y + (HEADER_HEIGHT - font.lineHeight) / 2;
        String title = page.title();
        // Truncate if too wide
        int maxTitleWidth = width - (titleX - x) - PADDING;
        if (font.width(title) > maxTitleWidth) {
            title = font.plainSubstrByWidth(title, maxTitleWidth - 3) + "..."; // verified: plainSubstrByWidth(String,int) from lexxie.dev 2026-06-14
        }
        gui.drawString(font, title, titleX, titleY, TITLE_COLOR, false); // verified: drawString(Font,String,int,int,int,boolean) from lexxie.dev 2026-06-14

        // ---- Metadata section ----
        int metaY = y + HEADER_HEIGHT + 1;
        gui.fill(x, metaY, x + width, metaY + METADATA_HEIGHT, BG_ALPHA);
        gui.hLine(x, x + width - 1, metaY + METADATA_HEIGHT, DIVIDER_COLOR);

        int metaContentX = x + PADDING + 2;
        int metaLine1Y = metaY + PADDING;
        int metaLine2Y = metaLine1Y + font.lineHeight + 2;

        // Source mod
        String sourceText = "来源: " + (page.sourceMod() != null ? page.sourceMod() : "未知");
        gui.drawString(font, sourceText, metaContentX, metaLine1Y, TEXT_GRAY, false);

        // URL
        String urlText = "链接: " + (page.url() != null ? page.url() : "无");
        gui.drawString(font, urlText, metaContentX, metaLine2Y, TEXT_DIM, false);

        // ---- Content area (for DocumentRenderer) ----
        int contentX = x + PADDING;
        int contentY = metaY + METADATA_HEIGHT + PADDING;
        int contentWidth = width - PADDING * 2;
        int contentHeight = (y + height) - contentY - PADDING;

        // Clear content area background (full dark, no transparency for readability)
        gui.fill(contentX, contentY, contentX + contentWidth, contentY + contentHeight, 0xFF0A0A0A);

        // ---- Return content area bounds for DocumentRenderer ----
        // (Caller should use getContentAreaBounds() to retrieve these values.)
    }

    /**
     * Draws a small {@code ←} back button at the given position.
     */
    private void drawBackButton(GuiGraphics gui, int x, int y, int width, int height) {
        // Border
        gui.hLine(x, x + width - 1, y, TEXT_GRAY);
        gui.hLine(x, x + width - 1, y + height - 1, TEXT_GRAY);
        gui.vLine(x, y, y + height - 1, TEXT_GRAY);
        gui.vLine(x + width - 1, y, y + height - 1, TEXT_GRAY);
        // Arrow text
        gui.drawString(font, "\u2190", x + 5, y + (height - font.lineHeight) / 2, BACK_BUTTON_COLOR, false);
    }

    /**
     * Returns the bounding rectangle for the content area (where DocumentRenderer should render).
     *
     * @param x      panel left edge
     * @param y      panel top edge
     * @param width  panel width
     * @param height panel height
     * @return int array of [contentX, contentY, contentWidth, contentHeight]
     */
    public int[] getContentAreaBounds(int x, int y, int width, int height) {
        int metaY = y + HEADER_HEIGHT + 1;
        int contentX = x + PADDING;
        int contentY = metaY + METADATA_HEIGHT + PADDING;
        int contentWidth = width - PADDING * 2;
        int contentHeight = (y + height) - contentY - PADDING;
        return new int[]{contentX, contentY, contentWidth, contentHeight};
    }
}
