package com.cy311.omnisearch.client.render;

import com.cy311.omnisearch.client.render.document.DocumentRenderer;
import com.cy311.omnisearch.client.render.document.PreparedDocumentLayout;
import com.cy311.omnisearch.client.render.image.ImageManager;
import com.cy311.omnisearch.client.screen.state.DetailViewState;
import com.cy311.omnisearch.gui.theme.OmniTheme;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

/**
 * Encapsulates detail-panel rendering, layout caching, and pointer interaction.
 */
public final class DetailContentPane {
    private static final int WHEEL_SCROLL_PIXELS = OmniTheme.SCROLL_STEP;
    /** Bump when layout/render code changes so cached layouts are rebuilt (see ensureLayout). */
    static final int LAYOUT_VERSION = 8;

    private final DetailPanelWidget panelWidget;
    private final DocumentRenderer documentRenderer;
    private final ScrollbarWidget scrollbar = new ScrollbarWidget();
    /** Image URLs that the current layout was built with while their size was still
     *  unknown (placeholder box). When one of them finishes loading, the layout must be
     *  rebuilt so the reserved box matches the real decoded size. */
    private java.util.Set<String> pendingImageUrls = java.util.Collections.emptySet();

    public DetailContentPane(DetailPanelWidget panelWidget, DocumentRenderer documentRenderer) {
        this.panelWidget = panelWidget;
        this.documentRenderer = documentRenderer;
    }

    public DetailViewState render(GuiGraphics gui, DetailViewState state, int x, int y, int width, int height, int mouseX, int mouseY) {
        if (state.page() == null) {
            gui.fill(x, y, x + width, y + height, OmniTheme.BG_CONTENT);
            return state.withDraggingScrollbar(false);
        }

        panelWidget.render(gui, x, y, width, height, state.page(), mouseX, mouseY);
        int[] contentArea = panelWidget.getContentAreaBounds(x, y, width, height);
        DetailViewState nextState = ensureLayout(state, contentArea[2]);
        int scrollOffset = Math.max(0, nextState.scrollOffset());
        if (scrollOffset != nextState.scrollOffset()) {
            nextState = nextState.withScrollOffset(scrollOffset);
        }

        gui.enableScissor(contentArea[0], contentArea[1], contentArea[0] + contentArea[2], contentArea[1] + contentArea[3]);
        // Cull nodes outside the visible viewport (scrollOffset shifts content up; the
        // scissor rect is the on-screen window) so long pages only draw what's visible.
        documentRenderer.paint(gui, nextState.cachedLayout(), contentArea[0], contentArea[1] - nextState.scrollOffset(),
            mouseX, mouseY, contentArea[1], contentArea[1] + contentArea[3]);
        gui.disableScissor();

        nextState = nextState.withContentHeight(nextState.cachedLayout().height());
        int maxScroll = Math.max(0, nextState.contentHeight() - contentArea[3]);
        int clampedScroll = Math.min(nextState.scrollOffset(), maxScroll);
        nextState = nextState.withScrollOffset(clampedScroll);
        if (maxScroll > 0) {
            drawScrollbar(gui, nextState, contentArea, maxScroll);
        }

        // Re-layout when any image this layout was built with (while its size was unknown)
        // has since finished loading, so the reserved box matches the real decoded size.
        // This fixes image/text overlap AND the "content cut off at scroll bottom" defect:
        // without it the layout height can stay at the placeholder value even though the
        // drawn images are taller, making the true end unreachable by scrolling.
        if (relayoutPending()) {
            nextState = nextState.clearLayoutCache();
        }
        return nextState;
    }

