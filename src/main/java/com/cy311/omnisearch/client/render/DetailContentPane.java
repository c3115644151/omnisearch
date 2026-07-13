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

    public DetailContentPane(DetailPanelWidget panelWidget, DocumentRenderer documentRenderer) {
        this.panelWidget = panelWidget;
        this.documentRenderer = documentRenderer;
    }

    public DetailViewState render(GuiGraphics gui, DetailViewState state, int x, int y, int width, int height) {
        if (state.page() == null) {
            gui.fill(x, y, x + width, y + height, OmniTheme.BG_CONTENT);
            return state.withDraggingScrollbar(false);
        }

        panelWidget.render(gui, x, y, width, height, state.page());
        int[] contentArea = panelWidget.getContentAreaBounds(x, y, width, height);
        DetailViewState nextState = ensureLayout(state, contentArea[2]);
        int scrollOffset = Math.max(0, nextState.scrollOffset());
        if (scrollOffset != nextState.scrollOffset()) {
            nextState = nextState.withScrollOffset(scrollOffset);
        }

        gui.enableScissor(contentArea[0], contentArea[1], contentArea[0] + contentArea[2], contentArea[1] + contentArea[3]);
        documentRenderer.paint(gui, nextState.cachedLayout(), contentArea[0], contentArea[1] - nextState.scrollOffset());
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
        int backX = x + 6;
        int backY = y + 4;
        if (mx >= backX && mx <= backX + 18 && my >= backY && my <= backY + 18) {
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
        int scrollbarX = contentArea[0] + contentArea[2] - OmniTheme.SCROLLBAR_WIDTH;
        if (mx >= scrollbarX && mx <= scrollbarX + OmniTheme.SCROLLBAR_WIDTH
                && my >= contentArea[1] && my <= contentArea[1] + contentArea[3]) {
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
        float thumbRatio = Math.min(1f, (float) contentArea[3] / Math.max(1, state.contentHeight()));
        int thumbHeight = Math.max(8, (int) (contentArea[3] * thumbRatio));
        float fraction = (float) (my - contentArea[1]) / Math.max(1, contentArea[3] - thumbHeight);
        fraction = Math.max(0, Math.min(1, fraction));
        return state.withScrollOffset((int) (fraction * maxScroll));
    }

    public DetailViewState handleScroll(DetailViewState state, double scrollY) {
        return state.withScrollOffset(state.scrollOffset() - (int) Math.round(scrollY) * 20);
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
        int sx = contentArea[0] + contentArea[2] - OmniTheme.SCROLLBAR_WIDTH;
        gui.fill(sx, contentArea[1], sx + OmniTheme.SCROLLBAR_WIDTH, contentArea[1] + contentArea[3], OmniTheme.BG_SCROLLBAR_TRACK);
        float thumbRatio = Math.min(1f, (float) contentArea[3] / Math.max(1, state.contentHeight()));
        int thumbH = Math.max(8, (int) (contentArea[3] * thumbRatio));
        float frac = (float) state.scrollOffset() / Math.max(1, maxScroll);
        int thumbY = contentArea[1] + (int) ((contentArea[3] - thumbH) * Math.min(1, Math.max(0, frac)));
        gui.fill(sx + 1, thumbY, sx + OmniTheme.SCROLLBAR_WIDTH - 1, thumbY + thumbH, OmniTheme.BG_SCROLLBAR_THUMB);
    }

    public record ClickResult(boolean handled, boolean goBack, @Nullable String openUrl, DetailViewState state) {
        public static ClickResult notHandled(DetailViewState state) {
            return new ClickResult(false, false, null, state);
        }
    }
}
