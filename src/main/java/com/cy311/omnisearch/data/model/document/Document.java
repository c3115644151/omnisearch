package com.cy311.omnisearch.data.model.document;

import org.jetbrains.annotations.Nullable;
import java.util.List;

public record Document(
    String title,
    @Nullable String sourceMod,
    @Nullable String sourceUrl,
    List<DocNode> content
) {}
