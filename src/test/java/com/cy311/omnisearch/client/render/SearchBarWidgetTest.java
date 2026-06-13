package com.cy311.omnisearch.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.Test;

import static com.cy311.omnisearch.client.render.RenderTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SearchBarWidgetTest {

    private static final int STONE_GRAY = 0xFFC6C6C6;
    private static final int BORDER_WHITE = 0xFFFFFFFF;
    private static final int BORDER_DARK = 0xFF373737;
    private static final int BLACK_BG = 0xFF000000;

    /**
     * editBox.render() may throw NPE in test environment (needs full Minecraft runtime).
     * Wrap render() in try-catch; calls before editBox.render() are still recorded on the mock.
     * With mock font (lineHeight=0), EditBox height = 0 + 4 = 4, outer panel height = 4 + 10 = 14.
     */
    private void safeRender(SearchBarWidget widget, GuiGraphics gui, int x, int y, int width, String query) {
        try {
            widget.render(gui, x, y, width, query);
        } catch (Exception ignored) {
            // editBox.render() internal calls may fail; verify earlier gui calls
        }
    }

    @Test
    void render_paintsStoneGrayBackground() {
        Font font = createMockFont();
        GuiGraphics gui = createMockGuiGraphics();
        SearchBarWidget widget = new SearchBarWidget(font, 10, 20, 200);

        safeRender(widget, gui, 10, 20, 200, "test");

        verify(gui, atLeastOnce()).fill(anyInt(), anyInt(), anyInt(), anyInt(), eq(STONE_GRAY));
    }

    @Test
    void render_paintsWhiteHighlightBorder() {
        Font font = createMockFont();
        GuiGraphics gui = createMockGuiGraphics();
        SearchBarWidget widget = new SearchBarWidget(font, 10, 20, 200);

        safeRender(widget, gui, 10, 20, 200, "test");

        verify(gui, atLeastOnce()).hLine(anyInt(), anyInt(), anyInt(), eq(BORDER_WHITE));
        verify(gui, atLeastOnce()).vLine(anyInt(), anyInt(), anyInt(), eq(BORDER_WHITE));
    }

    @Test
    void render_paintsDarkShadowBorder() {
        Font font = createMockFont();
        GuiGraphics gui = createMockGuiGraphics();
        SearchBarWidget widget = new SearchBarWidget(font, 10, 20, 200);

        safeRender(widget, gui, 10, 20, 200, "test");

        verify(gui, atLeastOnce()).hLine(anyInt(), anyInt(), anyInt(), eq(BORDER_DARK));
        verify(gui, atLeastOnce()).vLine(anyInt(), anyInt(), anyInt(), eq(BORDER_DARK));
    }

    @Test
    void constructor_createsEditBoxAtCorrectPosition() {
        Font font = createMockFont();
        SearchBarWidget widget = new SearchBarWidget(font, 10, 20, 200);

        assertEquals(15, widget.getEditBox().getX());
        assertEquals(25, widget.getEditBox().getY());
        assertEquals(190, widget.getEditBox().getWidth());
    }

    @Test
    void render_syncsEditBoxValue() {
        Font font = createMockFont();
        GuiGraphics gui = createMockGuiGraphics();
        SearchBarWidget widget = new SearchBarWidget(font, 10, 20, 200);

        safeRender(widget, gui, 10, 20, 200, "test query");

        assertEquals("test query", widget.getEditBox().getValue());
    }

    @Test
    void render_syncsEditBoxPosition() {
        Font font = createMockFont();
        GuiGraphics gui = createMockGuiGraphics();
        SearchBarWidget widget = new SearchBarWidget(font, 30, 40, 300);

        safeRender(widget, gui, 30, 40, 300, "test");

        assertEquals(35, widget.getEditBox().getX());
        assertEquals(45, widget.getEditBox().getY());
    }

    @Test
    void render_paintsBlackEditBoxBackground() {
        Font font = createMockFont();
        GuiGraphics gui = createMockGuiGraphics();
        SearchBarWidget widget = new SearchBarWidget(font, 10, 20, 200);

        safeRender(widget, gui, 10, 20, 200, "test");

        verify(gui, atLeastOnce()).fill(anyInt(), anyInt(), anyInt(), anyInt(), eq(BLACK_BG));
    }
}
