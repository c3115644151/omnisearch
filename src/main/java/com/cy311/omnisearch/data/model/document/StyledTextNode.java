package com.cy311.omnisearch.data.model.document;

import java.util.Objects;

public class StyledTextNode extends DocNode {
    private final String text;
    private final TextStyle style;

    public StyledTextNode(String text, TextStyle style) {
        this.text = Objects.requireNonNull(text, "text must not be null");
        this.style = Objects.requireNonNull(style, "style must not be null");
    }

    public String getText() {
        return text;
    }

    public TextStyle getStyle() {
        return style;
    }

    public String getType() {
        return "styled_text";
    }

    @Override
    public <T> T accept(DocNodeVisitor<T> visitor) {
        return visitor.visitStyledText(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StyledTextNode that)) return false;
        return text.equals(that.text) && style.equals(that.style);
    }

    @Override
    public int hashCode() {
        return 31 * text.hashCode() + style.hashCode();
    }

    @Override
    public String toString() {
        return "StyledTextNode{text='" + text + "', style=" + style + "}";
    }
}
