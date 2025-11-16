package com.cy311.omnisearch.client.gui;

import com.cy311.omnisearch.data.ItemData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ModernUI {

    private static final int COLOR_BACKGROUND = 0xCC1E2226;
    private static final int COLOR_BACKGROUND_TOP = 0x33202225;
    private static final int COLOR_BORDER = 0x80454A53;
    private static final int COLOR_BORDER_LIGHT = 0xFF60656F;
    private static final int COLOR_SUBTLE_TEXT = 0xFFA8A8A8;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_ITEM_NAME = 0xFFE0E0E0;
    private static final int COLOR_HOVER = 0x40FFFFFF;
    private static final int COLOR_TITLE = 0xFFFFFFFF;
    private static final int COLOR_MOD_NAME = 0xFFC3A8FF;
    private static final int COLOR_URL = 0xFF5555FF;
    private static final int COLOR_URL_HOVER = 0xFF8888FF;
    private static final int COLOR_GOLD = 0xFFDAA520;

    private static final float GLOBAL_SCALE_FACTOR = 0.9f;
    public static final float BODY_SCALE_FACTOR = 0.8f;

    public static void renderPanel(GuiGraphics pGuiGraphics, int pX, int pY, int pWidth, int pHeight, String pTitle, Font pFont) {
        drawRoundedRect(pGuiGraphics, pX, pY, pX + pWidth, pY + pHeight, 6, COLOR_BACKGROUND);
        drawRoundedRect(pGuiGraphics, pX, pY, pX + pWidth, pY + pHeight, 6, COLOR_BORDER, true);
        RenderCompat.push(pGuiGraphics);
        RenderCompat.translate(pGuiGraphics, pX + pWidth / 2, pY + 10);
        RenderCompat.scale(pGuiGraphics, GLOBAL_SCALE_FACTOR, GLOBAL_SCALE_FACTOR);
        pGuiGraphics.drawCenteredString(pFont, pTitle, 0, 0, COLOR_TITLE);
        RenderCompat.pop(pGuiGraphics);
    }

    public static void renderPanel(UIContext ctx, String title) {
        renderPanel(ctx.g, ctx.panelX, ctx.panelY, ctx.panelWidth, ctx.panelHeight, title, ctx.font);
    }

    public static void renderSearchResults(UIContext ctx, List<ClickableEntry> clickableResults) {
        renderSearchResults(ctx.g, ctx.font, ctx.panelX, ctx.panelY, ctx.panelWidth, ctx.panelHeight, clickableResults, ctx.scrollOffset, ctx.mouseX, ctx.mouseY);
    }

    public static void renderSearchResults(GuiGraphics guiGraphics, Font font, int panelX, int panelY, int panelWidth, int panelHeight, List<ClickableEntry> clickableResults, double scrollOffset, int mouseX, int mouseY) {
        int contentWidth = panelWidth - 20;
        int yOffset = panelY + 20 - (int) scrollOffset;
        Runnable tooltipToRender = null;
        guiGraphics.enableScissor(panelX, panelY + 20, panelX + panelWidth, panelY + panelHeight - 30);
        RenderCompat.push(guiGraphics);
        RenderCompat.translate(guiGraphics, panelX + 10, yOffset);
        guiGraphics.drawString(font, "找到多个结果:", 0, 0, COLOR_SUBTLE_TEXT);
        RenderCompat.pop(guiGraphics);
        yOffset += 15;
        for (ClickableEntry clickable : clickableResults) {
            String itemName = clickable.getItemName();
            String modName = clickable.getModName();
            int entryHeight = font.lineHeight + 5;
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
                int accentPurple = 0xFF8A65E5;
                guiGraphics.fill(accentX0, cardY0, accentX0 + accentW, cardY1, accentPurple);
            }
            RenderCompat.push(guiGraphics);
            RenderCompat.translate(guiGraphics, clickable.getX(), yOffset + 2);
            int nameColor = hover ? 0xFFE7D9FF : COLOR_ITEM_NAME;
            guiGraphics.drawString(font, itemName, 0, 0, nameColor);
            RenderCompat.pop(guiGraphics);
            if (modName != null && !modName.isEmpty()) {
                float scale = 0.8f;
                int itemNameWidth = font.width(itemName);
                int modNameWidth = (int) (font.width(modName) * scale);
                int availableWidth = contentWidth - itemNameWidth - 5;
                String truncatedModName = modName;
                if (modNameWidth > availableWidth) {
                    truncatedModName = font.plainSubstrByWidth(modName, (int) (availableWidth / scale), true) + "...";
                    modNameWidth = (int) (font.width(truncatedModName) * scale);
                }
                RenderCompat.push(guiGraphics);
                RenderCompat.translate(guiGraphics, clickable.getX() + contentWidth - modNameWidth, yOffset + 2 + (font.lineHeight * (1.0f - scale)));
                RenderCompat.scale(guiGraphics, scale, scale);
                int modColor = hover ? 0xFFD8C8FF : COLOR_MOD_NAME;
                guiGraphics.drawString(font, truncatedModName, 0, 0, modColor);
                RenderCompat.pop(guiGraphics);
            }
            yOffset += entryHeight;
        }
        guiGraphics.disableScissor();
        if (tooltipToRender != null) {
            tooltipToRender.run();
        }
    }

    public static void renderLoading(UIContext ctx) {
        renderLoading(ctx.g, ctx.font, ctx.panelX, ctx.panelWidth, ctx.panelY, ctx.panelHeight);
    }
    public static void renderLoading(GuiGraphics guiGraphics, Font font, int panelX, int panelWidth, int panelY, int panelHeight) {
        RenderCompat.push(guiGraphics);
        RenderCompat.translate(guiGraphics, panelX + panelWidth / 2, panelY + panelHeight / 2);
        RenderCompat.scale(guiGraphics, GLOBAL_SCALE_FACTOR, GLOBAL_SCALE_FACTOR);
        guiGraphics.drawCenteredString(font, "加载中...", 0, 0, COLOR_TITLE);
        RenderCompat.pop(guiGraphics);
    }

    public static void renderNoResults(UIContext ctx) {
        renderNoResults(ctx.g, ctx.font, ctx.panelX, ctx.panelWidth, ctx.panelY, ctx.panelHeight);
    }
    public static void renderNoResults(GuiGraphics guiGraphics, Font font, int panelX, int panelWidth, int panelY, int panelHeight) {
        RenderCompat.push(guiGraphics);
        RenderCompat.translate(guiGraphics, panelX + panelWidth / 2, panelY + panelHeight / 2);
        RenderCompat.scale(guiGraphics, GLOBAL_SCALE_FACTOR, GLOBAL_SCALE_FACTOR);
        guiGraphics.drawCenteredString(font, "无结果", 0, 0, COLOR_TITLE);
        RenderCompat.pop(guiGraphics);
    }

    public static void renderClickable(UIContext ctx, ClickableEntry entry) {
        renderClickable(ctx.g, ctx.font, entry, ctx.mouseX, ctx.mouseY);
    }
    public static void renderClickable(GuiGraphics guiGraphics, Font font, ClickableEntry entry, int mouseX, int mouseY) {
        if (entry.isMouseOver(mouseX, mouseY)) {
            drawRoundedRect(guiGraphics, entry.getX() - 2, entry.getY() - 2, entry.getX() + entry.getWidth() + 2, entry.getY() + entry.getHeight() + 2, 3, COLOR_HOVER);
        }
        RenderCompat.push(guiGraphics);
        RenderCompat.translate(guiGraphics, entry.getX(), entry.getY());
        RenderCompat.scale(guiGraphics, BODY_SCALE_FACTOR, BODY_SCALE_FACTOR);
        guiGraphics.drawString(font, entry.getText(), 0, 0, COLOR_TEXT);
        RenderCompat.pop(guiGraphics);
    }

    private static void drawRoundedRect(GuiGraphics guiGraphics, int x0, int y0, int x1, int y1, int radius, int color) {
        drawRoundedRect(guiGraphics, x0, y0, x1, y1, radius, color, false);
    }
    private static void drawRoundedRect(GuiGraphics guiGraphics, int x0, int y0, int x1, int y1, int radius, int color, boolean border) {
        if (border) {
            guiGraphics.hLine(x0 + radius, x1 - radius - 1, y0, color);
            guiGraphics.hLine(x0 + radius, x1 - radius - 1, y1 - 1, color);
            guiGraphics.vLine(x0, y0 + radius, y1 - radius - 1, color);
            guiGraphics.vLine(x1 - 1, y0 + radius, y1 - radius - 1, color);
        } else {
            guiGraphics.fill(x0 + radius, y0, x1 - radius, y1, color);
            guiGraphics.fill(x0, y0 + radius, x0 + radius, y1 - radius, color);
            guiGraphics.fill(x1 - radius, y0 + radius, x1, y1 - radius, color);
        }
    }

    public static int renderItemDetails(UIContext ctx, ItemData itemData, HtmlRenderer htmlRenderer, ClickableEntry urlEntry) {
        return renderItemDetails(ctx.g, ctx.font, ctx.panelX, ctx.panelY, ctx.panelWidth, ctx.panelHeight, itemData, htmlRenderer, ctx.scrollOffset, ctx.mouseX, ctx.mouseY, urlEntry);
    }
    public static int renderItemDetails(GuiGraphics guiGraphics, Font font, int panelX, int panelY, int panelWidth, int panelHeight, ItemData itemData, HtmlRenderer htmlRenderer, double scrollOffset, int mouseX, int mouseY, ClickableEntry urlEntry) {
        int contentWidth = panelWidth - 30;
        int yOffset = panelY + 30;
        guiGraphics.enableScissor(panelX + 10, panelY + 25, panelX + panelWidth - 10, panelY + panelHeight - 30);
        int metadataHeight = renderMetadataBlock(guiGraphics, font, panelX + 15, yOffset - (int)scrollOffset, contentWidth, itemData, mouseX, mouseY, urlEntry);
        yOffset += metadataHeight;
        yOffset += 5;
        if (htmlRenderer != null) {
            htmlRenderer.render(guiGraphics, panelX + 15, yOffset - (int) scrollOffset, panelY + 25, panelY + panelHeight - 30);
        }
        guiGraphics.disableScissor();
        return metadataHeight;
    }

    private static int renderMetadataBlock(GuiGraphics guiGraphics, Font font, int x, int y, int width, ItemData itemData, int mouseX, int mouseY, ClickableEntry urlEntry) {
        float titleScale = 1.1f * GLOBAL_SCALE_FACTOR;
        int titleHeight = (int)((font.lineHeight + 5) * titleScale);
        int modNameHeight = (int)((font.lineHeight + 5) * BODY_SCALE_FACTOR);
        int urlHeight = (int)((font.lineHeight + 5) * BODY_SCALE_FACTOR);
        int height = titleHeight + modNameHeight + urlHeight;
        guiGraphics.fill(x - 5, y - 5, x + width + 5, y + height, 0x50000000);
        RenderCompat.push(guiGraphics);
        RenderCompat.translate(guiGraphics, x + width / 2, y);
        RenderCompat.scale(guiGraphics, titleScale, titleScale);
        guiGraphics.drawCenteredString(font, Component.literal(itemData.title()), 0, 0, COLOR_GOLD);
        RenderCompat.pop(guiGraphics);
        y += titleHeight;
        RenderCompat.push(guiGraphics);
        RenderCompat.translate(guiGraphics, x + width / 2, y);
        RenderCompat.scale(guiGraphics, BODY_SCALE_FACTOR, BODY_SCALE_FACTOR);
        guiGraphics.drawCenteredString(font, itemData.modName(), 0, 0, COLOR_SUBTLE_TEXT);
        RenderCompat.pop(guiGraphics);
        y += modNameHeight;
        String urlText = itemData.url();
        int urlWidth = (int) (font.width(urlText) * BODY_SCALE_FACTOR);
        urlEntry.setBounds(x + (width - urlWidth) / 2, y, urlWidth, (int) (font.lineHeight * BODY_SCALE_FACTOR));
        int urlColor = 0xFF5555FF;
        RenderCompat.push(guiGraphics);
        RenderCompat.translate(guiGraphics, x + width / 2, y);
        RenderCompat.scale(guiGraphics, BODY_SCALE_FACTOR, BODY_SCALE_FACTOR);
        guiGraphics.drawCenteredString(font, urlText, 0, 0, urlColor);
        RenderCompat.pop(guiGraphics);
        return height + 5;
    }

    public static int getMetadataBlockHeight(Font font, int maxWidth, ItemData itemData) {
        float titleScale = 1.1f * GLOBAL_SCALE_FACTOR;
        int titleHeight = (int)((font.lineHeight + 5) * titleScale);
        int modNameHeight = (int)((font.lineHeight + 5) * BODY_SCALE_FACTOR);
        int urlHeight = (int)((font.lineHeight + 5) * BODY_SCALE_FACTOR);
        return titleHeight + modNameHeight + urlHeight + 5;
    }

    public static void renderSearchBox(UIContext ctx, int x, int y, int width, int height) {
        renderSearchBox(ctx.g, x, y, width, height);
    }
    public static void renderSearchBox(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        drawRoundedRect(guiGraphics, x, y, x + width, y + height, 3, 0x80202225);
        guiGraphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0x33151518);
        drawRoundedRect(guiGraphics, x, y, x + width, y + height, 3, COLOR_BORDER, true);
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
            this.g = g; this.font = font; this.panelX = panelX; this.panelY = panelY; this.panelWidth = panelWidth; this.panelHeight = panelHeight; this.mouseX = mouseX; this.mouseY = mouseY; this.scrollOffset = scrollOffset;
        }
    }
}