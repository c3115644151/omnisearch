package com.cy311.omnisearch.client.render;

import com.cy311.omnisearch.client.render.document.DocumentRenderer;
import com.cy311.omnisearch.client.screen.state.DetailViewState;
import com.cy311.omnisearch.gui.theme.OmniTheme;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

/**
 * Encapsulates detail-panel rendering, layout caching, and pointer interaction.
 */
public final class DetailContentPane {

    private final DetailPanelWidget panelWidget;
    private final DocumentRenderer documentRenderer;
    private final ScrollbarWidget scrollbar = new ScrollbarWidget();

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
        documentRenderer.paint(gui, nextState.cachedLayout(), contentArea[0], contentArea[1] - nextState.scrollOffset(), mouseX, mouseY);
        gui.disableScissor();

        nextState = nextState.withContentHeight(nextState.cachedLayout().height());
        int maxScroll = Math.max(0, nextState.contentHeight() - contentArea[3]);
        int clampedScroll = Math.min(nextState.scrollOffset(), maxScroll);
        nextState = nextState.withScrollOffset(clampedScroll);
        if (maxScroll > 0) {
            drawScrollbar(gui, nextState, contentArea, maxScroll);
        }
        return nextState;
    }

    public ClickResult handleClick(DetailViewState state, int x, int y, int width, int height, double mx, double my) {
        if (state.page() == null) {
            return ClickResult.notHandled(state);
        }
        int[] contentArea = panelWidget.getContentAreaBounds(x, y, width, height);
        int backX = x + OmniTheme.PADDING;
        int backY = y + (OmniTheme.HEADER_HEIGHT - OmniTheme.BACK_BUTTON_SIZE) / 2;
        if (mx >= backX && mx <= backX + OmniTheme.BACK_BUTTON_SIZE && my >= backY && my <= backY + OmniTheme.BACK_BUTTON_SIZE) {
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
            return new ClickResult(true, false, null, state.withDraggingScrollbar(true));
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

    public DetailViewState handleScroll(DetailViewState state, double scrollY, int contentHeight) {
        int maxScroll = Math.max(0, state.contentHeight() - contentHeight);
        int newOffset = state.scrollOffset() - (int) Math.round(scrollY) * OmniTheme.SCROLL_STEP;
        int clamped = Math.max(0, Math.min(newOffset, maxScroll));
        return state.withScrollOffset(clamped);
    }

    public DetailViewState stopDragging(DetailViewState state) {
        return state.withDraggingScrollbar(false);
    }

    private DetailViewState ensureLayout(DetailViewState state, int width) {
        if (state.page() == null) {
            return state;
        }
        if (state.cachedPageId() != null && state.cachedPageId().equals(state.page().id())
                && state.cachedWidth() == width && state.cachedLayout() != null) {
            return state;
        }
        var layout = documentRenderer.prepare(state.page().document(), width);
        return state.withCachedLayout(state.page().id(), width, layout, layout.extractLinks());
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
