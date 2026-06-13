package com.cy311.omnisearch.data.model;

import com.cy311.omnisearch.data.model.document.Document;

public record ItemPage(String id, String title, String sourceMod, Document document, String url) {}
