package com.cy311.omnisearch.client.gui;

import com.cy311.omnisearch.data.ItemData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.List;

public class ModernUI {

    // Color Palette
    private static final int COLOR_BACKGROUND = 0xCC1E2226;
    private static final int COLOR_BACKGROUND_TOP = 0x33202225;
    private static final int COLOR_BORDER = 0x80454A53;
    private static final int COLOR_BORDER_LIGHT = 0xFF60656F;
    private static final int COLOR_SUBTLE_TEXT = 0xFFA8A8A8;
    // private static final int COLOR_ACCENT = 0xFF8A65E5;     // A nice purple for accents
    private static final int COLOR_TEXT = 0xFFFFFFFF; // White
    private static final int COLOR_ITEM_NAME = 0xFFE0E0E0; // A slightly off-white for item names
    private static final int COLOR_HOVER = 0x40FFFFFF;      // Semi-transparent white for hover
    private static final int COLOR_TITLE = 0xFFFFFFFF; // White for titles
    private static final int COLOR_MOD_NAME = 0xFFC3A8FF; // A deeper purple for mod names
    // private static final int COLOR_SECTION_HEADER = 0xFFFFFFA0; // Light yellow for section headers
    private static final int COLOR_URL = 0xFF5555FF;
    private static final int COLOR_URL_HOVER = 0xFF8888FF;
    private static final int COLOR_GOLD = 0xFFDAA520; // Gold color for tooltip

    // Font scaling factors
    private static final float GLOBAL_SCALE_FACTOR = 0.9f; // Slightly smaller fonts globally
    public static final float BODY_SCALE_FACTOR = 0.8f; // Even smaller for body text

    public static void renderPanel(GuiGraphics pGuiGraphics, int pX, int pY, int pWidth, int pHeight, String pTitle, Font pFont) {
        drawRoundedRect(pGuiGraphics, pX, pY, pX + pWidth, pY + pHeight, 6, COLOR_BACKGROUND);
        
        drawRoundedRect(pGuiGraphics, pX, pY, pX + pWidth, pY + pHeight, 6, COLOR_BORDER, true);

        pGuiGraphics.pose().pushPose();
        pGuiGraphics.pose().translate(pX + pWidth / 2, pY + 10, 0);
        pGuiGraphics.pose().scale(GLOBAL_SCALE_FACTOR, GLOBAL_SCALE_FACTOR, GLOBAL_SCALE_FACTOR);
        pGuiGraphics.drawCenteredString(pFont, pTitle, 0, 0, COLOR_TITLE);
        pGuiGraphics.pose().popPose();
    }

    public static void renderPanel(UIContext ctx, String title) {
        renderPanel(ctx.g, ctx.panelX, ctx.panelY, ctx.panelWidth, ctx.panelHeight, title, ctx.font);
    }

