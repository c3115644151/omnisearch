package com.cy311.omnisearch.client.render;

import com.cy311.omnisearch.data.model.ItemPage;
import com.cy311.omnisearch.gui.theme.OmniTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

// verified: GuiGraphics fill/hLine/vLine/drawString signatures from lexxie.dev NeoForge 1.21.1 javadoc 2026-06-14
// verified: Font.width(String) from lexxie.dev NeoForge 1.21.1 2026-06-14

/**
 * Full-screen detail panel for viewing an {@link ItemPage}.
 * <p>
 * Design: compact header with back button + title + source mod tag inline.
 * Content area starts immediately below header.
 */
public class DetailPanelWidget {

    private static final int HEADER_HEIGHT = 26;
    private static final int BACK_BUTTON_SIZE = 18;
    private static final int PADDING = 6;

    private final Font font;
    // Track source mod tag bounds for click detection
    private int tagX, tagY, tagW, tagH;
    private String tagUrl;
    // Track title bounds for click detection
    private int titleClickX, titleClickY, titleClickW, titleClickH;
    private String titleUrl;

    public DetailPanelWidget(Font font) {
        this.font = font;
    }

    /** Returns the title clickable region [x, y, w, h]. Null if no page URL. */
    @org.jetbrains.annotations.Nullable
    public int[] getTitleClickTarget() {
        if (titleUrl == null) return null;
        return new int[]{titleClickX, titleClickY, titleClickW, titleClickH, 0};
    }

    @org.jetbrains.annotations.Nullable
    public String getTitleUrl() { return titleUrl; }

    /** Returns the source mod tag clickable region [x, y, w, h]. Null if no tag. */
    @org.jetbrains.annotations.Nullable
    public int[] getTagClickTarget() {
        if (tagUrl == null) return null;
        return new int[]{tagX, tagY, tagW, tagH, 0}; // url stored separately
    }

    @org.jetbrains.annotations.Nullable
    public String getTagUrl() { return tagUrl; }

    public void render(GuiGraphics gui, int x, int y, int width, int height, ItemPage page) {
        tagX = tagY = tagW = tagH = 0;
        tagUrl = null;
        titleClickX = titleClickY = titleClickW = titleClickH = 0;
        titleUrl = null;

        // ---- Background ----
        gui.fill(x, y, x + width, y + height, OmniTheme.BG_DARK);

        // ---- Header bar ----
        gui.fill(x, y, x + width, y + HEADER_HEIGHT, OmniTheme.BG_PANEL);
        gui.hLine(x, x + width - 1, y + HEADER_HEIGHT, OmniTheme.BORDER);

        // Back button (arrow)
        int backX = x + PADDING;
        int backY = y + (HEADER_HEIGHT - BACK_BUTTON_SIZE) / 2;
        drawBackButton(gui, backX, backY, BACK_BUTTON_SIZE, BACK_BUTTON_SIZE);

        // Title + source mod in one block, vertically aligned
        int titleX = backX + BACK_BUTTON_SIZE + PADDING;
        // Align title text to vertical center of header
        int titleY = y + (HEADER_HEIGHT - font.lineHeight) / 2;

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

        int maxTitleWidth = width - (titleX - x) - PADDING;

        // Draw title
        String displayTitle = title;
        if (sourceMod != null) {
            String tag = "[" + sourceMod + "]";
            int tagWidth = font.width(tag) + 8;
            // Reserve space for tag
            int availTitleW = maxTitleWidth - tagWidth - PADDING;
            if (font.width(title) > availTitleW && availTitleW > 20) {
                displayTitle = font.plainSubstrByWidth(title, Math.max(10, availTitleW - 3)) + "...";
            }
        } else {
            if (font.width(title) > maxTitleWidth) {
                displayTitle = font.plainSubstrByWidth(title, Math.max(10, maxTitleWidth - 3)) + "...";
            }
        }
        gui.drawString(font, displayTitle, titleX, titleY, OmniTheme.TEXT_HEADING_1, false);

        // Track title click target (clickable → page source URL)
        this.titleClickX = titleX;
        this.titleClickY = titleY;
        this.titleClickW = font.width(displayTitle);
        this.titleClickH = font.lineHeight;
        this.titleUrl = page.url() != null && !page.url().isBlank() ? page.url() : null;

        // Source mod tag (clickable, to the right of title)
        if (sourceMod != null) {
            String tag = "[" + sourceMod + "]";
            int tagStartX = titleX + font.width(displayTitle) + PADDING;
            int tagWidth = font.width(tag) + 8;

            // Tag background (vertically aligned with title text)
            int tagBgY = titleY - 1;
            int tagBgH = font.lineHeight + 2;
            gui.fill(tagStartX, tagBgY, tagStartX + tagWidth, tagBgY + tagBgH, 0xFF2A2A4A);

            // Tag text (blue link-style)
            int tagTextX = tagStartX + 4;
            gui.drawString(font, tag, tagTextX, titleY, OmniTheme.TEXT_LINK, false);

            // Underline (consistent with document links)
            gui.hLine(tagTextX, tagTextX + font.width(tag) - 1, titleY + font.lineHeight - 1, OmniTheme.TEXT_LINK);

            // Store click target
            this.tagX = tagStartX;
            this.tagY = tagBgY;
            this.tagW = tagWidth;
            this.tagH = tagBgH;
            this.tagUrl = sourceModUrl;
        }

        // ---- Content area ----
        int contentX = x + PADDING;
        int contentY = y + HEADER_HEIGHT + 1;
        int contentWidth = width - PADDING * 2;
        int contentHeight = (y + height) - contentY - PADDING;
        gui.fill(contentX, contentY, contentX + contentWidth, contentY + contentHeight, OmniTheme.BG_CONTENT);
    }

    private void drawBackButton(GuiGraphics gui, int x, int y, int width, int height) {
        gui.hLine(x, x + width - 1, y, OmniTheme.TEXT_GRAY);
        gui.hLine(x, x + width - 1, y + height - 1, OmniTheme.TEXT_GRAY);
        gui.vLine(x, y, y + height - 1, OmniTheme.TEXT_GRAY);
        gui.vLine(x + width - 1, y, y + height - 1, OmniTheme.TEXT_GRAY);
        gui.drawString(font, "\u2190", x + 5, y + (height - font.lineHeight) / 2, OmniTheme.TEXT_BACK_BUTTON, false);
    }

    public int[] getContentAreaBounds(int x, int y, int width, int height) {
        int contentX = x + PADDING;
        int contentY = y + HEADER_HEIGHT + 1;
        int contentWidth = width - PADDING * 2;
        int contentHeight = (y + height) - contentY - PADDING;
        return new int[]{contentX, contentY, contentWidth, contentHeight};
    }
}