    /**
     * True if one of the images the current layout was built with (while its size was
     * unknown) has finished loading, meaning the layout must be rebuilt with the real size.
     * <p>
     * Bounded: {@code pendingImageUrls} only shrinks (a pending URL is removed once the
     * layout has been rebuilt with it loaded, or once it permanently failed), so this can
     * never spin. An image that keeps failing stays pending but never returns true.
     */
    private boolean relayoutPending() {
        if (pendingImageUrls.isEmpty()) {
            return false;
        }
        ImageManager imageManager = documentRenderer.imageManager();
        if (imageManager == null) {
            return false;
        }
        for (String url : pendingImageUrls) {
            if (imageManager.isLoaded(url)) {
                return true;
            }
        }
        return false;
    }

    public ClickResult handleClick(DetailViewState state, int x, int y, int width, int height, double mx, double my) {
        if (state.page() == null) {
            return ClickResult.notHandled(state);
        }
        int[] contentArea = panelWidget.getContentAreaBounds(x, y, width, height);
        int backX = x + OmniTheme.PADDING;
        int backY = y + (OmniTheme.HEADER_HEIGHT - OmniTheme.BACK_BUTTON_SIZE) / 2;
        // Back arrow click box spans just the arrow glyph (font width) so it never overlaps
        // the title that now starts immediately after the glyph.
        int backHitW = Math.max(1, panelWidget.getBackGlyphWidth());
        if (mx >= backX && mx <= backX + backHitW && my >= backY && my <= backY + OmniTheme.BACK_BUTTON_SIZE) {
            return new ClickResult(true, true, null, state.withDraggingScrollbar(false));
        }
        if (panelWidget.getTitleUrl() != null) {
            int[] title = panelWidget.getTitleClickTarget();
            if (title != null && mx >= title[0] && mx <= title[0] + title[2]
                    && my >= title[1] && my <= title[1] + title[3]) {
                return new ClickResult(true, false, panelWidget.getTitleUrl(), state.withDraggingScrollbar(false));
            }
        }
        if (panelWidget.getTagUrl() != null) {
            int[] tag = panelWidget.getTagClickTarget();
            if (tag != null && mx >= tag[0] && mx <= tag[0] + tag[2]
                    && my >= tag[1] && my <= tag[1] + tag[3]) {
                return new ClickResult(true, false, panelWidget.getTagUrl(), state.withDraggingScrollbar(false));
            }
        }
        for (var link : state.cachedLinks()) {
            if (mx >= contentArea[0] + link.x() && mx <= contentArea[0] + link.x() + link.w()
                    && my >= contentArea[1] + link.y() - state.scrollOffset()
                    && my <= contentArea[1] + link.y() + link.h() - state.scrollOffset()) {
                return new ClickResult(true, false, link.url(), state.withDraggingScrollbar(false));
            }
        }
        int scrollbarX = contentArea[0] + contentArea[2];
        if (mx >= scrollbarX && mx <= scrollbarX + OmniTheme.SCROLLBAR_WIDTH
                && my >= contentArea[1] && my <= contentArea[1] + contentArea[3]) {
            int maxScroll = Math.max(0, state.contentHeight() - contentArea[3]);
            if (maxScroll > 0) {
                float thumbRatio = (float) contentArea[3] / Math.max(1, state.contentHeight());
                float frac = scrollbar.clickToFraction((int) my, contentArea[1], contentArea[3], thumbRatio);
                int newOffset = Math.round(frac * maxScroll);
                return new ClickResult(true, false, null, state.withScrollOffset(newOffset).withDraggingScrollbar(true));
            }
            return new ClickResult(true, false, null, state.withDraggingScrollbar(false));
        }
        return ClickResult.notHandled(state.withDraggingScrollbar(false));
    }

    public DetailViewState handleDrag(DetailViewState state, int x, int y, int width, int height, double my) {
        if (!state.draggingScrollbar() || state.page() == null) {
            return state;
        }
        int[] contentArea = panelWidget.getContentAreaBounds(x, y, width, height);
        int maxScroll = Math.max(1, state.contentHeight() - contentArea[3]);
        float thumbRatio = (float) contentArea[3] / Math.max(1, state.contentHeight());
        float frac = scrollbar.clickToFraction((int) my, contentArea[1], contentArea[3], thumbRatio);
        return state.withScrollOffset(Math.round(frac * maxScroll));
    }

