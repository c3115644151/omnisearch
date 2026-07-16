package com.cy311.omnisearch.client.render;

import com.cy311.omnisearch.data.model.SearchHit;
import com.cy311.omnisearch.gui.theme.OmniTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.List;

/**
 * Dark-background result list with scrolling.
 * Each row displays: [category] name ... sourceMod (gray, right-aligned).
 * Hovered/selected rows are highlighted.
 * Source mod is truncated if too long; full text shown via tooltip on hover.
 */
public class ResultListWidget {

    private static final int MOD_MAX_RATIO = 3; // source mod max width = contentWidth / 3

    private final Font font;
    private final ScrollbarWidget scrollbar = new ScrollbarWidget();

    public ResultListWidget(Font font) {
        this.font = font;
    }

    public int render(GuiGraphics gui, int x, int y, int width, int height,
                      List<SearchHit> results, int selectedIndex, int scrollOffset,
                      int mouseX, int mouseY, int contentRightX) {

        gui.fill(x, y, x + width, y + height, OmniTheme.BG_DARK);

        gui.hLine(x, x + width - 1, y, OmniTheme.BORDER_LIGHT);
        gui.vLine(x, y, y + height - 1, OmniTheme.BORDER_LIGHT);
        gui.hLine(x, x + width - 1, y + height - 1, OmniTheme.BORDER);
        gui.vLine(x + width - 1, y, y + height - 1, OmniTheme.BORDER);

        int contentX = x + 1;
        int contentY = y + 1;
        int contentWidth = width - 2 - OmniTheme.SCROLLBAR_WIDTH;
        int contentHeight = height - 2;

        gui.enableScissor(contentX, contentY, contentX + contentWidth, contentY + contentHeight);

        int visibleRows = contentHeight / OmniTheme.LIST_ITEM_HEIGHT;
        int startRow = Math.max(0, scrollOffset);
        int endRow = Math.min(results.size(), startRow + visibleRows + 1);

        String hoveredTooltipText = null;
        int hoveredTooltipX = 0;
        int hoveredTooltipY = 0;

        for (int i = startRow; i < endRow; i++) {
            int rowY = contentY + (i - scrollOffset) * OmniTheme.LIST_ITEM_HEIGHT;
            if (rowY + OmniTheme.LIST_ITEM_HEIGHT < contentY || rowY > contentY + contentHeight) continue;

            SearchHit hit = results.get(i);
            boolean isSelected = (i == selectedIndex);

            // Hover effect - only when mouseX is within content area
            boolean rowHovered = mouseX >= contentX && mouseX <= contentRightX
                    && mouseY >= rowY && mouseY < rowY + OmniTheme.LIST_ITEM_HEIGHT;
            if (rowHovered) {
                gui.fill(contentX, rowY, contentX + contentWidth, rowY + OmniTheme.LIST_ITEM_HEIGHT, OmniTheme.BG_HOVER);
            }

            // Selection highlight border
            if (isSelected) {
                gui.hLine(contentX, contentX + contentWidth - 1, rowY, OmniTheme.TEXT_WHITE);
                gui.hLine(contentX, contentX + contentWidth - 1, rowY + OmniTheme.LIST_ITEM_HEIGHT - 1, OmniTheme.TEXT_WHITE);
                gui.vLine(contentX, rowY, rowY + OmniTheme.LIST_ITEM_HEIGHT - 1, OmniTheme.TEXT_WHITE);
                gui.vLine(contentX + contentWidth - 1, rowY, rowY + OmniTheme.LIST_ITEM_HEIGHT - 1, OmniTheme.TEXT_WHITE);
            }

            // Category tag prefix from parsed search result
            String tagText = hit.category();
            if (tagText != null && !tagText.isEmpty()) {
                tagText = "(" + tagText + ")";
            }

            int textX = contentX + OmniTheme.ROW_PADDING_X;
            int textY = rowY + (OmniTheme.LIST_ITEM_HEIGHT - font.lineHeight) / 2;
            int rightEdge = contentX + contentWidth - OmniTheme.ROW_PADDING_X;

            // Source mod (right-aligned) - truncate mod name, not item name
            String sourceText = hit.sourceMod();
            boolean sourceTruncated = false;
            int sourceWidth = 0;
            int maxModWidth = contentWidth / MOD_MAX_RATIO;

            if (sourceText != null && !sourceText.isEmpty()) {
                sourceWidth = font.width(sourceText);
                if (sourceWidth > maxModWidth) {
                    sourceText = TextUtils.truncateWithEllipsis(font, sourceText, maxModWidth);
                    sourceWidth = font.width(sourceText);
                    sourceTruncated = true;
                }
            }

            // Draw category tag (left, gray) - cap at 30% of content width
            if (tagText != null) {
                int maxTagWidth = contentWidth * 3 / 10;
                if (font.width(tagText) > maxTagWidth) {
                    tagText = TextUtils.truncateWithEllipsis(font, tagText, maxTagWidth);
                }
                gui.drawString(font, tagText, textX, textY, OmniTheme.TEXT_GRAY, false);
                textX += font.width(tagText) + 3;
            }

            // Name (after tag, white) - takes all remaining space before source mod
            String name = hit.name();
            int nameAreaEnd = rightEdge - (sourceWidth > 0 ? sourceWidth + 6 : 0);
            int nameMaxWidth = nameAreaEnd - textX;
            boolean nameTruncated = false;
            int nameDrawX = textX;
            if (nameMaxWidth > 0 && font.width(name) > nameMaxWidth) {
                name = TextUtils.truncateWithEllipsis(font, name, nameMaxWidth);
                nameTruncated = true;
            }
            if (nameMaxWidth > 0) {
                gui.drawString(font, name, nameDrawX, textY, OmniTheme.TEXT_WHITE, false);
            }

            // Track hovered truncated name for tooltip
            if (nameTruncated && rowHovered
                    && mouseX >= nameDrawX && mouseX <= nameDrawX + font.width(name)) {
                hoveredTooltipText = hit.name();
                hoveredTooltipX = mouseX;
                hoveredTooltipY = mouseY;
            }

            // Source mod (right-aligned, gray) - underline when hovered to indicate clickable
            if (sourceWidth > 0) {
                int sourceX = rightEdge - sourceWidth;
                int sourceColor = OmniTheme.TEXT_GRAY;
                if (rowHovered && mouseX >= sourceX && mouseX <= sourceX + sourceWidth) {
                    // Hovered mod name: brighter color + underline to indicate clickable
                    sourceColor = OmniTheme.TEXT_WHITE;
                    gui.hLine(sourceX, sourceX + sourceWidth - 1, textY + font.lineHeight, OmniTheme.TEXT_WHITE);
                }
                gui.drawString(font, sourceText, sourceX, textY, sourceColor, false);

                // Track hovered truncated source mod for tooltip
                if (sourceTruncated && rowHovered
                        && mouseX >= sourceX && mouseX <= sourceX + sourceWidth) {
                    hoveredTooltipText = hit.sourceMod();
                    hoveredTooltipX = mouseX;
                    hoveredTooltipY = mouseY;
                }
            }
        }

        gui.disableScissor();

        // Render tooltip for truncated text
        if (hoveredTooltipText != null) {
            gui.renderTooltip(font, Component.literal(hoveredTooltipText), hoveredTooltipX, hoveredTooltipY);
        }

        // Scrollbar
        if (results.size() > visibleRows) {
            float ratio = (float) visibleRows / results.size();
            float frac = (float) scrollOffset / Math.max(1, results.size() - visibleRows);
            scrollbar.render(gui, x + width - OmniTheme.SCROLLBAR_WIDTH, y + 1, height - 2, frac, ratio);
        }

        return results.size() * OmniTheme.LIST_ITEM_HEIGHT;
    }