    public static void renderSearchResults(GuiGraphics guiGraphics, Font font, int panelX, int panelY, int panelWidth, int panelHeight, List<ClickableEntry> clickableResults, double scrollOffset, int mouseX, int mouseY) {
        int contentWidth = panelWidth - 20;
        int yOffset = panelY + 20 - (int) scrollOffset;
        Runnable tooltipToRender = null;

        guiGraphics.enableScissor(panelX, panelY + 20, panelX + panelWidth, panelY + panelHeight - 30);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(panelX + 10, yOffset, 0);
        guiGraphics.drawString(font, "找到多个结果:", 0, 0, COLOR_SUBTLE_TEXT);
        guiGraphics.pose().popPose();
        yOffset += 15;

        for (ClickableEntry clickable : clickableResults) {
            String itemName = clickable.getItemName();
            String modName = clickable.getModName();

            int entryHeight = font.lineHeight + 5; // 5 for padding
            clickable.setBounds(panelX + 10, yOffset, contentWidth, entryHeight);
            clickable.setHeight(entryHeight);

            int cardX0 = clickable.getX() - 4;
            int cardY0 = yOffset - 2;
            int cardX1 = clickable.getX() + contentWidth + 4;
            int cardY1 = yOffset + entryHeight + 2;
            boolean hover = clickable.isMouseOver(mouseX, mouseY);
            int cardBg = 0x26202225;
            int cardHover = 0x55303840;
            guiGraphics.fill(cardX0, cardY0, cardX1, cardY1, hover ? cardHover : cardBg);
            if (hover) {
                int accentW = 3;
                int accentX0 = cardX0;
                int accentPurple = 0xFF8A65E5; // 紫色高亮
                guiGraphics.fill(accentX0, cardY0, accentX0 + accentW, cardY1, accentPurple);
            }

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(clickable.getX(), yOffset + 2, 0);
            int nameColor = hover ? 0xFFE7D9FF : COLOR_ITEM_NAME; // 悬停时标题文字改为淡紫
            guiGraphics.drawString(font, itemName, 0, 0, nameColor);
            guiGraphics.pose().popPose();

            if (modName != null && !modName.isEmpty()) {
                float scale = 0.8f;
                int itemNameWidth = font.width(itemName);
                int modNameWidth = (int) (font.width(modName) * scale);
                int availableWidth = contentWidth - itemNameWidth - 5; // 5 for spacing

                String truncatedModName = modName;
                if (modNameWidth > availableWidth) {
                    truncatedModName = font.plainSubstrByWidth(modName, (int) (availableWidth / scale), true) + "...";
                    modNameWidth = (int) (font.width(truncatedModName) * scale);
                }

                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(clickable.getX() + contentWidth - modNameWidth, yOffset + 2 + (font.lineHeight * (1.0f - scale)), 0);
                guiGraphics.pose().scale(scale, scale, scale);
                int modColor = hover ? 0xFFD8C8FF : COLOR_MOD_NAME; // 悬停时模组名改为淡紫
                guiGraphics.drawString(font, truncatedModName, 0, 0, modColor);
                guiGraphics.pose().popPose();

                if (clickable.isMouseOver(mouseX, mouseY) && !truncatedModName.equals(modName)) {
                    tooltipToRender = () -> {
                        float tooltipScale = 0.7f * GLOBAL_SCALE_FACTOR;
                        int tooltipWidth = (int) (font.width(modName) * tooltipScale) + 6;
                        int tooltipHeight = (int) (font.lineHeight * tooltipScale) + 4;
                        int tooltipX = mouseX - tooltipWidth / 2;
                        int tooltipY = mouseY - tooltipHeight - 5;

                        drawRoundedRect(guiGraphics, tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 2, 0xCC303235); // Lighter, semi-transparent background

                        guiGraphics.pose().pushPose();
                        guiGraphics.pose().translate(tooltipX + 3, tooltipY + 2, 0);
                        guiGraphics.pose().scale(tooltipScale, tooltipScale, tooltipScale);
                        guiGraphics.drawString(font, modName, 0, 0, COLOR_GOLD);
                        guiGraphics.pose().popPose();
                    };
                }
            }

            yOffset += entryHeight;
        }

        guiGraphics.disableScissor();

        if (tooltipToRender != null) {
            tooltipToRender.run();
        }
    }

    public static void renderSearchResults(UIContext ctx, List<ClickableEntry> clickableResults) {
        renderSearchResults(ctx.g, ctx.font, ctx.panelX, ctx.panelY, ctx.panelWidth, ctx.panelHeight, clickableResults, ctx.scrollOffset, ctx.mouseX, ctx.mouseY);
    }

