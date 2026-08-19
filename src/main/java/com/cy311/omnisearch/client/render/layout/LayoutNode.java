package com.cy311.omnisearch.client.render.layout;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A positioned layout node produced by {@link LayoutEngine}.
 * Pure Java, no MC dependency.
 */
public class LayoutNode {

    public int x, y, w, h;
    public final LayoutType type;
    @Nullable public final String text;
    @Nullable public final String imageUrl;
    @Nullable public final String linkUrl;
    @Nullable public final String alt;
    public int headingLevel;        // 1-3 for HEADING nodes
    public boolean isBold;          // for STYLED_TEXT nodes
    public boolean isItalic;        // for STYLED_TEXT nodes
    public boolean isUnderline;     // for STYLED_TEXT nodes
    public boolean isStrikethrough; // for STYLED_TEXT nodes
    public int textColor = -1;      // ARGB color (if set)
    public boolean isHeader;        // for TABLE cells that are headers
    public float textScale = 1.0f;  // render scale for the text (captions use <1)
    public final List<LayoutNode> children;
    public final List<LayoutNode> inlineChildren;

    public LayoutNode(LayoutType type, @Nullable String text, @Nullable String imageUrl,
                      @Nullable String linkUrl, @Nullable String alt) {
        this.type = type;
        this.text = text;
        this.imageUrl = imageUrl;
        this.linkUrl = linkUrl;
        this.alt = alt;
        this.children = new ArrayList<>();
        this.inlineChildren = new ArrayList<>();
    }

    public LayoutNode(LayoutType type, @Nullable String text) {
        this(type, text, null, null, null);
    }

    public LayoutNode(LayoutType type) {
        this(type, null, null, null, null);
    }

    /** Convenience: set position and size */
    public LayoutNode at(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        return this;
    }

    /** Adds a child layout node */
    public LayoutNode add(LayoutNode child) {
        children.add(child);
        return this;
    }

    /** Adds an inline child layout node */
    public LayoutNode addInline(LayoutNode child) {
        inlineChildren.add(child);
        return this;
    }

    public LayoutNode withHeadingLevel(int level) {
        this.headingLevel = level;
        return this;
    }

    public LayoutNode withBold(boolean bold) {
        this.isBold = bold;
        return this;
    }

    public LayoutNode withItalic(boolean italic) {
        this.isItalic = italic;
        return this;
    }

    public LayoutNode withUnderline(boolean underline) {
        this.isUnderline = underline;
        return this;
    }

    public LayoutNode withStrikethrough(boolean strikethrough) {
        this.isStrikethrough = strikethrough;
        return this;
    }

    public LayoutNode withColor(int color) {
        this.textColor = color;
        return this;
    }

    public LayoutNode withTextScale(float scale) {
        this.textScale = scale;
        return this;
    }

    public LayoutNode withIsHeader(boolean header) {
        this.isHeader = header;
        return this;
    }

    public boolean isBlock() {
        return type == LayoutType.PARAGRAPH || type == LayoutType.HEADING
            || type == LayoutType.IMAGE || type == LayoutType.TABLE
            || type == LayoutType.LIST || type == LayoutType.LIST_ITEM
            || type == LayoutType.DIVIDER || type == LayoutType.LINK;
    }
}
