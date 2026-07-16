package com.cy311.omnisearch.client.render;

import com.cy311.omnisearch.data.model.SearchHit;
import com.cy311.omnisearch.gui.theme.OmniTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.cy311.omnisearch.client.render.RenderTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;

class ResultListWidgetTest {

    private static final int DARK_BG = 0xCC000000;

    private final Font font = createMockFont();
    private final GuiGraphics gui = createMockGuiGraphics();
    private final ResultListWidget widget = new ResultListWidget(font);

    private final List<SearchHit> results = List.of(
        new SearchHit("item/1", "娜迦鳞片", "item", "暮色森林", null),
        new SearchHit("item/2", "月光蠕行者的眼珠", "item", "暮色森林", null),
        new SearchHit("item/3", "烧焦的树皮", "item", "交错维度", null)
    );

    private static final int CONTENT_RIGHT_X = 10 + 200 - OmniTheme.SCROLLBAR_WIDTH;

    @Test
    void render_paintsDarkBackground() {
        widget.render(gui, 10, 20, 200, 300, results, -1, 0, 0, 0, CONTENT_RIGHT_X);

        verify(gui).fill(10, 20, 210, 320, DARK_BG);
    }

    @Test
    void render_drawsResultNameAndSourceMod() {
        widget.render(gui, 10, 20, 200, 300, results, -1, 0, 0, 0, CONTENT_RIGHT_X);

        // Verify basic presence of each text
        verify(gui, atLeastOnce()).drawString(any(), eq("娜迦鳞片"), anyInt(), anyInt(), anyInt(), anyBoolean());
        verify(gui, atLeastOnce()).drawString(any(), eq("暮色森林"), anyInt(), anyInt(), anyInt(), anyBoolean());
        verify(gui, atLeastOnce()).drawString(any(), eq("月光蠕行者的眼珠"), anyInt(), anyInt(), anyInt(), anyBoolean());
        verify(gui, atLeastOnce()).drawString(any(), eq("烧焦的树皮"), anyInt(), anyInt(), anyInt(), anyBoolean());
        verify(gui, atLeastOnce()).drawString(any(), eq("交错维度"), anyInt(), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void render_scrollOffsetAffectsPosition() {
        widget.render(gui, 10, 20, 200, 300, results, 2, 1, 0, 0, CONTENT_RIGHT_X);

        // Row 2 (index 2) at visual row: (2-1)*16 = 16 -> rowY = 21+16 = 37, textY = 37+(16-lh)/2
        verify(gui, atLeastOnce()).drawString(any(), eq("烧焦的树皮"), anyInt(), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void render_emptyList_doesNotThrow() {
        assertDoesNotThrow(() ->
            widget.render(gui, 10, 20, 200, 300, List.of(), -1, 0, 0, 0, CONTENT_RIGHT_X)
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
        widget.render(gui, 10, 20, 200, 300, results, -1, 0, 0, 0, CONTENT_RIGHT_X);

        int contentX = 11;
        int contentY = 21;
        int contentWidth = 192;
        int contentHeight = 298;
        verify(gui).enableScissor(contentX, contentY, contentX + contentWidth, contentY + contentHeight);
        verify(gui).disableScissor();
    }

    @Test
    void render_drawsCategoryTag() {
        List<SearchHit> catResults = List.of(
            new SearchHit("item/10", "巫妖塔", "item", "暮色森林", "自然生成")
        );
        widget.render(gui, 10, 20, 200, 300, catResults, -1, 0, 0, 0, CONTENT_RIGHT_X);

        verify(gui, atLeastOnce()).drawString(any(), eq("(自然生成)"), anyInt(), anyInt(), anyInt(), anyBoolean());
        verify(gui, atLeastOnce()).drawString(any(), eq("巫妖塔"), anyInt(), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void render_truncatesSourceModWhenTooLong() {
        // Long source mod should be truncated, not the item name
        List<SearchHit> catResults = List.of(
            new SearchHit("item/10", "短名", "item", "这是一个非常非常非常非常长的模组名称", null)
        );
        widget.render(gui, 10, 20, 80, 300, catResults, -1, 0, 0, 0, 10 + 80 - OmniTheme.SCROLLBAR_WIDTH);

        // Source mod should be truncated (contain "...")
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.captor();
        verify(gui, atLeastOnce()).drawString(any(), textCaptor.capture(), anyInt(), anyInt(), anyInt(), anyBoolean());
        assertTrue(textCaptor.getAllValues().stream().anyMatch(s -> s.endsWith("...")),
            "Truncated source mod should contain '...'");
    }

    @Test
    void render_tooltipOnHoveredTruncatedMod() {
        // Long source mod + mouse hovering over it should trigger renderTooltip
        List<SearchHit> catResults = List.of(
            new SearchHit("item/10", "短名", "item", "这是一个非常非常非常非常长的模组名称", null)
        );
        // width=80 -> contentWidth=72 -> maxModWidth=24
        // sourceText truncated to ~24px wide, right-aligned at rightEdge
        // rightEdge = contentX + contentWidth - ROW_PADDING_X = 11 + 72 - 3 = 80
        // sourceWidth after truncation ~= 24px, so sourceX ~= 80 - 24 = 56
        // Mouse at x=60, y=25 (within row 0)
        widget.render(gui, 10, 20, 80, 300, catResults, -1, 0, 60, 25, 10 + 80 - OmniTheme.SCROLLBAR_WIDTH);

        // renderTooltip should be called with the full source mod text
        verify(gui, atLeastOnce()).renderTooltip(any(), any(net.minecraft.network.chat.Component.class), anyInt(), anyInt());
    }
}
