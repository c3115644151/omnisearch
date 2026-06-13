package com.cy311.omnisearch.search;

import com.cy311.omnisearch.data.model.CaptchaContext;
import com.cy311.omnisearch.data.model.ItemPage;
import com.cy311.omnisearch.data.model.SearchHit;
import com.cy311.omnisearch.data.model.SearchQuery;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public record SearchState(
    Page currentPage,
    SearchQuery query,
    List<SearchHit> results,
    @Nullable ItemPage detailPage,
    NavigationStack navStack,
    LoadingState loading,
    @Nullable String errorMessage,
    @Nullable CaptchaContext captcha
) {
    public enum Page { SEARCH, RESULTS, DETAIL }
    public enum LoadingState { IDLE, LOADING, CAPTCHA_REQUIRED, ERROR }

    public static SearchState initial() {
        return new SearchState(
            Page.SEARCH,
            new SearchQuery(""),
            List.of(),
            null,
            new NavigationStack(),
            LoadingState.IDLE,
            null,
            null
        );
    }

    public SearchState withPage(Page page) {
        return new SearchState(page, query, results, detailPage, navStack, loading, errorMessage, captcha);
    }

    public SearchState withQuery(SearchQuery query) {
        return new SearchState(currentPage, query, results, detailPage, navStack, loading, errorMessage, captcha);
    }

    public SearchState withResults(List<SearchHit> results) {
        return new SearchState(currentPage, query, results, detailPage, navStack, loading, errorMessage, captcha);
    }

    public SearchState withDetailPage(@Nullable ItemPage detailPage) {
        return new SearchState(currentPage, query, results, detailPage, navStack, loading, errorMessage, captcha);
    }

    public SearchState withNavStack(NavigationStack navStack) {
        return new SearchState(currentPage, query, results, detailPage, navStack, loading, errorMessage, captcha);
    }

    public SearchState withLoading(LoadingState loading) {
        return new SearchState(currentPage, query, results, detailPage, navStack, loading, errorMessage, captcha);
    }

    public SearchState withErrorMessage(@Nullable String errorMessage) {
        return new SearchState(currentPage, query, results, detailPage, navStack, loading, errorMessage, captcha);
    }

    public SearchState withCaptcha(@Nullable CaptchaContext captcha) {
        return new SearchState(currentPage, query, results, detailPage, navStack, loading, errorMessage, captcha);
    }
}
