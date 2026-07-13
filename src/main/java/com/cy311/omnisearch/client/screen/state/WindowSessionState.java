package com.cy311.omnisearch.client.screen.state;

import com.cy311.omnisearch.data.model.CaptchaContext;
import com.cy311.omnisearch.data.model.PendingRequest;
import com.cy311.omnisearch.search.SearchState;
import org.jetbrains.annotations.Nullable;

public record WindowSessionState(
    SearchState.LoadingState loading,
    @Nullable String errorMessage,
    @Nullable CaptchaContext captcha,
    @Nullable PendingRequest pendingRequest
) {
    public static WindowSessionState initial() {
        return new WindowSessionState(SearchState.LoadingState.IDLE, null, null, null);
    }

    public WindowSessionState withLoading(SearchState.LoadingState loading) {
        return new WindowSessionState(loading, errorMessage, captcha, pendingRequest);
    }

    public WindowSessionState withErrorMessage(@Nullable String errorMessage) {
        return new WindowSessionState(loading, errorMessage, captcha, pendingRequest);
    }

    public WindowSessionState withCaptcha(@Nullable CaptchaContext captcha) {
        return new WindowSessionState(loading, errorMessage, captcha, pendingRequest);
    }

    public WindowSessionState withPendingRequest(@Nullable PendingRequest pendingRequest) {
        return new WindowSessionState(loading, errorMessage, captcha, pendingRequest);
    }
}
