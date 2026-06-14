package com.cy311.omnisearch.data.model;

import java.util.Map;

/**
 * Context for a CAPTCHA challenge from mcmod.cn.
 *
 * @param captchaImageUrl data URI of the CAPTCHA image (base64 PNG)
 * @param captchaId       the captcha question text (e.g. "图中有多少个苦力怕")
 * @param answerUrl       the URL to submit the captcha answer to
 */
public record CaptchaContext(
    String captchaImageUrl,
    String captchaId,
    String answerUrl,
    Map<String, String> hiddenFields
) {
    public CaptchaContext(String captchaImageUrl, String captchaId, String answerUrl) {
        this(captchaImageUrl, captchaId, answerUrl, Map.of());
    }
}
