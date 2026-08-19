package com.cy311.omnisearch.data.model.document;

import java.util.Objects;

/**
 * An image caption (mcmod.cn: {@code <span class="figcaption">…</span>}).
 * Rendered as a small gray centered note under the image, distinct from body text.
 */
public class CaptionNode extends DocNode {
    private final String text;

    public CaptionNode(String text) {
        this.text = Objects.requireNonNull(text, "text must not be null");
    }

    public String getText() {
        return text;
    }

    public String getType() {
        return "caption";
    }

    @Override
    public <T> T accept(DocNodeVisitor<T> visitor) {
        return visitor.visitCaption(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CaptionNode that)) return false;
        return text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    @Override
    public String toString() {
        return "CaptionNode{text='" + text + "'}";
    }
}
