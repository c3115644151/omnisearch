package com.cy311.omnisearch.data.model.document;

import java.util.Objects;

public class ImageInlineNode extends DocNode {
    private final String url;
    private final String alt;

    public ImageInlineNode(String url, String alt) {
        this.url = Objects.requireNonNull(url, "url must not be null");
        this.alt = Objects.requireNonNull(alt, "alt must not be null");
    }

    public String getUrl() {
        return url;
    }

    public String getAlt() {
        return alt;
    }

    public String getType() {
        return "image_inline";
    }

    @Override
    public <T> T accept(DocNodeVisitor<T> visitor) {
        return visitor.visitImageInline(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ImageInlineNode that)) return false;
        return url.equals(that.url) && alt.equals(that.alt);
    }

    @Override
    public int hashCode() {
        return 31 * url.hashCode() + alt.hashCode();
    }

    @Override
    public String toString() {
        return "ImageInlineNode{url='" + url + "', alt='" + alt + "'}";
    }
}
