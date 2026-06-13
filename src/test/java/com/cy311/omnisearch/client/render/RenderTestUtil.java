package com.cy311.omnisearch.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Shared test utilities for rendering tests.
 * Provides factory methods for Mockito mocks of MC rendering classes.
 */
public class RenderTestUtil {

    /** Approximate pixels per character for test calculations. */
    public static final int PX_PER_CHAR = 6;
    public static final int FONT_LINE_HEIGHT = 9;

    /**
     * Creates a mock Font with stubbed width() and lineHeight.
     * width(text) returns text.length() * PX_PER_CHAR.
     * width(string) returns string.length() * PX_PER_CHAR.
     * lineHeight returns FONT_LINE_HEIGHT.
     */
    public static Font createMockFont() {
        Font font = mock(Font.class, Mockito.RETURNS_DEFAULTS);
        // width(String)
        lenient().when(font.width((String) any())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            return text != null ? text.length() * PX_PER_CHAR : 0;
        });
        // width(FormattedText) - needed for some MC internals
        lenient().when(font.width((net.minecraft.network.chat.FormattedText) any()))
            .thenReturn(0);
        // lineHeight is a final field, need deep stubbing
        return font;
    }

    /**
     * Creates a mock GuiGraphics with lenient defaults.
     * Returns the mock which can be verified after render calls.
     */
    public static GuiGraphics createMockGuiGraphics() {
        GuiGraphics gui = mock(GuiGraphics.class, Mockito.RETURNS_SMART_NULLS);
        // Stub pose() to prevent NullPointerException when real render calls (e.g., EditBox) access the pose stack
        lenient().when(gui.pose()).thenReturn(mock(com.mojang.blaze3d.vertex.PoseStack.class));
        return gui;
    }

    /**
     * Captures the text argument from a drawString call.
     */
    public static ArgumentCaptor<String> captureText() {
        return ArgumentCaptor.captor();
    }

    /**
     * Captures the color argument from a drawString call.
     */
    public static ArgumentCaptor<Integer> captureColor() {
        return ArgumentCaptor.captor();
    }

    /**
     * Records all drawString invocations and returns the (text, x, y, color) tuples.
     */
    public record DrawCall(String text, int x, int y, int color) {}

    public static List<DrawCall> getDrawCalls(GuiGraphics gui) {
        var invocations = mockingDetails(gui).getInvocations();
        return invocations.stream()
            .filter(i -> i.getMethod().getName().equals("drawString"))
            .map(i -> {
                Object[] args = i.getArguments();
                String text = args[1] instanceof String s ? s : args[1].toString();
                int x = args[2] instanceof Number n ? n.intValue() : 0;
                int y = args[3] instanceof Number n ? n.intValue() : 0;
                int color = args[4] instanceof Number n ? n.intValue() : 0;
                return new DrawCall(text, x, y, color);
            })
            .toList();
    }
}
