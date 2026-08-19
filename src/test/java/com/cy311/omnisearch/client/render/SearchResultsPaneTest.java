package com.cy311.omnisearch.client.render;

import com.cy311.omnisearch.client.screen.state.SearchSessionState;
import com.cy311.omnisearch.data.model.SearchHit;
import com.cy311.omnisearch.data.model.SearchQuery;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.cy311.omnisearch.client.render.RenderTestUtil.createMockFont;
import static com.cy311.omnisearch.client.render.RenderTestUtil.createMockGuiGraphics;
import static org.junit.jupiter.api.Assertions.*;

class SearchResultsPaneTest {

    private final Font font = createMockFont();
    private final GuiGraphics gui = createMockGuiGraphics();
    private final SearchResultsPane pane = new SearchResultsPane(new ResultListWidget(font), font);

    private SearchSessionState stateWithResults() {
        return SearchSessionState.initial()
            .withCurrentView(SearchSessionState.BodyView.RESULTS)
            .withQuery(new SearchQuery("娜迦"))
            .withResults(List.of(
                new SearchHit("item/1", "娜迦鳞片", "item", "暮色森林", null),
                new SearchHit("item/2", "月光蠕行者的眼珠", "item", "暮色森林", null),
                new SearchHit("item/3", "烧焦的树皮", "item", "交错维度", null),
                new SearchHit("item/4", "火焰血", "item", "暮色森林", null),
                new SearchHit("item/5", "巫妖法杖", "item", "暮色森林", null),
                new SearchHit("item/6", "幻影头骨", "item", "交错维度", null),
                new SearchHit("item/7", "云杉原木", "item", "Minecraft", null),
                new SearchHit("item/8", "云杉木板", "item", "Minecraft", null),
                new SearchHit("item/9", "去皮云杉原木", "item", "Minecraft", null),
                new SearchHit("item/10", "巫妖刷怪蛋", "item", "暮色森林", null)
            ));
    }

    @Test
    void render_clampsScrollOffset() {
        SearchSessionState state = stateWithResults().withResultsScrollOffset(999);

        SearchSessionState rendered = pane.render(gui, state, 20, 40, 220, 60, 0, 0);

        // 10 results, viewport height 60 → visibleRows = 60/16 = 3, so maxScroll = 10-3 = 7.
        // The offset is clamped down to that max (not to 0).
        assertEquals(7, rendered.resultsScrollOffset());
    }

    @Test
    void handleClick_returnsClickedRow() {
        SearchSessionState state = stateWithResults();

        var click = pane.handleClick(state, 20, 40, 220, 120, 30, 42);

        assertTrue(click.handled());
        assertEquals(0, click.row());
        assertEquals(0, click.state().selectedResultIndex());
    }

    @Test
    void handleScroll_clampsToZero() {
        // viewport can show all rows -> offset clamps back to 0
        SearchSessionState state = stateWithResults().withResultsScrollOffset(6);

        SearchSessionState next = pane.handleScroll(state, 1, 200);

        assertEquals(0, next.resultsScrollOffset());
    }

    @Test
    void handleScroll_usesViewportHeightInsteadOfScreenHeight() {
        SearchSessionState state = stateWithResults();

        SearchSessionState next = pane.handleScroll(state, -1, 48);

        assertTrue(next.resultsScrollOffset() > 0);
    }

    @Test
    void handleClick_onScrollbarStartsDragging() {
        SearchSessionState state = stateWithResults();

        var click = pane.handleClick(state, 20, 40, 220, 48, 238, 52);

        assertTrue(click.handled());
        assertTrue(click.state().draggingScrollbar());
    }

    @Test
    void handleDrag_movesScrollOffsetWhileDragging() {
        SearchSessionState state = stateWithResults().withDraggingScrollbar(true);

        SearchSessionState next = pane.handleDrag(state, 20, 40, 220, 48, 80);

        assertTrue(next.resultsScrollOffset() > 0);
    }
}
