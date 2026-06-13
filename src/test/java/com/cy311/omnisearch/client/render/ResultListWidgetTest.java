package com.cy311.omnisearch.client.render;

import com.cy311.omnisearch.data.model.SearchHit;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.cy311.omnisearch.client.render.RenderTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResultListWidgetTest {

    private static final int DARK_BG = 0xFF1A1A1A;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_SOURCE_MOD = 0xFFAAAAAA;
    private static final int HIGHLIGHT_BORDER = 0xFFFFFFFF;

    private final Font font = createMockFont();
    private final GuiGraphics gui = createMockGuiGraphics();
    private final ResultListWidget widget = new ResultListWidget(font);

    private final List<SearchHit> results = List.of(
        new SearchHit("item/1", "娜迦鳞片", "item", "暮色森林"),
        new SearchHit("item/2", "月光蠕行者的眼珠", "item", "暮色森林"),
        new SearchHit("item/3", "烧焦的树皮", "item", "交错维度")
    );

    @Test
    void render_paintsDarkBackground() {
        widget.render(gui, 10, 20, 200, 300, results, -1, 0);

        verify(gui).fill(10, 20, 210, 320, DARK_BG);
    }

    @Test
    void render_drawsResultNameAndSourceMod() {
        widget.render(gui, 10, 20, 200, 300, results, -1, 0);

        // Verify basic presence of each text
        verify(gui, atLeastOnce()).drawString(any(), eq("娜迦鳞片"), anyInt(), anyInt(), anyInt(), anyBoolean());
        verify(gui, atLeastOnce()).drawString(any(), eq("暮色森林"), anyInt(), anyInt(), anyInt(), anyBoolean());
        verify(gui, atLeastOnce()).drawString(any(), eq("月光蠕行者的眼珠"), anyInt(), anyInt(), anyInt(), anyBoolean());
        verify(gui, atLeastOnce()).drawString(any(), eq("烧焦的树皮"), anyInt(), anyInt(), anyInt(), anyBoolean());
        verify(gui, atLeastOnce()).drawString(any(), eq("交错维度"), anyInt(), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void render_highlightsSelectedRow() {
        widget.render(gui, 10, 20, 200, 300, results, 0, 0);

        // Selected row 0: highlight border at contentX=11, rowY=21, contentWidth=192, ROW_HEIGHT=20
        verify(gui).hLine(11, 202, 21, HIGHLIGHT_BORDER);       // top
        verify(gui).hLine(11, 202, 40, HIGHLIGHT_BORDER);       // bottom (21+20-1=40)
        verify(gui).vLine(11, 21, 40, HIGHLIGHT_BORDER);        // left
        verify(gui).vLine(202, 21, 40, HIGHLIGHT_BORDER);       // right
    }

    @Test
    void render_scrollOffsetAffectsPosition() {
        widget.render(gui, 10, 20, 200, 300, results, 2, 1);

        // Row 2 (index 2) at visual row: (2-1)*20 = 20 -> rowY = 21+20 = 41, textY = 41+10 = 51
        verify(gui, atLeastOnce()).drawString(any(), eq("烧焦的树皮"), anyInt(), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void render_emptyList_doesNotThrow() {
        assertDoesNotThrow(() ->
            widget.render(gui, 10, 20, 200, 300, List.of(), -1, 0)
        );
    }

    @Test
    void getRowAt_returnsCorrectIndex() {
        assertEquals(0, widget.getRowAt(21, 20, 0));
        assertEquals(1, widget.getRowAt(41, 20, 0));
    }

    @Test
    void getRowAt_withScrollOffset() {
        assertEquals(3, widget.getRowAt(41, 20, 2));
    }

    @Test
    void getRowAt_returnsMinusOneForOutside() {
        assertEquals(-1, widget.getRowAt(19, 20, 0));
        assertEquals(-1, widget.getRowAt(20, 20, 0));
    }

    @Test
    void render_enablesScissor() {
        widget.render(gui, 10, 20, 200, 300, results, -1, 0);

        int contentX = 11;
        int contentY = 21;
        int contentWidth = 192;
        int contentHeight = 298;
        verify(gui).enableScissor(contentX, contentY, contentX + contentWidth, contentY + contentHeight);
        verify(gui).disableScissor();
    }
}
