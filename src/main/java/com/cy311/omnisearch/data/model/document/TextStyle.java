package com.cy311.omnisearch.data.model.document;

import org.jetbrains.annotations.Nullable;

public record TextStyle(
    boolean bold,
    boolean italic,
    boolean underline,
    boolean strikethrough,
    @Nullable String color
) {
    public static final TextStyle NORMAL = new TextStyle(false, false, false, false, null);
    public static final TextStyle BOLD = new TextStyle(true, false, false, false, null);
}
