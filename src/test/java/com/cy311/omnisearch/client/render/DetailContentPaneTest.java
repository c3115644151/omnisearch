package com.cy311.omnisearch.client.render;

import com.cy311.omnisearch.client.render.document.DocumentRenderer;
import com.cy311.omnisearch.client.screen.state.DetailViewState;
import com.cy311.omnisearch.data.model.ItemPage;
import com.cy311.omnisearch.data.model.document.Document;
import com.cy311.omnisearch.data.model.document.TextNode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.cy311.omnisearch.client.render.RenderTestUtil.createMockFont;
import static com.cy311.omnisearch.client.render.RenderTestUtil.createMockGuiGraphics;
import static org.junit.jupiter.api.Assertions.*;

class DetailContentPaneTest {

    private final Font font = createMockFont();
    private final GuiGraphics gui = createMockGuiGraphics();
    private final DetailContentPane pane = new DetailContentPane(
        new DetailPanelWidget(font),
        new DocumentRenderer(font, null)
    );

    @Test
    void render_populatesLayoutCacheAndContentHeight() {
        ItemPage page = new ItemPage(
            "item/1",
            "娜迦鳞片",
            "暮色森林",
            new Document("Title", null, null, List.of(new TextNode("content line"))),
            "https://www.mcmod.cn/item/1.html"
        );
        DetailViewState state = DetailViewState.initial().withPage(page);

        DetailViewState rendered = pane.render(gui, state, 100, 40, 320, 220, 0, 0);

        assertNotNull(rendered.cachedLayout());
        assertEquals(page.id(), rendered.cachedPageId());
        assertEquals(310, rendered.cachedWidth());
        assertEquals(rendered.cachedLayout().height(), rendered.contentHeight());
        assertNotNull(rendered.cachedLinks());
    }

    @Test
    void handleClick_detectsBackAndTitleTargets() {
        ItemPage page = new ItemPage(
            "item/1",
            "娜迦鳞片",
            "暮色森林|https://www.mcmod.cn/class/1.html",
            new Document("Title", null, null, List.of(new TextNode("content line"))),
            "https://www.mcmod.cn/item/1.html"
        );
        DetailViewState state = pane.render(gui, DetailViewState.initial().withPage(page), 100, 40, 320, 220, 0, 0);

        var back = pane.handleClick(state, 100, 40, 320, 220, 108, 48);
        assertTrue(back.handled());
        assertTrue(back.goBack());
        assertNull(back.openUrl());

        int titleX = 100 + 4 + 14 + 4;
        int titleY = 40 + (18 - font.lineHeight) / 2;
        var title = pane.handleClick(state, 100, 40, 320, 220, titleX + 5, titleY + 1);
        assertTrue(title.handled());
        assertFalse(title.goBack());
        assertEquals("https://www.mcmod.cn/item/1.html", title.openUrl());
    }

    @Test
    void handleScroll_usesViewportHeightForMaxScroll() {
        DetailViewState state = DetailViewState.initial()
            .withPage(new ItemPage("item/1", "测试", "来源", new Document("Title", null, null, List.of(new TextNode("content"))), "https://example.com"))
            .withContentHeight(600)
            .withScrollOffset(0);

        DetailViewState next = pane.handleScroll(state, -1, 120);

        assertTrue(next.scrollOffset() > 0);
    }

    @Test
    void handleClick_onScrollbarWithoutOverflowDoesNotStartDragging() {
        DetailViewState state = DetailViewState.initial()
            .withPage(new ItemPage("item/1", "测试", "来源", new Document("Title", null, null, List.of(new TextNode("content"))), "https://example.com"))
            .withContentHeight(50);

        // Panel (100,40,320,220): contentArea x=104..414, so scrollbar spans x=414..420.
        // Click on the scrollbar (mx=416). Content (50) fits viewport (201) → maxScroll=0,
        // so the click must be handled but NOT start dragging.
        var click = pane.handleClick(state, 100, 40, 320, 220, 416, 120);

        assertTrue(click.handled());
        assertFalse(click.state().draggingScrollbar());
    }
}
