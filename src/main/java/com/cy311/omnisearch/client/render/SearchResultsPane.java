package com.cy311.omnisearch.client.render;

import com.cy311.omnisearch.client.screen.state.SearchSessionState;
import com.cy311.omnisearch.gui.theme.OmniTheme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nullable;

public final class SearchResultsPane {
    private static final int CONTENT_BORDER = 2;
    private static final int WHEEL_SCROLL_ROWS = 1;

    private final ResultListWidget listWidget;
    private final ScrollbarWidget scrollbar = new ScrollbarWidget();
    private final Font font;

    public SearchResultsPane(ResultListWidget listWidget, Font font) {
        this.listWidget = listWidget;
        this.font = font;
    }

    public SearchSessionState render(GuiGraphics gui, SearchSessionState state, int x, int y, int width, int height,
                                     int mouseX, int mouseY) {
        listWidget.render(
            gui,
            x,
            y,
            width,
            height,
            state.results(),
            state.selectedResultIndex(),
            state.resultsScrollOffset(),
            mouseX,
            mouseY,
            x + width - OmniTheme.SCROLLBAR_WIDTH
        );

        // Loading indicator at bottom of list when fetching next page
        if (state.loadingMore()) {
            int contentX = x + 1;
            int contentWidth = width - 2 - OmniTheme.SCROLLBAR_WIDTH;
            int contentY = y + 1;
            int contentHeight = height - 2;
            int rowHeight = 16;
            int totalRowsHeight = state.results().size() * rowHeight;
            int indicatorY = contentY + Math.min(totalRowsHeight, contentHeight) - font.lineHeight - 2;
            if (indicatorY > contentY) {
                String text = "加载更多...";
                gui.drawCenteredString(font, text, contentX + contentWidth / 2, indicatorY, OmniTheme.TEXT_GRAY);
            }
        }

        int maxScroll = maxScroll(state.results().size(), height);
        int clampedOffset = Math.max(0, Math.min(state.resultsScrollOffset(), maxScroll));
        return clampedOffset == state.resultsScrollOffset()
            ? state
            : state.withResultsScrollOffset(clampedOffset);
    }

    public SearchSessionState handleScroll(SearchSessionState state, double scrollY, int viewportHeight) {
        int maxScroll = maxScroll(state.results().size(), viewportHeight);
        int step = Math.max(1, (int) Math.round(Math.abs(scrollY))) * WHEEL_SCROLL_ROWS;
        int newOffset = state.resultsScrollOffset() - Integer.signum((int) Math.round(scrollY)) * step;
        int clamped = Math.max(0, Math.min(newOffset, maxScroll));
        return state.withResultsScrollOffset(clamped);
    }

    public ClickResult handleClick(SearchSessionState state, int x, int y, int width, int height, double mx, double my) {
        if (mx < x || mx > x + width || my < y || my > y + height) {
            return ClickResult.notHandled(state);
        }
        int scrollbarX = x + width - OmniTheme.SCROLLBAR_WIDTH;
        if (mx >= scrollbarX) {
            int maxScroll = maxScroll(state.results().size(), height);
            if (maxScroll > 0) {
                float thumbRatio = thumbRatio(state.results().size(), height);
                float frac = scrollbar.clickToFraction((int) my, y + 1, trackHeight(height), thumbRatio);
                int newOffset = Math.round(frac * maxScroll);
                return new ClickResult(true, -1, null, state.withResultsScrollOffset(newOffset).withDraggingScrollbar(true));
            }
            return new ClickResult(true, -1, null, state.withDraggingScrollbar(false));
        }

        // Check if click is on a mod name (source mod area)
        String modName = listWidget.getModNameAt((int) mx, (int) my, x, y, width, state.results(), state.resultsScrollOffset());
        if (modName != null) {
            return new ClickResult(true, -1, modName, state);
        }

        // Normal row click -> select result
        int row = listWidget.getRowAt((int) my, y, state.resultsScrollOffset());
        if (row >= 0 && row < state.results().size()) {
            return new ClickResult(true, row, null, state.withSelectedResultIndex(row));
        }
        return ClickResult.notHandled(state);
    }

    public SearchSessionState handleDrag(SearchSessionState state, int x, int y, int width, int height, double my) {
        if (!state.draggingScrollbar()) {
            return state;
        }
        int maxScroll = maxScroll(state.results().size(), height);
        if (maxScroll <= 0) return state;
        float thumbRatio = thumbRatio(state.results().size(), height);
        float frac = scrollbar.clickToFraction((int) my, y + 1, trackHeight(height), thumbRatio);
        return state.withResultsScrollOffset(Math.round(frac * maxScroll));
    }

    public SearchSessionState stopDragging(SearchSessionState state) {
        return state.withDraggingScrollbar(false);
    }

    public record ClickResult(boolean handled, int row, @Nullable String modFilter, SearchSessionState state) {
        public static ClickResult notHandled(SearchSessionState state) {
            return new ClickResult(false, -1, null, state);
        }
    }

    private static int visibleRows(int viewportHeight) {
        int contentHeight = Math.max(0, viewportHeight - CONTENT_BORDER);
        return Math.max(1, contentHeight / OmniTheme.LIST_ITEM_HEIGHT);
    }

    private static int maxScroll(int resultCount, int viewportHeight) {
        return Math.max(0, resultCount - visibleRows(viewportHeight));
    }

    private static float thumbRatio(int resultCount, int viewportHeight) {
        if (resultCount <= 0) {
            return 1F;
        }
        return (float) visibleRows(viewportHeight) / resultCount;
    }

    private static int trackHeight(int viewportHeight) {
        return Math.max(0, viewportHeight - CONTENT_BORDER);
    }
}
