package com.cy311.omnisearch.data.model.document;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SectionNode extends DocNode {
    private final String title;
    private final List<DocNode> children;

    public SectionNode(String title, List<DocNode> children) {
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.children = List.copyOf(
            Objects.requireNonNull(children, "children must not be null"));
    }

    public String getTitle() {
        return title;
    }

    public List<DocNode> getChildren() {
        return children;
    }

    public String getType() {
        return "section";
    }

    @Override
    public <T> T accept(DocNodeVisitor<T> visitor) {
        return visitor.visitSection(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SectionNode that)) return false;
        return title.equals(that.title) && children.equals(that.children);
    }

    @Override
    public int hashCode() {
        return 31 * title.hashCode() + children.hashCode();
    }

    @Override
    public String toString() {
        return "SectionNode{title='" + title + "', children=" + children + "}";
    }
}
