package com.cy311.omnisearch.data.model.document;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ListNode extends DocNode {
    private final boolean ordered;
    private final List<DocNode> items;

    public ListNode(boolean ordered, List<DocNode> items) {
        this.ordered = ordered;
        this.items = List.copyOf(
            Objects.requireNonNull(items, "items must not be null"));
    }

    public boolean isOrdered() {
        return ordered;
    }

    public List<DocNode> getItems() {
        return items;
    }

    public String getType() {
        return "list";
    }

    @Override
    public <T> T accept(DocNodeVisitor<T> visitor) {
        return visitor.visitList(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ListNode listNode)) return false;
        return ordered == listNode.ordered && items.equals(listNode.items);
    }

    @Override
    public int hashCode() {
        return (ordered ? 1 : 0) + 31 * items.hashCode();
    }

    @Override
    public String toString() {
        return "ListNode{ordered=" + ordered + ", items=" + items + "}";
    }
}
