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
    private static final int TEXT_GRAY = 0xFFAAAAAA;
    private static final int HEADER_HEIGHT = 26;
    private static final int BACK_BUTTON_SIZE = 18;
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

        // Back button at x=6, y=(26-18)/2=4, size 18x18
        int backX = PADDING;
        int backY = (HEADER_HEIGHT - BACK_BUTTON_SIZE) / 2;
        // Border lines
        verify(gui).hLine(backX, backX + BACK_BUTTON_SIZE - 1, backY, TEXT_GRAY);
        verify(gui).hLine(backX, backX + BACK_BUTTON_SIZE - 1, backY + BACK_BUTTON_SIZE - 1, TEXT_GRAY);
        verify(gui).vLine(backX, backY, backY + BACK_BUTTON_SIZE - 1, TEXT_GRAY);
        verify(gui).vLine(backX + BACK_BUTTON_SIZE - 1, backY, backY + BACK_BUTTON_SIZE - 1, TEXT_GRAY);
        // Arrow text (←)
        verify(gui).drawString(font, "\u2190", backX + 5, backY + (BACK_BUTTON_SIZE - font.lineHeight) / 2, TITLE_COLOR, false);
    }

    @Test
    void render_rendersTitle() {
        widget.render(gui, 0, 0, 400, 300, page);

        int backX = PADDING;
        int titleX = backX + BACK_BUTTON_SIZE + PADDING;
        int titleY = (HEADER_HEIGHT - font.lineHeight) / 2;
        verify(gui).drawString(font, "娜迦鳞片", titleX, titleY, TITLE_COLOR, false);
    }

    @Test
    void render_rendersSourceMod() {
        widget.render(gui, 0, 0, 400, 300, page);

        int backX = PADDING;
        int titleX = backX + BACK_BUTTON_SIZE + PADDING;
        int titleY = (HEADER_HEIGHT - font.lineHeight) / 2;

        // Source mod renders as a clickable tag in the header (blue text with underline on dark blue background)
        int tagStartX = titleX + font.width("娜迦鳞片") + PADDING;
        int tagWidth = font.width("[暮色森林]") + 8;
        // Tag background
        verify(gui).fill(tagStartX, titleY - 1, tagStartX + tagWidth, titleY - 1 + font.lineHeight + 2, 0xFF2A2A4A);
        // Tag text (blue link-style)
        verify(gui).drawString(font, "[暮色森林]", tagStartX + 4, titleY, 0xFF5555FF, false);
        // Underline (consistent with document links)
        verify(gui).hLine(tagStartX + 4, tagStartX + 4 + font.width("[暮色森林]") - 1, titleY + font.lineHeight - 1, 0xFF5555FF);
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

        int contentX = PADDING;
        int contentY = HEADER_HEIGHT + 1;
        int contentWidth = 400 - PADDING * 2;
        int contentHeight = 300 - contentY - PADDING;
        verify(gui).fill(contentX, contentY, contentX + contentWidth, contentY + contentHeight, 0xFF0A0A0A);
    }

    @Test
    void render_paintsMetadataBackground() {
        // Metadata section removed in new design; content area starts directly below header
        widget.render(gui, 0, 0, 400, 300, page);

        int contentX = PADDING;
        int contentY = HEADER_HEIGHT + 1;
        int contentWidth = 400 - PADDING * 2;
        int contentHeight = 300 - contentY - PADDING;
        verify(gui).fill(contentX, contentY, contentX + contentWidth, contentY + contentHeight, 0xFF0A0A0A);
        // No separate metadata background or separator line
        verify(gui, never()).fill(0, HEADER_HEIGHT + 1, 400, HEADER_HEIGHT + 1 + 50, BG_ALPHA);
        verify(gui, never()).hLine(0, 399, HEADER_HEIGHT + 1 + 50, 0xFF555555);
    }

    @Test
    void getContentAreaBounds_returnsCorrectCoordinates() {
        int[] bounds = widget.getContentAreaBounds(0, 0, 400, 300);

        int contentX = PADDING;
        int contentY = HEADER_HEIGHT + 1;
        int contentWidth = 400 - PADDING * 2;
        int contentHeight = 300 - contentY - PADDING;

        assertArrayEquals(new int[]{contentX, contentY, contentWidth, contentHeight}, bounds);
    }

    @Test
    void getContentAreaBounds_withNonZeroOrigin() {
        int[] bounds = widget.getContentAreaBounds(50, 60, 400, 300);

        int contentX = 50 + PADDING;
        int contentY = 60 + HEADER_HEIGHT + 1;
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
        int titleX = backX + BACK_BUTTON_SIZE + PADDING;
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
