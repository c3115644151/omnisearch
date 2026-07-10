package com.cy311.omnisearch.gui.component;

import com.cy311.omnisearch.client.render.DetailPanelWidget;
import com.cy311.omnisearch.client.render.document.DocumentRenderer;
import com.cy311.omnisearch.client.render.document.PreparedDocumentLayout;
import com.cy311.omnisearch.client.render.image.ImageManager;
import com.cy311.omnisearch.data.model.ItemPage;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DetailView implements UIComponent {
    private final DetailPanelWidget panel;
    private final DocumentRenderer docRenderer;
    private String cachedPageId;
    private int cachedWidth;
    @Nullable private PreparedDocumentLayout cachedLayout;
    @Nullable private List<DocumentRenderer.LinkHit> cachedLinks;
    private int scrollOffset;
    private int contentHeight;
    private boolean draggingScrollbar;

    public DetailView(Font font, ImageManager imageManager) {
        this.panel = new DetailPanelWidget(font);
        this.docRenderer = new DocumentRenderer(font, imageManager);
    }

    public void setScrollOffset(int offset) { this.scrollOffset = offset; }
    public int getScrollOffset() { return scrollOffset; }
    public int getContentHeight() { return contentHeight; }
    public boolean isDraggingScrollbar() { return draggingScrollbar; }
    public void setDraggingScrollbar(boolean d) { this.draggingScrollbar = d; }
    @Nullable public PreparedDocumentLayout getCachedLayout() { return cachedLayout; }
    @Nullable public List<DocumentRenderer.LinkHit> getCachedLinks() { return cachedLinks; }
    public int[] getContentAreaBounds(int x, int y, int w, int h) { return panel.getContentAreaBounds(x, y, w, h); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        // Panel draws background and returns nothing here
    }

    public void renderPage(GuiGraphics g, int x, int y, int w, int h, ItemPage page) {
        panel.render(g, x, y, w, h, page);
        int[] ca = panel.getContentAreaBounds(x, y, w, h);
        scrollOffset = Math.max(0, scrollOffset);

        // Refresh cached layout if page or width changed
        ensureLayout(page, ca[2]);

        g.enableScissor(ca[0], ca[1], ca[0] + ca[2], ca[1] + ca[3]);
        if (cachedLayout != null) {
            docRenderer.paint(g, cachedLayout, ca[0], ca[1] - scrollOffset);
        }
        g.disableScissor();

        if (cachedLayout != null) {
            contentHeight = cachedLayout.height();
        }
        int maxScroll = Math.max(0, contentHeight - ca[3]);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        // Draw scrollbar
        if (maxScroll > 0) {
            drawScrollbar(g, ca, maxScroll);
        }
    }

    private void ensureLayout(ItemPage page, int width) {
        if (cachedPageId != null && cachedPageId.equals(page.id()) && cachedWidth == width) {
            return;
        }
        cachedLayout = docRenderer.prepare(page.document(), width);
        cachedLinks = cachedLayout != null ? cachedLayout.extractLinks() : null;
        cachedPageId = page.id();
        cachedWidth = width;
    }

    private void drawScrollbar(GuiGraphics g, int[] ca, int maxScroll) {
        int sx = ca[0] + ca[2] - 6;
        g.fill(sx, ca[1], sx + 6, ca[1] + ca[3], 0xFF333333);
        float thumbRatio = Math.min(1f, (float) ca[3] / Math.max(1, contentHeight));
        int thumbH = Math.max(8, (int) (ca[3] * thumbRatio));
        float frac = (float) scrollOffset / maxScroll;
        int thumbY = ca[1] + (int) ((ca[3] - thumbH) * frac);
        g.fill(sx + 1, thumbY, sx + 5, thumbY + thumbH, 0xFF6C6C6C);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        scrollOffset -= (int) Math.round(scrollY) * 20;
        scrollOffset = Math.max(0, scrollOffset);
        return true;
    }
}
