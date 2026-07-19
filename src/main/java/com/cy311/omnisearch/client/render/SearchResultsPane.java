package com.cy311.omnisearch.client.render;

import com.cy311.omnisearch.client.screen.state.SearchSessionState;
import com.cy311.omnisearch.gui.theme.OmniTheme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nullable;

public final class SearchResultsPane {

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

        int maxScroll = Math.max(0, state.results().size() - Math.max(1, height / 16));
        int clampedOffset = Math.max(0, Math.min(state.resultsScrollOffset(), maxScroll));
        return clampedOffset == state.resultsScrollOffset()
            ? state
            : state.withResultsScrollOffset(clampedOffset);
    }

    public SearchSessionState handleScroll(SearchSessionState state, double scrollY, int height) {
        int maxScroll = Math.max(0, state.results().size() - Math.max(1, height / 16));
        int newOffset = state.resultsScrollOffset() - (int) Math.round(scrollY) * 3;
        int clamped = Math.max(0, Math.min(newOffset, maxScroll));
        return state.withResultsScrollOffset(clamped);
    }

    public ClickResult handleClick(SearchSessionState state, int x, int y, int width, int height, double mx, double my) {
        if (mx < x || mx > x + width || my < y || my > y + height) {
            return ClickResult.notHandled(state);
        }
        int scrollbarX = x + width - OmniTheme.SCROLLBAR_WIDTH;
        if (mx > scrollbarX) {
            int visibleRows = Math.max(1, height / OmniTheme.LIST_ITEM_HEIGHT);
            int maxScroll = Math.max(0, state.results().size() - visibleRows);
            if (maxScroll > 0) {
                float thumbRatio = (float) visibleRows / state.results().size();
                float frac = scrollbar.clickToFraction((int) my, y + 1, height - 2, thumbRatio);
                int newOffset = Math.round(frac * maxScroll);
                return new ClickResult(true, -1, null, state.withResultsScrollOffset(newOffset).withDraggingScrollbar(true));
            }
            return ClickResult.notHandled(state);
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
        int visibleRows = Math.max(1, height / OmniTheme.LIST_ITEM_HEIGHT);
        int maxScroll = Math.max(0, state.results().size() - visibleRows);
        if (maxScroll <= 0) return state;
        float thumbRatio = (float) visibleRows / state.results().size();
        float frac = scrollbar.clickToFraction((int) my, y + 1, height - 2, thumbRatio);
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
}
