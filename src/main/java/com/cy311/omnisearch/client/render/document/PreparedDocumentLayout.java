package com.cy311.omnisearch.client.render.document;

import com.cy311.omnisearch.client.render.layout.LayoutNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Immutable snapshot of a fully-laid-out document.
 * <p>
 * All coordinates are <strong>relative</strong> to the content area origin (0, 0).
 * Callers must add content-area offsets and scroll offset during painting and hit-testing.
 */
public record PreparedDocumentLayout(
    List<LayoutNode> nodes,
    int height,
    Set<String> imageUrls
) {
    public PreparedDocumentLayout(List<LayoutNode> nodes, int height) {
        this(nodes, height, Set.copyOf(collectImageUrls(nodes)));
    }

    public List<DocumentRenderer.LinkHit> extractLinks() {
        var result = new ArrayList<DocumentRenderer.LinkHit>();
        collectLinks(nodes, result);
        return Collections.unmodifiableList(result);
    }

    /**
     * True once every image referenced by this document has finished loading.
     */
    public boolean allImagesLoaded() {
        return imageUrls.isEmpty();
    }

    private static void collectLinks(List<LayoutNode> nodes, List<DocumentRenderer.LinkHit> out) {
        for (LayoutNode node : nodes) {
            if (node.linkUrl != null && node.w > 0 && node.h > 0) {
                out.add(new DocumentRenderer.LinkHit(node.x, node.y, node.w, node.h, node.linkUrl));
            }
            collectLinks(node.children, out);
            collectLinks(node.inlineChildren, out);
        }
    }

    private static List<String> collectImageUrls(List<LayoutNode> nodes) {
        List<String> urls = new ArrayList<>();
        collectImageUrls(nodes, urls);
        return urls;
    }

    private static void collectImageUrls(List<LayoutNode> nodes, List<String> out) {
        for (LayoutNode node : nodes) {
            if (node.type == com.cy311.omnisearch.client.render.layout.LayoutType.IMAGE
                    || node.type == com.cy311.omnisearch.client.render.layout.LayoutType.INLINE_IMAGE) {
                if (node.imageUrl != null && !node.imageUrl.isBlank()
                        && !node.imageUrl.startsWith("mc-icon://")) {
                    out.add(node.imageUrl);
                }
            }
            collectImageUrls(node.children, out);
            collectImageUrls(node.inlineChildren, out);
        }
    }
}
