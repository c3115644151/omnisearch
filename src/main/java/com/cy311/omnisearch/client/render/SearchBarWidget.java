package com.cy311.omnisearch.client.render;

import com.cy311.omnisearch.gui.theme.OmniTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla-style search bar using EditBox's native bordered rendering.
 * Supports a mod filter tag chip (e.g. "[暮色森林] ×") shown before the query text.
 * The tag has an X button to clear the filter.
 */
public class SearchBarWidget {

    private static final int TAG_PADDING = 3;
    private static final int X_SIZE = 10; // X button click area

    private final EditBox editBox;
    private final Font font;

    // Cached tag layout for click detection
    private int cachedTagWidth = 0;
    private int cachedXButtonX = 0;

    public SearchBarWidget(Font font, int x, int y, int width) {
        this.font = font;
        int height = font.lineHeight + 6;
        this.editBox = new EditBox(font, x, y, width, height, Component.empty());
        this.editBox.setTextColor(OmniTheme.TEXT_WHITE);
        this.editBox.setBordered(true);
        this.editBox.setHint(Component.literal("搜索MC百科...").withColor(OmniTheme.TEXT_PLACEHOLDER));
        this.editBox.setMaxLength(256);
    }

    public void render(GuiGraphics gui, int x, int y, int width, @Nullable String query, @Nullable String modFilter, int mouseX, int mouseY) {
        int h = editBox.getHeight();

        // Calculate tag layout
        int tagTotalWidth = 0;
        String tagText = null;
        int tagTextWidth = 0;
        if (modFilter != null && !modFilter.isEmpty()) {
            tagText = modFilter;
            tagTextWidth = font.width(tagText);
            tagTotalWidth = TAG_PADDING + tagTextWidth + 3 + X_SIZE + TAG_PADDING;
        }

        // Position the edit box after the tag
        int editBoxX = x;
        int editBoxWidth = width;
        if (tagTotalWidth > 0) {
            editBoxX = x + tagTotalWidth + OmniTheme.PADDING;
            editBoxWidth = width - tagTotalWidth - OmniTheme.PADDING;
        }

        // Draw background for the full bar area
        gui.fill(x, y, x + width, y + h, OmniTheme.BG_CONTENT);

        // Configure and render edit box
        editBox.setX(editBoxX);
        editBox.setY(y);
        editBox.setWidth(editBoxWidth);
        if (query != null && !query.equals(editBox.getValue())) {
            editBox.setValue(query);
        }
        editBox.render(gui, 0, 0, 0);

        // Draw tag chip
        if (tagText != null) {
            int tagX = x;
            int tagY = y;
            int tagH = h;
            int tagW = tagTotalWidth;

            // Background
            gui.fill(tagX, tagY + 1, tagX + tagW, tagY + tagH - 1, OmniTheme.CHIP_MOD_BG);
            // Border
            gui.hLine(tagX, tagX + tagW - 1, tagY + 1, OmniTheme.CHIP_MOD_BORDER);
            gui.hLine(tagX, tagX + tagW - 1, tagY + tagH - 1, OmniTheme.CHIP_MOD_BORDER);
            gui.vLine(tagX, tagY + 1, tagY + tagH - 1, OmniTheme.CHIP_MOD_BORDER);
            gui.vLine(tagX + tagW - 1, tagY + 1, tagY + tagH - 1, OmniTheme.CHIP_MOD_BORDER);

            // Tag text
            int textY = tagY + (tagH - font.lineHeight) / 2;
            gui.drawString(font, tagText, tagX + TAG_PADDING, textY, OmniTheme.CHIP_MOD_TEXT, false);

            // X button
            int xBtnX = tagX + TAG_PADDING + tagTextWidth + 3;
            int xBtnY = tagY + (tagH - X_SIZE) / 2;
            boolean xHovered = mouseX >= xBtnX && mouseX <= xBtnX + X_SIZE
                    && mouseY >= xBtnY && mouseY <= xBtnY + X_SIZE;
            int xColor = xHovered ? OmniTheme.TEXT_WHITE : OmniTheme.TEXT_GRAY;
            // Draw "×" character (offset +1 for visual centering of this glyph)
            int xTextY = tagY + (tagH - font.lineHeight) / 2 + 1;
            gui.drawString(font, "\u00D7", xBtnX + 2, xTextY, xColor, false);

            // Cache for click detection
            cachedTagWidth = tagW;
            cachedXButtonX = xBtnX;
        } else {
            cachedTagWidth = 0;
        }
    }

    /**
     * Legacy render without mod filter.
     */
    public void render(GuiGraphics gui, int x, int y, int width, @Nullable String query) {
        render(gui, x, y, width, query, null, 0, 0);
    }

    /**
     * Checks if a click at (mouseX, mouseY) falls within the X button of the mod filter tag.
     */
    public boolean isXButtonClicked(int mouseX, int mouseY, int barY) {
        if (cachedTagWidth == 0) return false;
        int xBtnY = barY + (editBox.getHeight() - X_SIZE) / 2;
        return mouseX >= cachedXButtonX && mouseX <= cachedXButtonX + X_SIZE
                && mouseY >= xBtnY && mouseY <= xBtnY + X_SIZE;
    }

    public EditBox getEditBox() {
        return editBox;
    }

    public int getTotalHeight() {
        return editBox.getHeight();
    }
}
