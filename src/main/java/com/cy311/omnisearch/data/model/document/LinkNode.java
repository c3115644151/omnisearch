package com.cy311.omnisearch.data.model.document;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class LinkNode extends DocNode {
    private final String url;
    private final List<DocNode> children;

    public LinkNode(String url, List<DocNode> children) {
        this.url = Objects.requireNonNull(url, "url must not be null");
        this.children = List.copyOf(
            Objects.requireNonNull(children, "children must not be null"));
    }

    public String getUrl() {
        return url;
    }

    public List<DocNode> getChildren() {
        return children;
    }

    public String getType() {
        return "link";
    }

    @Override
    public <T> T accept(DocNodeVisitor<T> visitor) {
        return visitor.visitLink(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LinkNode linkNode)) return false;
        return url.equals(linkNode.url) && children.equals(linkNode.children);
    }

    @Override
    public int hashCode() {
        return 31 * url.hashCode() + children.hashCode();
    }

    @Override
    public String toString() {
        return "LinkNode{url='" + url + "', children=" + children + "}";
    }
}
