package com.cy311.omnisearch.data.model;

import java.util.List;
import org.jetbrains.annotations.Nullable;

public record SearchPageBatch(
    List<SearchHit> results,
    @Nullable String nextPageUrl
) {}
