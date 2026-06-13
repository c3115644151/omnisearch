package com.cy311.omnisearch.data.source;

import com.cy311.omnisearch.data.model.CaptchaContext;

/**
 * Thrown when mcmod.cn responds with a CAPTCHA challenge page.
 * <p>
 * Carries a {@link CaptchaContext} with the CAPTCHA image data and question
 * text, so the GUI layer can display the challenge to the user.
 * <p>
 * Unchecked (RuntimeException) because it propagates through
 * {@link java.util.concurrent.CompletableFuture} lambdas which cannot throw
 * checked exceptions.
 */
public class CaptchaRequiredException extends RuntimeException {
    private final CaptchaContext captchaContext;

    public CaptchaRequiredException(CaptchaContext captchaContext) {
        super("CAPTCHA challenge required");
        this.captchaContext = captchaContext;
    }

    public CaptchaContext getCaptchaContext() {
        return captchaContext;
    }
}
