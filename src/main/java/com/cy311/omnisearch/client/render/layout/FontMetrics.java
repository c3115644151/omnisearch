package com.cy311.omnisearch.client.render.layout;

import java.util.function.ToIntFunction;

/**
 * Font metrics for layout calculation.
 * Pure Java, no MC dependency.
 */
public record FontMetrics(int lineHeight, ToIntFunction<String> widthMeasurer, ToIntFunction<String> boldWidthMeasurer) {

    /**
     * Creates metrics where bold text measures the same as regular text.
     * Used by pure-Java tests that build metrics from a simple lambda.
     */
    public FontMetrics(int lineHeight, ToIntFunction<String> widthMeasurer) {
        this(lineHeight, widthMeasurer, widthMeasurer);
    }

    /**
     * Measures the width of a text string (regular weight).
     */
    public int textWidth(String text) {
        if (text == null || text.isEmpty()) return 0;
        return Math.max(0, widthMeasurer.applyAsInt(text));
    }

    /**
     * Measures the width of a text string rendered with the bold style.
     * Minecraft's bitmap font widens bold glyphs (each glyph's advance gains the
     * {@code GlyphInfo.getBoldOffset()} of +1px), so bold text must be measured wider
     * than regular text or it overflows its wrap boundary.
     */
    public int boldWidth(String text) {
        if (text == null || text.isEmpty()) return 0;
        return Math.max(0, boldWidthMeasurer.applyAsInt(text));
    }

    /**
     * Wraps text to fit within maxWidth, returning lines.
     */
    public java.util.List<String> wrapText(String text, int maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) {
            lines.add("");
            return lines;
        }
        int start = 0;
        int len = text.length();
        while (start < len) {
            // Find how many chars fit
            int end = start + 1;
            int lineW = 0;
            while (end <= len) {
                lineW += textWidth(text.substring(end - 1, end));
                if (lineW > maxWidth && end - start > 1) {
                    end--;
                    break;
                }
                end++;
            }
            if (end > len) end = len;
            lines.add(text.substring(start, end));
            start = end;
        }
        if (lines.isEmpty()) lines.add("");
        return lines;
    }
}
