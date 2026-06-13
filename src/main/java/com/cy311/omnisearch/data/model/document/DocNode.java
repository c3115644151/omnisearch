package com.cy311.omnisearch.data.model.document;

public abstract class DocNode {
    public abstract <T> T accept(DocNodeVisitor<T> visitor);
    public abstract String getType();
}
