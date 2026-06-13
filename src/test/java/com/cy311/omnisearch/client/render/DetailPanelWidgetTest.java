package com.cy311.omnisearch.client.render;

import com.cy311.omnisearch.data.model.ItemPage;
import com.cy311.omnisearch.data.model.document.Document;
import com.cy311.omnisearch.data.model.document.TextNode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.cy311.omnisearch.client.render.RenderTestUtil.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DetailPanelWidgetTest {

    private static final int BG_ALPHA = 0xCC000000;
    private static final int HEADER_BG = 0xAA1A1A1A;
    private static final int TITLE_COLOR = 0xFFFFAA00;
    private static final int TEXT_GRAY = 0xFFAAAAAA;
    private static final int TEXT_DIM = 0xFF666666;
    private static final int HEADER_HEIGHT = 30;
    private static final int METADATA_HEIGHT = 50;
    private static final int PADDING = 6;

    private final Font font = createMockFont();
    private final GuiGraphics gui = createMockGuiGraphics();
    private final DetailPanelWidget widget = new DetailPanelWidget(font);

    private final ItemPage page = new ItemPage(
        "item/123",
        "娜迦鳞片",
        "暮色森林",
        new Document("Title", null, null, List.of(new TextNode("content"))),
        "https://www.mcmod.cn/item/123.html"
    );

    @Test
    void render_paintsSemiTransparentBackground() {
        widget.render(gui, 0, 0, 400, 300, page);

        verify(gui).fill(0, 0, 400, 300, BG_ALPHA);
    }

    @Test
    void render_rendersHeaderBackground() {
        widget.render(gui, 0, 0, 400, 300, page);

        verify(gui).fill(0, 0, 400, HEADER_HEIGHT, HEADER_BG);
        verify(gui).hLine(0, 399, HEADER_HEIGHT, 0xFF555555);
    }

    @Test
    void render_rendersBackButtonArea() {
        widget.render(gui, 0, 0, 400, 300, page);

        // Back button at x=6, y=(30-20)/2=5, size 20x20
        int backX = PADDING;
        int backY = (HEADER_HEIGHT - 20) / 2;
        // Border lines
        verify(gui).hLine(backX, backX + 19, backY, TEXT_GRAY);
        verify(gui).hLine(backX, backX + 19, backY + 19, TEXT_GRAY);
        verify(gui).vLine(backX, backY, backY + 19, TEXT_GRAY);
        verify(gui).vLine(backX + 19, backY, backY + 19, TEXT_GRAY);
        // Arrow text (←)
        verify(gui).drawString(font, "\u2190", backX + 5, backY + (20 - font.lineHeight) / 2, TITLE_COLOR, false);
    }

    @Test
    void render_rendersTitle() {
        widget.render(gui, 0, 0, 400, 300, page);

        int backX = PADDING;
        int titleX = backX + 20 + PADDING;
        int titleY = (HEADER_HEIGHT - font.lineHeight) / 2;
        verify(gui).drawString(font, "娜迦鳞片", titleX, titleY, TITLE_COLOR, false);
    }

    @Test
    void render_rendersSourceMod() {
        widget.render(gui, 0, 0, 400, 300, page);

        int metaY = HEADER_HEIGHT + 1;
        int metaContentX = PADDING + 2;
        int metaLine1Y = metaY + PADDING;
        verify(gui).drawString(font, "来源: 暮色森林", metaContentX, metaLine1Y, TEXT_GRAY, false);
    }

    @Test
    void render_rendersUrl() {
        widget.render(gui, 0, 0, 400, 300, page);

        int metaY = HEADER_HEIGHT + 1;
        int metaContentX = PADDING + 2;
        int metaLine1Y = metaY + PADDING;
        int metaLine2Y = metaLine1Y + font.lineHeight + 2;
        verify(gui).drawString(font, "链接: https://www.mcmod.cn/item/123.html", metaContentX, metaLine2Y, TEXT_DIM, false);
    }

    @Test
    void render_rendersContentAreaBackground() {
        widget.render(gui, 0, 0, 400, 300, page);

        int metaY = HEADER_HEIGHT + 1;
        int contentX = PADDING;
        int contentY = metaY + METADATA_HEIGHT + PADDING;
        int contentWidth = 400 - PADDING * 2;
        int contentHeight = 300 - contentY - PADDING;
        verify(gui).fill(contentX, contentY, contentX + contentWidth, contentY + contentHeight, 0xFF0A0A0A);
    }

    @Test
    void render_paintsMetadataBackground() {
        widget.render(gui, 0, 0, 400, 300, page);

        int metaY = HEADER_HEIGHT + 1;
        verify(gui).fill(0, metaY, 400, metaY + METADATA_HEIGHT, BG_ALPHA);
        verify(gui).hLine(0, 399, metaY + METADATA_HEIGHT, 0xFF555555);
    }

    @Test
    void getContentAreaBounds_returnsCorrectCoordinates() {
        int[] bounds = widget.getContentAreaBounds(0, 0, 400, 300);

        int metaY = HEADER_HEIGHT + 1;
        int contentX = PADDING;
        int contentY = metaY + METADATA_HEIGHT + PADDING;
        int contentWidth = 400 - PADDING * 2;
        int contentHeight = 300 - contentY - PADDING;

        assertArrayEquals(new int[]{contentX, contentY, contentWidth, contentHeight}, bounds);
    }

    @Test
    void getContentAreaBounds_withNonZeroOrigin() {
        int[] bounds = widget.getContentAreaBounds(50, 60, 400, 300);

        int metaY = 60 + HEADER_HEIGHT + 1;
        int contentX = 50 + PADDING;
        int contentY = metaY + METADATA_HEIGHT + PADDING;
        int contentWidth = 400 - PADDING * 2;
        int contentHeight = (60 + 300) - contentY - PADDING;

        assertArrayEquals(new int[]{contentX, contentY, contentWidth, contentHeight}, bounds);
    }

    @Test
    void render_truncatesLongTitle() {
        // Create a font with plainSubstrByWidth stubbed
        Font wrapFont = RenderTestUtil.createMockFont();
        lenient().when(wrapFont.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            int maxWidth = invocation.getArgument(1);
            int maxChars = Math.max(0, maxWidth / RenderTestUtil.PX_PER_CHAR);
            if (maxChars >= text.length()) return text;
            return text.substring(0, maxChars);
        });
        DetailPanelWidget wrapWidget = new DetailPanelWidget(wrapFont);
        GuiGraphics wrapGui = createMockGuiGraphics();

        // Create a title that is wider than available space
        // 60 chars * 6 = 360px, which exceeds maxTitleWidth with a normal font
        String longTitle = "这是一个非常长的标题AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        ItemPage longTitlePage = new ItemPage(
            "id", longTitle, "mod",
            new Document("T", null, null, List.of()), "url"
        );

        wrapWidget.render(wrapGui, 0, 0, 400, 300, longTitlePage);

        int backX = PADDING;
        int titleX = backX + 20 + PADDING;
        int titleY = (HEADER_HEIGHT - wrapFont.lineHeight) / 2;

        // Verify the drawString was called with a truncated title (containing "...")
        // The truncated text will be "null..." if plainSubstrByWidth is not stubbed,
        // or actual truncated text if stubbed.
        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.captor();
        verify(wrapGui).drawString(any(), captor.capture(), eq(titleX), eq(titleY), eq(TITLE_COLOR), eq(false));
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().contains("..."),
            "truncated title should contain '...'");
    }
}
