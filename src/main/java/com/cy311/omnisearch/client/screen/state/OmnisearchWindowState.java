package com.cy311.omnisearch.client.screen.state;

public record OmnisearchWindowState(
    WindowSessionState window,
    SearchSessionState search,
    DetailViewState detail
) {
    public static OmnisearchWindowState initial() {
        return new OmnisearchWindowState(
            WindowSessionState.initial(),
            SearchSessionState.initial(),
            DetailViewState.initial()
        );
    }

    public OmnisearchWindowState withWindow(WindowSessionState window) {
        return new OmnisearchWindowState(window, search, detail);
    }

    public OmnisearchWindowState withSearch(SearchSessionState search) {
        return new OmnisearchWindowState(window, search, detail);
    }

    public OmnisearchWindowState withDetail(DetailViewState detail) {
        return new OmnisearchWindowState(window, search, detail);
    }
}
