package com.cy311.omnisearch.client.render.layout;

import java.util.function.ToIntFunction;

/**
 * Font metrics for layout calculation.
 * Pure Java, no MC dependency.
 */
public record FontMetrics(int lineHeight, ToIntFunction<String> widthMeasurer) {

    /**
     * Measures the width of a text string.
     */
    public int textWidth(String text) {
        if (text == null || text.isEmpty()) return 0;
        return Math.max(0, widthMeasurer.applyAsInt(text));
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
