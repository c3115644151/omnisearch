package com.cy311.omnisearch.client.screen.state;

import com.cy311.omnisearch.client.render.document.DocumentRenderer;
import com.cy311.omnisearch.client.render.document.PreparedDocumentLayout;
import com.cy311.omnisearch.data.model.ItemPage;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record DetailViewState(
    @Nullable ItemPage page,
    int scrollOffset,
    boolean draggingScrollbar,
    @Nullable String cachedPageId,
    int cachedWidth,
    int cachedLayoutVersion,
    @Nullable PreparedDocumentLayout cachedLayout,
    List<DocumentRenderer.LinkHit> cachedLinks,
    int contentHeight
) {
    public static DetailViewState initial() {
        return new DetailViewState(null, 0, false, null, 0, -1, null, List.of(), 0);
    }

    public DetailViewState withPage(@Nullable ItemPage page) {
        return new DetailViewState(page, scrollOffset, draggingScrollbar, cachedPageId, cachedWidth, cachedLayoutVersion, cachedLayout, cachedLinks, contentHeight);
    }

    public DetailViewState withScrollOffset(int scrollOffset) {
        return new DetailViewState(page, scrollOffset, draggingScrollbar, cachedPageId, cachedWidth, cachedLayoutVersion, cachedLayout, cachedLinks, contentHeight);
    }

    public DetailViewState withDraggingScrollbar(boolean draggingScrollbar) {
        return new DetailViewState(page, scrollOffset, draggingScrollbar, cachedPageId, cachedWidth, cachedLayoutVersion, cachedLayout, cachedLinks, contentHeight);
    }

    public DetailViewState withCachedLayout(@Nullable String cachedPageId, int cachedWidth, int cachedLayoutVersion,
                                            @Nullable PreparedDocumentLayout cachedLayout,
                                            List<DocumentRenderer.LinkHit> cachedLinks) {
        return new DetailViewState(page, scrollOffset, draggingScrollbar, cachedPageId, cachedWidth, cachedLayoutVersion, cachedLayout, cachedLinks, contentHeight);
    }

    public DetailViewState withContentHeight(int contentHeight) {
        return new DetailViewState(page, scrollOffset, draggingScrollbar, cachedPageId, cachedWidth, cachedLayoutVersion, cachedLayout, cachedLinks, contentHeight);
    }

    public DetailViewState clearLayoutCache() {
        return new DetailViewState(page, scrollOffset, draggingScrollbar, null, 0, -1, null, List.of(), contentHeight);
    }

    public DetailViewState resetForNewPage() {
        return new DetailViewState(null, 0, false, null, 0, -1, null, List.of(), 0);
    }
}
