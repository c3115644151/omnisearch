package com.cy311.omnisearch.data.model;

import org.jetbrains.annotations.Nullable;

public record SearchHit(String id, String name, String type, String sourceMod, String category,
                        @Nullable String modEnName) {

    /**
     * Compatibility constructor for callers that don't carry the English mod name.
     */
    public SearchHit(String id, String name, String type, String sourceMod, String category) {
        this(id, name, type, sourceMod, category, null);
    }
}