    public DetailViewState handleScroll(DetailViewState state, double scrollY, int viewportHeight) {
        // maxScroll is computed from the SAME viewport used by render()'s clamp and the
        // scrollbar, so wheel scrolling reaches exactly the same bottom as dragging.
        // (Previously mouseScrolled fed a different (smaller) viewport height, which made
        //  the wheel stop short of the true bottom — visible as "can't scroll the last bit,
        //  but the scrollbar goes all the way".)
        int maxScroll = Math.max(0, state.contentHeight() - viewportHeight);
        int step = Math.max(1, (int) Math.round(Math.abs(scrollY))) * WHEEL_SCROLL_PIXELS;
        int newOffset = state.scrollOffset() - Integer.signum((int) Math.round(scrollY)) * step;
        int clamped = Math.max(0, Math.min(newOffset, maxScroll));
        return state.withScrollOffset(clamped);
    }

    /**
     * Scrolls within the panel using the same content-area viewport as {@link #render}
     * (via {@code getContentAreaBounds}), guaranteeing wheel scrolling reaches exactly the
     * same bottom as the scrollbar/dragging.
     *
     * @param x,y,width,height the panel bounds (as passed to render)
     */
    public DetailViewState scrollInPanel(DetailViewState state, double scrollY, int x, int y, int width, int height) {
        if (state.page() == null) {
            return state;
        }
        int[] contentArea = panelWidget.getContentAreaBounds(x, y, width, height);
        return handleScroll(state, scrollY, contentArea[3]);
    }

    public DetailViewState stopDragging(DetailViewState state) {
        return state.withDraggingScrollbar(false);
    }

    private DetailViewState ensureLayout(DetailViewState state, int width) {
        if (state.page() == null) {
            return state;
        }
        // The layout cache must invalidate when the layout/render code changes, not just
        // when the page or width changes. Otherwise a long-lived session keeps showing the
        // old layout after a mod update (this bit us repeatedly during table rework).
        if (state.cachedPageId() != null && state.cachedPageId().equals(state.page().id())
                && state.cachedWidth() == width && state.cachedLayout() != null
                && state.cachedLayoutVersion() == LAYOUT_VERSION) {
            return state;
        }
        var layout = documentRenderer.prepare(state.page().document(), width);
        // Record which images this layout was built with while their size was still unknown
        // (placeholder). When one of them loads, we re-layout to use the real size.
        ImageManager imageManager = documentRenderer.imageManager();
        if (imageManager == null) {
            pendingImageUrls = java.util.Collections.emptySet();
        } else {
            java.util.Set<String> pending = new java.util.LinkedHashSet<>();
            for (String url : layout.imageUrls()) {
                if (!imageManager.isLoaded(url)) {
                    pending.add(url);
                }
            }
            pendingImageUrls = pending;
        }
        return state.withCachedLayout(state.page().id(), width, LAYOUT_VERSION, layout, layout.extractLinks());
    }

    private void drawScrollbar(GuiGraphics gui, DetailViewState state, int[] contentArea, int maxScroll) {
        // Align scrollbar to the right edge of the panel (same as ResultListWidget)
        int sx = contentArea[0] + contentArea[2];
        float ratio = (float) contentArea[3] / Math.max(1, state.contentHeight());
        float frac = (float) state.scrollOffset() / Math.max(1, maxScroll);
        scrollbar.render(gui, sx, contentArea[1], contentArea[3], frac, ratio);
    }

    public record ClickResult(boolean handled, boolean goBack, @Nullable String openUrl, DetailViewState state) {
        public static ClickResult notHandled(DetailViewState state) {
            return new ClickResult(false, false, null, state);
        }
    }
}
