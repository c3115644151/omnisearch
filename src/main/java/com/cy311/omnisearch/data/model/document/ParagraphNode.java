package com.cy311.omnisearch.data.model.document;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ParagraphNode extends DocNode {
    private final List<DocNode> children;

    public ParagraphNode(List<DocNode> children) {
        this.children = List.copyOf(
            Objects.requireNonNull(children, "children must not be null"));
    }

    public List<DocNode> getChildren() {
        return children;
    }

    public String getType() {
        return "paragraph";
    }

    @Override
    public <T> T accept(DocNodeVisitor<T> visitor) {
        return visitor.visitParagraph(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParagraphNode that)) return false;
        return children.equals(that.children);
    }

    @Override
    public int hashCode() {
        return children.hashCode();
    }

    @Override
    public String toString() {
        return "ParagraphNode{children=" + children + "}";
    }
}
