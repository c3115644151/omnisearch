package com.cy311.omnisearch.data.model.document;

import java.util.List;
import java.util.Objects;

public class ParagraphNode extends DocNode {
    private final List<DocNode> children;
    /** True when the source paragraph carries CSS text-indent (mcmod.cn uses 2em on body
     *  paragraphs). Rendered as a first-line indent. Defaults to false. */
    private final boolean firstLineIndent;

    public ParagraphNode(List<DocNode> children) {
        this(children, false);
    }

    public ParagraphNode(List<DocNode> children, boolean firstLineIndent) {
        this.children = List.copyOf(
            Objects.requireNonNull(children, "children must not be null"));
        this.firstLineIndent = firstLineIndent;
    }

    public List<DocNode> getChildren() {
        return children;
    }

    public boolean isFirstLineIndent() {
        return firstLineIndent;
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
        return firstLineIndent == that.firstLineIndent && children.equals(that.children);
    }

    @Override
    public int hashCode() {
        return 31 * children.hashCode() + Boolean.hashCode(firstLineIndent);
    }

    @Override
    public String toString() {
        return "ParagraphNode{children=" + children + ", indent=" + firstLineIndent + "}";
    }
}
