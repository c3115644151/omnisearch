package com.cy311.omnisearch.data.model.document;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class HeadingNode extends DocNode {
    private final int level;
    private final List<DocNode> children;

    public HeadingNode(int level, List<DocNode> children) {
        this.level = level;
        this.children = List.copyOf(
            Objects.requireNonNull(children, "children must not be null"));
    }

    public int getLevel() {
        return level;
    }

    public List<DocNode> getChildren() {
        return children;
    }

    public String getType() {
        return "heading";
    }

    @Override
    public <T> T accept(DocNodeVisitor<T> visitor) {
        return visitor.visitHeading(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HeadingNode that)) return false;
        return level == that.level && children.equals(that.children);
    }

    @Override
    public int hashCode() {
        return 31 * level + children.hashCode();
    }

    @Override
    public String toString() {
        return "HeadingNode{level=" + level + ", children=" + children + "}";
    }
}
