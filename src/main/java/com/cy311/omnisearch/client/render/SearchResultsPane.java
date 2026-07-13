package com.cy311.omnisearch.client.render;

import com.cy311.omnisearch.client.screen.state.SearchSessionState;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphics;

public final class SearchResultsPane {

    private final ResultListWidget listWidget;

    public SearchResultsPane(ResultListWidget listWidget) {
        this.listWidget = listWidget;
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
            mouseY
        );
        int maxScroll = Math.max(0, state.results().size() - Math.max(1, height / 20));
        int clampedOffset = Math.max(0, Math.min(state.resultsScrollOffset(), maxScroll));
        return clampedOffset == state.resultsScrollOffset()
            ? state
            : state.withResultsScrollOffset(clampedOffset);
    }

    public SearchSessionState handleScroll(SearchSessionState state, double scrollY) {
        return state.withResultsScrollOffset(state.resultsScrollOffset() - (int) Math.round(scrollY) * 3);
    }

    public ClickResult handleClick(SearchSessionState state, int x, int y, int width, int height, double mx, double my) {
        if (mx < x || mx > x + width || my < y || my > y + height) {
            return ClickResult.notHandled(state);
        }
        int row = listWidget.getRowAt((int) my, y, state.resultsScrollOffset());
        if (row >= 0 && row < state.results().size()) {
            return new ClickResult(true, row, state.withSelectedResultIndex(row));
        }
        return ClickResult.notHandled(state);
    }

    public record ClickResult(boolean handled, int row, SearchSessionState state) {
        public static ClickResult notHandled(SearchSessionState state) {
            return new ClickResult(false, -1, state);
        }
    }
}
