package com.cy311.omnisearch.client.render.document;

import com.cy311.omnisearch.client.render.layout.LayoutNode;
import java.util.List;

/**
 * Immutable snapshot of a fully-laid-out document.
 * <p>
 * All coordinates are <strong>relative</strong> to the content area origin (0, 0).
 * Callers must add content-area offsets and scroll offset during painting and hit-testing.
 */
public record PreparedDocumentLayout(
    List<LayoutNode> nodes,
    int height
) {
    public List<DocumentRenderer.LinkHit> extractLinks() {
        var result = new java.util.ArrayList<DocumentRenderer.LinkHit>();
        collectLinks(nodes, result);
        return java.util.Collections.unmodifiableList(result);
    }

    private static void collectLinks(List<LayoutNode> nodes, java.util.List<DocumentRenderer.LinkHit> out) {
        for (LayoutNode node : nodes) {
            if (node.linkUrl != null && node.w > 0 && node.h > 0) {
                out.add(new DocumentRenderer.LinkHit(node.x, node.y, node.w, node.h, node.linkUrl));
            }
            collectLinks(node.children, out);
            collectLinks(node.inlineChildren, out);
        }
    }
}
