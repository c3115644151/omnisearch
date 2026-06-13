package com.cy311.omnisearch.data.model.document;

public class DividerNode extends DocNode {
    public DividerNode() {}

    public String getType() {
        return "divider";
    }

    @Override
    public <T> T accept(DocNodeVisitor<T> visitor) {
        return visitor.visitDivider(this);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DividerNode;
    }

    @Override
    public int hashCode() {
        return DividerNode.class.hashCode();
    }

    @Override
    public String toString() {
        return "DividerNode{}";
    }
}