    public int getRowAt(int mouseY, int listY, int scrollOffset) {
        int relativeY = mouseY - listY - 1;
        if (relativeY < 0) return -1;
        return relativeY / OmniTheme.LIST_ITEM_HEIGHT + scrollOffset;
    }

    /**
     * Checks if a click at (mouseX, mouseY) falls within the source mod text area of any row.
     * Returns the sourceMod string if the click is on a mod name, null otherwise.
     */
    public String getModNameAt(int mouseX, int mouseY, int listX, int listY, int listWidth,
                               List<SearchHit> results, int scrollOffset) {
        int contentX = listX + 1;
        int contentWidth = listWidth - 2 - OmniTheme.SCROLLBAR_WIDTH;
        int rightEdge = contentX + contentWidth - OmniTheme.ROW_PADDING_X;
        int maxModWidth = contentWidth / MOD_MAX_RATIO;

        int row = getRowAt(mouseY, listY, scrollOffset);
        if (row < 0 || row >= results.size()) return null;

        SearchHit hit = results.get(row);
        String sourceText = hit.sourceMod();
        if (sourceText == null || sourceText.isEmpty()) return null;

        int sourceWidth = font.width(sourceText);
        if (sourceWidth > maxModWidth) {
            sourceText = TextUtils.truncateWithEllipsis(font, sourceText, maxModWidth);
            sourceWidth = font.width(sourceText);
        }

        int sourceX = rightEdge - sourceWidth;
        if (mouseX >= sourceX && mouseX <= sourceX + sourceWidth) {
            return hit.sourceMod();
        }
        return null;
    }
}
