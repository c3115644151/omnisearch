package com.cy311.omnisearch.client.render;

import com.cy311.omnisearch.data.model.SearchHit;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import java.util.List;

// verified: GuiGraphics fill/hLine/vLine signatures from lexxie.dev NeoForge 1.21.1 javadoc 2026-06-14
// verified: Font.width(String) from lexxie.dev NeoForge 1.21.1 2026-06-14

/**
 * Dark-background result list with scrolling, styled after MC container inventories.
 * <p>
 * Each row displays: 16x16 icon placeholder + item name + source mod (gray).
 * Hovered/selected rows are highlighted with a white dashed-style border.
 */
public class ResultListWidget {

    private static final int DARK_BG = 0xFF1A1A1A;
    private static final int INNER_BORDER_TOP_LEFT = 0xFF555555;
    private static final int INNER_BORDER_BOTTOM_RIGHT = 0xFFAAAAAA;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_SOURCE_MOD = 0xFFAAAAAA;
    private static final int HIGHLIGHT_BORDER = 0xFFFFFFFF;
    private static final int SCROLLBAR_BG = 0xFF333333;
    private static final int SCROLLBAR_THUMB = 0xFF6C6C6C;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int ICON_SIZE = 16;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_PADDING_X = 4;
    private static final int ROW_PADDING_Y = 2;
    private static final int TEXT_ICON_GAP = 4;

    private final Font font;

    public ResultListWidget(Font font) {
        this.font = font;
    }

    /**
     * Renders the result list.
     *
     * @param gui           the GuiGraphics instance
     * @param x             left edge of the list area
     * @param y             top edge of the list area
     * @param width         width of the list area
     * @param height        height of the list area
     * @param results       search results to display
     * @param selectedIndex index of the currently selected row, or -1 if none
     * @param scrollOffset  number of rows scrolled past the top
     * @return the total rendered height of the list content
     */
    public int render(GuiGraphics gui, int x, int y, int width, int height,
                      List<SearchHit> results, int selectedIndex, int scrollOffset) {

        // ---- Background ----
        gui.fill(x, y, x + width, y + height, DARK_BG);

        // ---- Inner double border ----
        // Top and left: dark (inner shadow)
        gui.hLine(x, x + width - 1, y, INNER_BORDER_TOP_LEFT);
        gui.vLine(x, y, y + height - 1, INNER_BORDER_TOP_LEFT);
        // Bottom and right: light (inner highlight)
        gui.hLine(x, x + width - 1, y + height - 1, INNER_BORDER_BOTTOM_RIGHT);
        gui.vLine(x + width - 1, y, y + height - 1, INNER_BORDER_BOTTOM_RIGHT);

        // ---- Clip region for scrolling ----
        int contentX = x + 1;
        int contentY = y + 1;
        int contentWidth = width - 2 - SCROLLBAR_WIDTH;
        int contentHeight = height - 2;

        gui.enableScissor(contentX, contentY, contentX + contentWidth, contentY + contentHeight); // verified: enableScissor(int,int,int,int) from lexxie.dev 2026-06-14

        // ---- Render visible rows ----
        int visibleRows = contentHeight / ROW_HEIGHT;
        int startRow = Math.max(0, scrollOffset);
        int endRow = Math.min(results.size(), startRow + visibleRows + 1);

        for (int i = startRow; i < endRow; i++) {
            int rowY = contentY + (i - scrollOffset) * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT < contentY || rowY > contentY + contentHeight) {
                continue;
            }

            SearchHit hit = results.get(i);
            boolean isSelected = (i == selectedIndex);

            // ---- Row highlight (white dashed-style border) ----
            if (isSelected) {
                drawHighlightBorder(gui, contentX, rowY, contentWidth, ROW_HEIGHT);
            }

            // ---- Icon placeholder (16x16 gray box) ----
            int iconX = contentX + ROW_PADDING_X;
            int iconY = rowY + ROW_PADDING_Y;
            gui.fill(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, 0xFF666666);
            // Inner lighter box to suggest icon area
            gui.fill(iconX + 2, iconY + 2, iconX + ICON_SIZE - 2, iconY + ICON_SIZE - 2, 0xFF444444);

            // ---- Name text ----
            int textX = iconX + ICON_SIZE + TEXT_ICON_GAP;
            int textY = rowY + (ROW_HEIGHT - font.lineHeight) / 2;
            gui.drawString(font, hit.name(), textX, textY, TEXT_WHITE, false); // verified: drawString(Font,String,int,int,int,boolean) from lexxie.dev 2026-06-14

            // ---- Source mod text (gray, right-aligned) ----
            String sourceText = hit.sourceMod();
            int sourceWidth = font.width(sourceText);
            int sourceX = contentX + contentWidth - ROW_PADDING_X - sourceWidth;
            gui.drawString(font, sourceText, sourceX, textY, TEXT_SOURCE_MOD, false);
        }

        gui.disableScissor(); // verified: disableScissor() from lexxie.dev 2026-06-14

        // ---- Scrollbar ----
        drawScrollbar(gui, x + width - SCROLLBAR_WIDTH, y + 1, SCROLLBAR_WIDTH, height - 2,
                results.size(), visibleRows, scrollOffset);

        return results.size() * ROW_HEIGHT;
    }

    private void drawHighlightBorder(GuiGraphics gui, int x, int y, int width, int height) {
        // Draw a white dashed-style highlight using hLine/vLine
        gui.hLine(x, x + width - 1, y, HIGHLIGHT_BORDER);
        gui.hLine(x, x + width - 1, y + height - 1, HIGHLIGHT_BORDER);
        gui.vLine(x, y, y + height - 1, HIGHLIGHT_BORDER);
        gui.vLine(x + width - 1, y, y + height - 1, HIGHLIGHT_BORDER);
    }

    private void drawScrollbar(GuiGraphics gui, int x, int y, int width, int height,
                               int totalRows, int visibleRows, int scrollOffset) {
        if (totalRows <= 0) return;

        // Background track
        gui.fill(x, y, x + width, y + height, SCROLLBAR_BG);

        // Thumb (sliding indicator)
        float thumbRatio = Math.min(1.0f, (float) visibleRows / totalRows);
        int thumbHeight = Math.max(8, (int) (height * thumbRatio));
        float scrollMax = Math.max(1, totalRows - visibleRows);
        float scrollFraction = (float) scrollOffset / scrollMax;
        int thumbY = y + (int) ((height - thumbHeight) * scrollFraction);

        gui.fill(x + 1, thumbY, x + width - 1, thumbY + thumbHeight, SCROLLBAR_THUMB);
    }

    /**
     * Returns the row index at the given mouse Y position relative to the list area.
     *
     * @param mouseY       mouse Y in screen coordinates
     * @param listY        top edge of the list area
     * @param scrollOffset current scroll offset
     * @return row index, or -1 if outside the list
     */
    public int getRowAt(int mouseY, int listY, int scrollOffset) {
        int relativeY = mouseY - listY - 1;
        if (relativeY < 0) return -1;
        int row = relativeY / ROW_HEIGHT + scrollOffset;
        return row;
    }
}