    public static void renderLoading(GuiGraphics guiGraphics, Font font, int panelX, int panelWidth, int panelY, int panelHeight) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(panelX + panelWidth / 2, panelY + panelHeight / 2, 0);
        guiGraphics.pose().scale(GLOBAL_SCALE_FACTOR, GLOBAL_SCALE_FACTOR, GLOBAL_SCALE_FACTOR);
        guiGraphics.drawCenteredString(font, "加载中...", 0, 0, COLOR_TITLE);
        guiGraphics.pose().popPose();
    }

    public static void renderLoading(UIContext ctx) {
        renderLoading(ctx.g, ctx.font, ctx.panelX, ctx.panelWidth, ctx.panelY, ctx.panelHeight);
    }

    public static void renderNoResults(GuiGraphics guiGraphics, Font font, int panelX, int panelWidth, int panelY, int panelHeight) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(panelX + panelWidth / 2, panelY + panelHeight / 2, 0);
        guiGraphics.pose().scale(GLOBAL_SCALE_FACTOR, GLOBAL_SCALE_FACTOR, GLOBAL_SCALE_FACTOR);
        guiGraphics.drawCenteredString(font, "无结果", 0, 0, COLOR_TITLE);
        guiGraphics.pose().popPose();
    }

    public static void renderNoResults(UIContext ctx) {
        renderNoResults(ctx.g, ctx.font, ctx.panelX, ctx.panelWidth, ctx.panelY, ctx.panelHeight);
    }

    public static void renderClickable(GuiGraphics guiGraphics, Font font, ClickableEntry entry, int mouseX, int mouseY) {
        if (entry.isMouseOver(mouseX, mouseY)) {
            drawRoundedRect(guiGraphics, entry.getX() - 2, entry.getY() - 2, entry.getX() + entry.getWidth() + 2, entry.getY() + entry.getHeight() + 2, 3, COLOR_HOVER);
        }
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(entry.getX(), entry.getY(), 0);
        guiGraphics.pose().scale(BODY_SCALE_FACTOR, BODY_SCALE_FACTOR, BODY_SCALE_FACTOR);
        guiGraphics.drawString(font, entry.getText(), 0, 0, COLOR_TEXT);
        guiGraphics.pose().popPose();
    }

    public static void renderClickable(UIContext ctx, ClickableEntry entry) {
        renderClickable(ctx.g, ctx.font, entry, ctx.mouseX, ctx.mouseY);
    }

    // Helper method to draw a rounded rectangle
    private static void drawRoundedRect(GuiGraphics guiGraphics, int x0, int y0, int x1, int y1, int radius, int color) {
        drawRoundedRect(guiGraphics, x0, y0, x1, y1, radius, color, false);
    }

    private static void drawRoundedRect(GuiGraphics guiGraphics, int x0, int y0, int x1, int y1, int radius, int color, boolean border) {
        if (border) {
            // Border lines
            guiGraphics.hLine(x0 + radius, x1 - radius - 1, y0, color);
            guiGraphics.hLine(x0 + radius, x1 - radius - 1, y1 - 1, color);
            guiGraphics.vLine(x0, y0 + radius, y1 - radius - 1, color);
            guiGraphics.vLine(x1 - 1, y0 + radius, y1 - radius - 1, color);
            // Rounded border corners
            if (radius > 0) {
                drawCorner(guiGraphics, x0 + radius, y0 + radius, radius, 0, color, true);
                drawCorner(guiGraphics, x1 - radius - 1, y0 + radius, radius, 1, color, true);
                drawCorner(guiGraphics, x1 - radius - 1, y1 - radius - 1, radius, 2, color, true);
                drawCorner(guiGraphics, x0 + radius, y1 - radius - 1, radius, 3, color, true);
                int cornerHighlight = 0x8060656F;
                int cornerShadow = 0x33202225;
                if (radius > 1) {
                    drawCorner(guiGraphics, x0 + radius - 1, y0 + radius - 1, radius - 1, 0, cornerHighlight, true);
                    drawCorner(guiGraphics, x1 - radius - 2, y0 + radius - 1, radius - 1, 1, cornerHighlight, true);
                    drawCorner(guiGraphics, x1 - radius - 2, y1 - radius - 2, radius - 1, 2, cornerShadow, true);
                    drawCorner(guiGraphics, x0 + radius - 1, y1 - radius - 2, radius - 1, 3, cornerShadow, true);
                }
            }
        } else {
            // Filled rounded rectangle
            guiGraphics.fill(x0 + radius, y0, x1 - radius, y1, color);
            guiGraphics.fill(x0, y0 + radius, x0 + radius, y1 - radius, color);
            guiGraphics.fill(x1 - radius, y0 + radius, x1, y1 - radius, color);
            drawCorner(guiGraphics, x0 + radius, y0 + radius, radius, 0, color, false);
            drawCorner(guiGraphics, x1 - radius, y0 + radius, radius, 1, color, false);
            drawCorner(guiGraphics, x1 - radius, y1 - radius, radius, 2, color, false);
            drawCorner(guiGraphics, x0 + radius, y1 - radius, radius, 3, color, false);
        }
    }

    // Helper for drawing corners
    private static void drawCorner(GuiGraphics guiGraphics, int x, int y, int radius, int quadrant, int color, boolean border) {
        int r2 = radius * radius;
        int r1 = (radius - 1);
        int r1sq = r1 * r1;
        for (int i = 0; i <= radius; i++) {
            for (int j = 0; j <= radius; j++) {
                int dx = radius - i;
                int dy = radius - j;
                int distSq = dx * dx + dy * dy;
                boolean draw = border ? (distSq <= r2 && distSq >= r1sq) : (distSq <= r2);
                if (draw) {
                    int px = x + (quadrant == 1 || quadrant == 2 ? i : -i);
                    int py = y + (quadrant == 2 || quadrant == 3 ? j : -j);
                    guiGraphics.fill(px, py, px + 1, py + 1, color);
                }
            }
        }
    }

    public static int renderItemDetails(GuiGraphics guiGraphics, Font font, int panelX, int panelY, int panelWidth, int panelHeight, ItemData itemData, HtmlRenderer htmlRenderer, double scrollOffset, int mouseX, int mouseY, ClickableEntry urlEntry) {
        int contentWidth = panelWidth - 30;
        int yOffset = panelY + 30;

        guiGraphics.enableScissor(panelX + 10, panelY + 25, panelX + panelWidth - 10, panelY + panelHeight - 30);

        // 1. Render Metadata Block
        int metadataHeight = renderMetadataBlock(guiGraphics, font, panelX + 15, yOffset - (int)scrollOffset, contentWidth, itemData, mouseX, mouseY, urlEntry);
        yOffset += metadataHeight;

        // Add a bit of space after the metadata block
        yOffset += 5;

        // 2. Render HTML Content (Description)
        if (htmlRenderer != null) {
            htmlRenderer.render(guiGraphics, panelX + 15, yOffset - (int) scrollOffset, panelY + 25, panelY + panelHeight - 30);
        }

        guiGraphics.disableScissor();
        return metadataHeight;
    }

    public static int renderItemDetails(UIContext ctx, ItemData itemData, HtmlRenderer htmlRenderer, ClickableEntry urlEntry) {
        return renderItemDetails(ctx.g, ctx.font, ctx.panelX, ctx.panelY, ctx.panelWidth, ctx.panelHeight, itemData, htmlRenderer, ctx.scrollOffset, ctx.mouseX, ctx.mouseY, urlEntry);
    }

    private static int renderMetadataBlock(GuiGraphics guiGraphics, Font font, int x, int y, int width, ItemData itemData, int mouseX, int mouseY, ClickableEntry urlEntry) {
        // Calculate height correctly first
        float titleScale = 1.1f * GLOBAL_SCALE_FACTOR;
        int titleHeight = (int)((font.lineHeight + 5) * titleScale);
        int modNameHeight = (int)((font.lineHeight + 5) * BODY_SCALE_FACTOR);
        int urlHeight = (int)((font.lineHeight + 5) * BODY_SCALE_FACTOR);
        int height = titleHeight + modNameHeight + urlHeight;

        // Background for the metadata block - draw first
        guiGraphics.fill(x - 5, y - 5, x + width + 5, y + height, 0x50000000);

        // Now draw the text on top
        // Title
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x + width / 2, y, 0);
        guiGraphics.pose().scale(titleScale, titleScale, titleScale);
        Component titleComponent = Component.literal(itemData.title()).withStyle(Style.EMPTY.withBold(true));
        guiGraphics.drawCenteredString(font, titleComponent, 0, 0, COLOR_GOLD);
        guiGraphics.pose().popPose();
        y += titleHeight;

        // Mod Name
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x + width / 2, y, 0);
        guiGraphics.pose().scale(BODY_SCALE_FACTOR, BODY_SCALE_FACTOR, BODY_SCALE_FACTOR);
        guiGraphics.drawCenteredString(font, itemData.modName(), 0, 0, COLOR_SUBTLE_TEXT);
        guiGraphics.pose().popPose();
        y += modNameHeight;

        // URL
        String urlText = itemData.url();
        int urlWidth = (int) (font.width(urlText) * BODY_SCALE_FACTOR);
        urlEntry.setBounds(x + (width - urlWidth) / 2, y, urlWidth, (int) (font.lineHeight * BODY_SCALE_FACTOR));

        boolean isHovering = urlEntry.isMouseOver(mouseX, mouseY);
        int urlColor = isHovering ? COLOR_URL_HOVER : COLOR_URL;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x + width / 2, y, 0);
        guiGraphics.pose().scale(BODY_SCALE_FACTOR, BODY_SCALE_FACTOR, BODY_SCALE_FACTOR);
        guiGraphics.drawCenteredString(font, urlText, 0, 0, urlColor);
        guiGraphics.pose().popPose();

        if (isHovering) {
            int centeredUrlWidth = (int) (font.width(urlText) * BODY_SCALE_FACTOR);
            int underlineX = x + (width - centeredUrlWidth) / 2;
            guiGraphics.hLine(underlineX, underlineX + centeredUrlWidth - 1, y + (int)(font.lineHeight * BODY_SCALE_FACTOR), urlColor);
        }

        return height + 5; // Return full block height with padding
    }

    public static int getMetadataBlockHeight(Font font, int maxWidth, ItemData itemData) {
        float titleScale = 1.1f * GLOBAL_SCALE_FACTOR;
        int titleHeight = (int)((font.lineHeight + 5) * titleScale);
        int modNameHeight = (int)((font.lineHeight + 5) * BODY_SCALE_FACTOR);
        int urlHeight = (int)((font.lineHeight + 5) * BODY_SCALE_FACTOR);
        int textHeight = titleHeight + modNameHeight + urlHeight;
        // The background is drawn from y-5 to y+height, so total height is textHeight + 5
        return textHeight + 5;
    }

    public static void renderSearchBox(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        drawRoundedRect(guiGraphics, x, y, x + width, y + height, 3, 0x80202225);
        guiGraphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0x33151518);
        // Thinner border
        drawRoundedRect(guiGraphics, x, y, x + width, y + height, 3, COLOR_BORDER, true);
    }
    public static void renderSearchBox(UIContext ctx, int x, int y, int width, int height) {
        renderSearchBox(ctx.g, x, y, width, height);
    }
    static class UIContext {
        public final GuiGraphics g;
        public final Font font;
        public final int panelX;
        public final int panelY;
        public final int panelWidth;
        public final int panelHeight;
        public final int mouseX;
        public final int mouseY;
        public final double scrollOffset;

        public UIContext(GuiGraphics g, Font font, int panelX, int panelY, int panelWidth, int panelHeight, int mouseX, int mouseY, double scrollOffset) {
            this.g = g;
            this.font = font;
            this.panelX = panelX;
            this.panelY = panelY;
            this.panelWidth = panelWidth;
            this.panelHeight = panelHeight;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
            this.scrollOffset = scrollOffset;
        }
    }
}
