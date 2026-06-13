package com.cy311.omnisearch.data.model.document;

import org.jetbrains.annotations.Nullable;
import java.util.Objects;

public class ImageNode extends DocNode {
    private final String url;
    private final String alt;
    @Nullable
    private final String localPath;

    public ImageNode(String url, String alt, @Nullable String localPath) {
        this.url = Objects.requireNonNull(url, "url must not be null");
        this.alt = Objects.requireNonNull(alt, "alt must not be null");
        this.localPath = localPath;
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
            && Objects.equals(localPath, imageNode.localPath);
    }

    @Override
    public int hashCode() {
        int result = url.hashCode();
        result = 31 * result + alt.hashCode();
        result = 31 * result + (localPath != null ? localPath.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ImageNode{url='" + url + "', alt='" + alt
            + "', localPath='" + localPath + "'}";
    }
}
