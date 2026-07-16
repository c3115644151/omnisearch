package com.cy311.omnisearch.client.render;

import com.cy311.omnisearch.data.model.ItemPage;
import com.cy311.omnisearch.data.model.document.Document;
import com.cy311.omnisearch.data.model.document.TextNode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.cy311.omnisearch.client.render.RenderTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DetailPanelWidgetTest {

    private static final int BG_ALPHA = 0xCC000000;
    private static final int HEADER_BG = 0xAA1A1A1A;
    private static final int TITLE_COLOR = 0xFFFFAA00;
    private static final int HEADER_HEIGHT = 18;
    private static final int BACK_BUTTON_SIZE = 14;
    private static final int PADDING = 4;

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

        // Full-background fill removed; header uses BG_PANEL and content uses BG_CONTENT
        verify(gui, never()).fill(0, 0, 400, 300, BG_ALPHA);
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

        // Back button is now a simple drawString (←) at x=4, y=(18-9)/2+1=5
        int backX = PADDING;
        int backY = (HEADER_HEIGHT - font.lineHeight) / 2 + 1;
        verify(gui).drawString(font, "\u2190", backX, backY, TITLE_COLOR, false);
    }

    @Test
    void render_rendersTitle() {
        widget.render(gui, 0, 0, 400, 300, page);

        int backX = PADDING;
        int titleX = backX + BACK_BUTTON_SIZE + PADDING;
        int titleY = (HEADER_HEIGHT - font.lineHeight) / 2 + 1;
        verify(gui).drawString(font, "娜迦鳞片", titleX, titleY, TITLE_COLOR, false);
    }

    @Test
    void render_rendersSourceMod() {
        widget.render(gui, 0, 0, 400, 300, page);

        int titleY = (HEADER_HEIGHT - font.lineHeight) / 2 + 1;

        // Source mod renders as a clickable tag in the header (blue text on dark blue background)
        // Tag is right-aligned to scrollbar right edge: tagStartX = x + width - tagWidth
        int tagWidth = font.width("[暮色森林]") + 6;
        int tagStartX = 400 - PADDING - tagWidth;
        // Tag background
        verify(gui).fill(tagStartX, titleY - 1, tagStartX + tagWidth, titleY - 1 + font.lineHeight + 2, 0xFF2A2A4A);
        // Tag text (blue link-style)
        verify(gui).drawString(font, "[暮色森林]", tagStartX + 3, titleY, 0xFF5555FF, false);
    }

    @Test
    void render_rendersUrl() {
        // URL text is no longer drawn; source mod URL is stored for click detection
        widget.render(gui, 0, 0, 400, 300, page);
        assertNull(widget.getTagUrl(), "No tag URL for source mod without URL");

        // When source mod contains a pipe-separated URL, it's stored as tag click target
        ItemPage pageWithUrl = new ItemPage(
            "item/123",
            "娜迦鳞片",
            "暮色森林|https://www.mcmod.cn/class/1.html",
            new Document("Title", null, null, List.of(new TextNode("content"))),
            "https://www.mcmod.cn/item/123.html"
        );
        DetailPanelWidget urlWidget = new DetailPanelWidget(font);
        urlWidget.render(gui, 0, 0, 400, 300, pageWithUrl);
        assertEquals("https://www.mcmod.cn/class/1.html", urlWidget.getTagUrl());
    }

    @Test
    void render_rendersContentAreaBackground() {
        widget.render(gui, 0, 0, 400, 300, page);

        int contentY = HEADER_HEIGHT + 1;
        // Background fills from x+1 to x+width-1, top to bottom of body (no padding gap)
        verify(gui).fill(1, contentY, 399, 300, 0xFF0A0A0A);
    }

    @Test
    void render_paintsMetadataBackground() {
        widget.render(gui, 0, 0, 400, 300, page);

        int contentY = HEADER_HEIGHT + 1;
        verify(gui).fill(1, contentY, 399, 300, 0xFF0A0A0A);
        verify(gui, never()).fill(0, HEADER_HEIGHT + 1, 400, HEADER_HEIGHT + 1 + 40, BG_ALPHA);
        verify(gui, never()).hLine(0, 399, HEADER_HEIGHT + 1 + 40, 0xFF555555);
    }

    @Test
    void getContentAreaBounds_returnsCorrectCoordinates() {
        int[] bounds = widget.getContentAreaBounds(0, 0, 400, 300);

        int contentX = PADDING;
        int contentY = HEADER_HEIGHT + 1;
        int contentWidth = 400 - PADDING - 6;
        int contentHeight = 300 - contentY;

        assertArrayEquals(new int[]{contentX, contentY, contentWidth, contentHeight}, bounds);
    }

    @Test
    void getContentAreaBounds_withNonZeroOrigin() {
        int[] bounds = widget.getContentAreaBounds(50, 60, 400, 300);

        int contentX = 50 + PADDING;
        int contentY = 60 + HEADER_HEIGHT + 1;
        int contentWidth = 400 - PADDING - 6;
        int contentHeight = (60 + 300) - contentY;

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
        int titleX = backX + BACK_BUTTON_SIZE + PADDING;
        int titleY = (HEADER_HEIGHT - wrapFont.lineHeight) / 2 + 1;

        // Verify the drawString was called with a truncated title (containing "...")
        // The truncated text will be "null..." if plainSubstrByWidth is not stubbed,
        // or actual truncated text if stubbed.
        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.captor();
        verify(wrapGui).drawString(any(), captor.capture(), eq(titleX), eq(titleY), eq(TITLE_COLOR), eq(false));
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().contains("..."),
            "truncated title should contain '...'");
    }
}
