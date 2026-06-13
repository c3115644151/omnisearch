package com.cy311.omnisearch.data.source;

import com.cy311.omnisearch.data.model.CaptchaContext;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects and parses mcmod.cn CAPTCHA challenge pages.
 * <p>
 * Based on verified patterns from MapleSugar365 fork.
 * Pure Java, zero MC dependency.
 * <p>
 * Usage:
 * <pre>{@code
 * McmodCaptchaHandler handler = new McmodCaptchaHandler();
 * if (handler.isCaptchaPage(html)) {
 *     CaptchaContext ctx = handler.parseCaptcha(html, pageUrl);
 *     // render ctx.captchaImageUrl() as image, prompt user with ctx.captchaId()
 * }
 * }</pre>
 */
public class McmodCaptchaHandler {

    private static final Pattern CAPTCHA_CHECK_PATTERN = Pattern.compile("安全验证");
    private static final Pattern CAPTCHA_IMAGE_PATTERN = Pattern.compile(
        "<img\\s+id=\"captchaImage\"\\s+[^>]*src=\"data:image/png;base64,([^\"]+)\"");
    private static final Pattern CAPTCHA_QUESTION_PATTERN = Pattern.compile(
        "<p\\s+class=\"captcha-question\"[^>]*>\\s*图中有多少个\\s*<b\\s+class=\"item\"[^>]*>([^<]+)</b>\\s*\\?\\s*</p>");

    /**
     * Detects if the given HTML is a mcmod.cn CAPTCHA challenge page.
     *
     * @param html Raw HTML response
     * @return true if the page is a CAPTCHA challenge
     */
    public boolean isCaptchaPage(String html) {
        return html != null && CAPTCHA_CHECK_PATTERN.matcher(html).find()
            && html.contains("captcha-image-container");
    }

    /**
     * Parses a CAPTCHA page HTML into a CaptchaContext.
     * <p>
     * {@link CaptchaContext#captchaImageUrl()} stores the full data URI
     * ({@code data:image/png;base64,...}) of the CAPTCHA image.
     * {@link CaptchaContext#captchaId()} stores the question text
     * (e.g. "图中有多少个苦力怕").
     *
     * @param html    The CAPTCHA page HTML
     * @param pageUrl The URL that triggered the CAPTCHA (reserved for future use)
     * @return CaptchaContext with image data and question, or null if parsing fails
     */
    public CaptchaContext parseCaptcha(String html, String pageUrl) {
        if (html == null || html.isBlank()) {
            return null;
        }

        Matcher imgMatcher = CAPTCHA_IMAGE_PATTERN.matcher(html);
        Matcher questionMatcher = CAPTCHA_QUESTION_PATTERN.matcher(html);

        if (!imgMatcher.find() || !questionMatcher.find()) {
            return null;
        }

        String imageBase64 = imgMatcher.group(1);
        String itemName = questionMatcher.group(1).trim();
        String question = "图中有多少个" + itemName;

        // captchaImageUrl stores the full data URI for direct rendering
        String dataUri = "data:image/png;base64," + imageBase64;

        return new CaptchaContext(dataUri, question);
    }
}
