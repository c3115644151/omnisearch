package com.cy311.omnisearch.client.render;

import com.cy311.omnisearch.data.model.ItemPage;
import com.cy311.omnisearch.gui.theme.OmniTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Compact detail header for the floating window.
 * Single-line: back button + title + source mod tag.
 */
public class DetailPanelWidget {

    private final Font font;
    private int tagX, tagY, tagW, tagH;
    private String tagUrl;
    private int titleClickX, titleClickY, titleClickW, titleClickH;
    private String titleUrl;
    // Track original (untruncated) text for tooltips
    private String originalTitle;
    private String originalModName;
    private boolean titleTruncated;
    private boolean modTruncated;
    // Track hover state for tooltips (set during render, used after)
    private boolean titleHovered;
    private boolean tagHovered;

    public DetailPanelWidget(Font font) {
        this.font = font;
    }

    @org.jetbrains.annotations.Nullable
    public int[] getTitleClickTarget() {
        if (titleUrl == null) return null;
        return new int[]{titleClickX, titleClickY, titleClickW, titleClickH, 0};
    }

    @org.jetbrains.annotations.Nullable
    public String getTitleUrl() { return titleUrl; }

    @org.jetbrains.annotations.Nullable
    public int[] getTagClickTarget() {
        if (tagUrl == null) return null;
        return new int[]{tagX, tagY, tagW, tagH, 0};
    }

    @org.jetbrains.annotations.Nullable
    public String getTagUrl() { return tagUrl; }

    public void render(GuiGraphics gui, int x, int y, int width, int height, ItemPage page) {
        render(gui, x, y, width, height, page, 0, 0);
    }

