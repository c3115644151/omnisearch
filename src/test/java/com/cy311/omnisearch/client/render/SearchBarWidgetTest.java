package com.cy311.omnisearch.client.render;

import com.cy311.omnisearch.gui.theme.OmniTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.Test;

import static com.cy311.omnisearch.client.render.RenderTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SearchBarWidgetTest {

    private static final int BLACK_BG = OmniTheme.BG_CONTENT;

    /**
     * editBox.render() may throw NPE in test environment (needs full Minecraft runtime).
     * Wrap render() in try-catch; calls before editBox.render() are still recorded on the mock.
     */
    private void safeRender(SearchBarWidget widget, GuiGraphics gui, int x, int y, int width, String query) {
        try {
            widget.render(gui, x, y, width, query);
        } catch (Exception ignored) {
        }
    }

    @Test
    void render_paintsBlackBackground() {
        Font font = createMockFont();
        GuiGraphics gui = createMockGuiGraphics();
        SearchBarWidget widget = new SearchBarWidget(font, 10, 20, 200);

        safeRender(widget, gui, 10, 20, 200, "test");

        verify(gui, atLeastOnce()).fill(anyInt(), anyInt(), anyInt(), anyInt(), eq(BLACK_BG));
    }

    @Test
    void constructor_createsEditBoxAtCorrectPosition() {
        Font font = createMockFont();
        SearchBarWidget widget = new SearchBarWidget(font, 10, 20, 200);

        assertEquals(10, widget.getEditBox().getX());
        assertEquals(20, widget.getEditBox().getY());
        assertEquals(200, widget.getEditBox().getWidth());
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

        assertEquals(30, widget.getEditBox().getX());
        assertEquals(40, widget.getEditBox().getY());
    }

    @Test
    void getTotalHeight_returnsEditBoxHeight() {
        Font font = createMockFont();
        SearchBarWidget widget = new SearchBarWidget(font, 10, 20, 200);

        assertTrue(widget.getTotalHeight() > 0);
    }
}
