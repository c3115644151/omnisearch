package com.cy311.omnisearch.data.model.document;

import java.util.Objects;

public class TextNode extends DocNode {
    private final String text;

    public TextNode(String text) {
        this.text = Objects.requireNonNull(text, "text must not be null");
    }

    public String getText() {
        return text;
    }

    public String getType() {
        return "text";
    }

    @Override
    public <T> T accept(DocNodeVisitor<T> visitor) {
        return visitor.visitText(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TextNode textNode)) return false;
        return text.equals(textNode.text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    @Override
    public String toString() {
        return "TextNode{text='" + text + "'}";
    }
}
