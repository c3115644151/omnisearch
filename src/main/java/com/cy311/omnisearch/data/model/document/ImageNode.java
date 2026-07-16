package com.cy311.omnisearch.data.model.document;

import org.jetbrains.annotations.Nullable;
import java.util.Objects;

public class ImageNode extends DocNode {
    private final String url;
    private final String alt;
    @Nullable
    private final String localPath;
    private final int origWidth;   // original width from HTML (0 if unknown)
    private final int origHeight;  // original height from HTML (0 if unknown)

    public ImageNode(String url, String alt, @Nullable String localPath) {
        this(url, alt, localPath, 0, 0);
    }

    public ImageNode(String url, String alt, @Nullable String localPath, int origWidth, int origHeight) {
        this.url = Objects.requireNonNull(url, "url must not be null");
        this.alt = Objects.requireNonNull(alt, "alt must not be null");
        this.localPath = localPath;
        this.origWidth = origWidth;
        this.origHeight = origHeight;
    }

    public String getUrl() {
        return url;
    }

    public String getAlt() {
        return alt;
    }

    @Nullable
    public String getLocalPath() {
        return localPath;
    }

    public int getOrigWidth() {
        return origWidth;
    }

    public int getOrigHeight() {
        return origHeight;
    }

    public String getType() {
        return "image";
    }

    @Override
    public <T> T accept(DocNodeVisitor<T> visitor) {
        return visitor.visitImage(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ImageNode imageNode)) return false;
        return url.equals(imageNode.url) && alt.equals(imageNode.alt)
            && Objects.equals(localPath, imageNode.localPath)
            && origWidth == imageNode.origWidth && origHeight == imageNode.origHeight;
    }

    @Override
    public int hashCode() {
        int result = url.hashCode();
        result = 31 * result + alt.hashCode();
        result = 31 * result + (localPath != null ? localPath.hashCode() : 0);
        result = 31 * result + origWidth;
        result = 31 * result + origHeight;
        return result;
    }

    @Override
    public String toString() {
        return "ImageNode{url='" + url + "', alt='" + alt
            + "', localPath='" + localPath + "', origW=" + origWidth + ", origH=" + origHeight + "}";
    }
}