    public void render(GuiGraphics gui, int x, int y, int width, int height, ItemPage page, int mouseX, int mouseY) {
        tagX = tagY = tagW = tagH = 0;
        tagUrl = null;
        titleClickX = titleClickY = titleClickW = titleClickH = 0;
        titleUrl = null;
        originalTitle = null;
        originalModName = null;
        titleTruncated = false;
        modTruncated = false;
        titleHovered = false;
        tagHovered = false;

        // Header bar
        gui.fill(x, y, x + width, y + OmniTheme.HEADER_HEIGHT, OmniTheme.BG_PANEL);
        gui.hLine(x, x + width - 1, y + OmniTheme.HEADER_HEIGHT, OmniTheme.BORDER);

        // Back button (centered vertically, +1 for glyph visual centering)
        int backX = x + OmniTheme.PADDING;
        int backY = y + (OmniTheme.HEADER_HEIGHT - font.lineHeight) / 2 + 1;
        boolean backHovered = mouseX >= backX && mouseX <= backX + OmniTheme.BACK_BUTTON_SIZE
                && mouseY >= backY && mouseY <= backY + font.lineHeight;
        gui.drawString(font, "\u2190", backX, backY, backHovered ? OmniTheme.TEXT_WHITE : OmniTheme.TEXT_HEADING_1, false);

        // Title + source mod
        int titleX = backX + OmniTheme.BACK_BUTTON_SIZE + OmniTheme.PADDING;
        int titleY = y + (OmniTheme.HEADER_HEIGHT - font.lineHeight) / 2 + 1;

        String title = page.title();
        String sourceModRaw = page.sourceMod();
        String sourceMod = null;
        String sourceModUrl = null;
        if (sourceModRaw != null && !sourceModRaw.isBlank()) {
            if (sourceModRaw.contains("|")) {
                String[] parts = sourceModRaw.split("\\|", 2);
                sourceMod = parts[0];
                sourceModUrl = parts[1];
            } else {
                sourceMod = sourceModRaw;
            }
        }

        int maxTitleWidth = width - (titleX - x) - OmniTheme.PADDING;

        // Calculate tag dimensions first (needed for title truncation)
        String displayTag = null;
        int tagWidth = 0;
        if (sourceMod != null) {
            displayTag = "[" + sourceMod + "]";
            int maxModWidth = width / 3;
            tagWidth = font.width(displayTag) + 6;
            if (font.width(displayTag) > maxModWidth) {
                String truncatedMod = TextUtils.truncateWithEllipsis(font, sourceMod, maxModWidth - 6 - 2);
                displayTag = "[" + truncatedMod + "]";
                tagWidth = font.width(displayTag) + 6;
                modTruncated = true;
                originalModName = sourceMod;
            }
        }

        // Truncate title
        String displayTitle = title;
        if (sourceMod != null) {
            int availTitleW = maxTitleWidth - tagWidth - OmniTheme.PADDING;
            if (font.width(title) > availTitleW && availTitleW > 20) {
                displayTitle = TextUtils.truncateWithEllipsis(font, title, availTitleW);
                titleTruncated = true;
                originalTitle = title;
            }
        } else {
            if (font.width(title) > maxTitleWidth) {
                displayTitle = TextUtils.truncateWithEllipsis(font, title, maxTitleWidth);
                titleTruncated = true;
                originalTitle = title;
            }
        }

        // Hover detection for title
        titleHovered = mouseX >= titleX && mouseX <= titleX + font.width(displayTitle)
                && mouseY >= titleY && mouseY <= titleY + font.lineHeight;
        boolean titleClickable = page.url() != null && !page.url().isBlank();

        // Draw title: brighter + underline when hovered and clickable
        int titleColor = OmniTheme.TEXT_HEADING_1;
        if (titleHovered && titleClickable) {
            titleColor = OmniTheme.TEXT_WHITE;
            gui.hLine(titleX, titleX + font.width(displayTitle) - 1, titleY + font.lineHeight, OmniTheme.TEXT_WHITE);
        }
        gui.drawString(font, displayTitle, titleX, titleY, titleColor, false);

        this.titleClickX = titleX;
        this.titleClickY = titleY;
        this.titleClickW = font.width(displayTitle);
        this.titleClickH = font.lineHeight;
        this.titleUrl = titleClickable ? page.url() : null;

        // Draw tag
        if (displayTag != null) {
            int tagStartX = x + width - OmniTheme.PADDING - tagWidth;
            int tagBgY = titleY - 1;
            int tagBgH = font.lineHeight + 2;

            // Hover detection for tag
            tagHovered = mouseX >= tagStartX && mouseX <= tagStartX + tagWidth
                    && mouseY >= tagBgY && mouseY <= tagBgY + tagBgH;

            gui.fill(tagStartX, tagBgY, tagStartX + tagWidth, tagBgY + tagBgH, OmniTheme.CHIP_DETAIL_BG);

            int tagTextX = tagStartX + 3;
            int tagColor = OmniTheme.CHIP_DETAIL_TEXT;
            if (tagHovered) {
                tagColor = OmniTheme.TEXT_WHITE;
                gui.hLine(tagTextX, tagTextX + font.width(displayTag) - 1, titleY + font.lineHeight, OmniTheme.TEXT_WHITE);
            }
            gui.drawString(font, displayTag, tagTextX, titleY, tagColor, false);

            this.tagX = tagStartX;
            this.tagY = tagBgY;
            this.tagW = tagWidth;
            this.tagH = tagBgH;
            this.tagUrl = sourceModUrl;
        }

        // Content area background
        int contentY = y + OmniTheme.HEADER_HEIGHT + 1;
        gui.fill(x + 1, contentY, x + width - 1, y + height, OmniTheme.BG_CONTENT);

        // Tooltips for truncated text
        if (titleTruncated && titleHovered && originalTitle != null) {
            gui.renderTooltip(font, Component.literal(originalTitle), mouseX, mouseY);
        } else if (modTruncated && tagHovered && originalModName != null) {
            gui.renderTooltip(font, Component.literal(originalModName), mouseX, mouseY);
        }
    }

    public int[] getContentAreaBounds(int x, int y, int width, int height) {
        int contentX = x + OmniTheme.PADDING;
        int contentY = y + OmniTheme.HEADER_HEIGHT + 1;
        int contentWidth = width - OmniTheme.PADDING - OmniTheme.SCROLLBAR_WIDTH;
        int contentHeight = (y + height) - contentY;
        return new int[]{contentX, contentY, contentWidth, contentHeight};
    }
}
