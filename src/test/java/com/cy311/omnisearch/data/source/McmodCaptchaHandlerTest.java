package com.cy311.omnisearch.data.source;

import com.cy311.omnisearch.data.model.CaptchaContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class McmodCaptchaHandlerTest {

    private final McmodCaptchaHandler handler = new McmodCaptchaHandler();

    // ══════════════════════════════════════════════
    // isCaptchaPage
    // ══════════════════════════════════════════════

    @Test
    void isCaptchaPage_returnsTrueForCaptchaPage() {
        String html = "<html><body>"
            + "<div class=\"captcha-image-container\">"
            + "<p class=\"tips\">安全验证</p>"
            + "<img id=\"captchaImage\" src=\"data:image/png;base64,abc123\">"
            + "</div></body></html>";
        assertTrue(handler.isCaptchaPage(html));
    }

    @Test
    void isCaptchaPage_returnsFalseForNormalPage() {
        String html = "<html><body><div>正常内容</div></body></html>";
        assertFalse(handler.isCaptchaPage(html));
    }

    @Test
    void isCaptchaPage_returnsFalseForNull() {
        assertFalse(handler.isCaptchaPage(null));
    }

    @Test
    void isCaptchaPage_returnsFalseForEmptyString() {
        assertFalse(handler.isCaptchaPage(""));
    }

    @Test
    void isCaptchaPage_returnsFalseWhenCaptchaCheckMissing() {
        // Contains captcha-image-container but not "安全验证"
        String html = "<html><body><div class=\"captcha-image-container\"></div></body></html>";
        assertFalse(handler.isCaptchaPage(html));
    }

    @Test
    void isCaptchaPage_returnsFalseWhenContainerMissing() {
        // Contains "安全验证" but not captcha-image-container
        String html = "<html><body><p>安全验证</p></body></html>";
        assertFalse(handler.isCaptchaPage(html));
    }

    // ══════════════════════════════════════════════
    // parseCaptcha
    // ══════════════════════════════════════════════

    @Test
    void parseCaptcha_extractsBase64AndQuestion() {
        String html = "<html><body>"
            + "<div class=\"captcha-image-container\">"
            + "<p class=\"tips\">安全验证</p>"
            + "<img id=\"captchaImage\" src=\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUg\">"
            + "<p class=\"captcha-question\">图中有多少个<b class=\"item\">苦力怕</b>?</p>"
            + "</div></body></html>";

        CaptchaContext ctx = handler.parseCaptcha(html, "https://www.mcmod.cn/item/123.html");

        assertNotNull(ctx);
        assertEquals("data:image/png;base64,iVBORw0KGgoAAAANSUhEUg", ctx.captchaImageUrl());
        assertEquals("图中有多少个苦力怕", ctx.captchaId());
    }

    @Test
    void parseCaptcha_handlesDifferentItemName() {
        String html = "<html><body>"
            + "<img id=\"captchaImage\" src=\"data:image/png;base64,xyz789\">"
            + "<p class=\"captcha-question\">图中有多少个<b class=\"item\">僵尸</b>?</p>"
            + "</body></html>";

        CaptchaContext ctx = handler.parseCaptcha(html, "https://www.mcmod.cn/");

        assertNotNull(ctx);
        assertTrue(ctx.captchaImageUrl().startsWith("data:image/png;base64,"));
        assertTrue(ctx.captchaId().contains("僵尸"));
    }

    @Test
    void parseCaptcha_handlesSpacesAroundTags() {
        // HTML with extra whitespace around the question elements
        String html = "<html><body>"
            + "<img id=\"captchaImage\" src=\"data:image/png;base64,abcdef\">"
            + "<p class=\"captcha-question\" style=\"text-align:center\">\n"
            + "    图中有多少个    <b class=\"item\">   骷髅   </b>   ?\n"
            + "</p></body></html>";

        CaptchaContext ctx = handler.parseCaptcha(html, "https://www.mcmod.cn/");

        assertNotNull(ctx);
        assertEquals("图中有多少个骷髅", ctx.captchaId());
    }

    @Test
    void parseCaptcha_returnsNullForNonCaptchaPage() {
        String html = "<html><body>正常页面内容</body></html>";
        assertNull(handler.parseCaptcha(html, "https://www.mcmod.cn/"));
    }

    @Test
    void parseCaptcha_returnsNullForMissingImage() {
        // Has question but no image
        String html = "<html><body>"
            + "<p class=\"captcha-question\">图中有多少个<b class=\"item\">苦力怕</b>?</p>"
            + "</body></html>";
        assertNull(handler.parseCaptcha(html, "https://www.mcmod.cn/"));
    }

    @Test
    void parseCaptcha_returnsNullForMissingQuestion() {
        // Has image but no question
        String html = "<html><body>"
            + "<img id=\"captchaImage\" src=\"data:image/png;base64,abc\">"
            + "</body></html>";
        assertNull(handler.parseCaptcha(html, "https://www.mcmod.cn/"));
    }

    @Test
    void parseCaptcha_returnsNullForNullHtml() {
        assertNull(handler.parseCaptcha(null, "https://www.mcmod.cn/"));
    }

    @Test
    void parseCaptcha_returnsNullForEmptyHtml() {
        assertNull(handler.parseCaptcha("", "https://www.mcmod.cn/"));
    }

    @Test
    void parseCaptcha_returnsNullForBlankHtml() {
        assertNull(handler.parseCaptcha("   ", "https://www.mcmod.cn/"));
    }

    // ══════════════════════════════════════════════
    // parseCaptcha — form action URL extraction
    // ══════════════════════════════════════════════

    @Test
    void parseCaptcha_extractsFormActionUrl() {
        String html = "<html><body>"
            + "<img id=\"captchaImage\" src=\"data:image/png;base64,abc\">"
            + "<p class=\"captcha-question\">图中有多少个<b class=\"item\">苦力怕</b>?</p>"
            + "<form id=\"captchaForm\" action=\"/captcha/verify\" method=\"POST\">"
            + "</form></body></html>";

        CaptchaContext ctx = handler.parseCaptcha(html, "https://www.mcmod.cn/item/123.html");

        assertNotNull(ctx);
        assertEquals("https://www.mcmod.cn/captcha/verify", ctx.answerUrl());
    }

    @Test
    void parseCaptcha_fallsBackToPageUrlWhenNoForm() {
        String html = "<html><body>"
            + "<img id=\"captchaImage\" src=\"data:image/png;base64,abc\">"
            + "<p class=\"captcha-question\">图中有多少个<b class=\"item\">爬行者</b>?</p>"
            + "</body></html>";

        CaptchaContext ctx = handler.parseCaptcha(html, "https://www.mcmod.cn/s?key=test");

        assertNotNull(ctx);
        assertEquals("https://www.mcmod.cn/s?key=test", ctx.answerUrl());
    }

    // ══════════════════════════════════════════════
    // extractHiddenId
    // ══════════════════════════════════════════════

    @Test
    void extractHiddenId_findsCaptchaId() {
        // Full captcha page with hidden fields
        String html = "<html><body>"
            + "<form>"
            + "<img src=\"data:image/png;base64,abc123\">"
            + "<p>图中有多少个<b>苦力怕</b>?</p>"
            + "<input type=\"hidden\" name=\"captcha_id\" value=\"abc123def456\">"
            + "</form></body></html>";

        CaptchaContext ctx = handler.parseCaptcha(html, "https://www.mcmod.cn/");
        assertNotNull(ctx);
        assertEquals("abc123def456", ctx.hiddenFields().get("captcha_id"));
    }

    @Test
    void extractHiddenId_returnsNullWhenNotFound() {
        assertNull(handler.parseCaptcha("<html></html>", "https://www.mcmod.cn/"));
    }

    @Test
    void extractHiddenId_returnsNullForNullHtml() {
        assertNull(handler.parseCaptcha(null, "https://www.mcmod.cn/"));
    }

    // ══════════════════════════════════════════════
    // Real-world mcmod.cn captcha page structure
    // ══════════════════════════════════════════════

    /**
     * Builds a realistic captcha page HTML based on the actual mcmod.cn structure
     * observed in production (form with no action, base64 image, error-message div).
     */
    private static String buildRealisticCaptchaHtml(String extraContent) {
        return "<!DOCTYPE html>\n"
            + "<html lang=\"zh-CN\">\n"
            + "<head><meta charset=\"utf-8\"><title>安全验证</title></head>\n"
            + "<body>\n"
            + "<form method=\"POST\" id=\"captchaForm\">\n"
            + "  <div class=\"error-message\">验证码错误</div>\n"
            + "  <div class=\"captcha-image-container\">\n"
            + "    <img id=\"captchaImage\" src=\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUg\">\n"
            + "    <p class=\"captcha-question\">图中有多少个<b class=\"item\">苦力怕</b>?</p>\n"
            + "  </div>\n"
            + extraContent
            + "</form>\n"
            + "</body>\n"
            + "</html>";
    }

    @Test
    void parseCaptcha_realisticMcmodPage_detectedAsCaptcha() {
        String html = buildRealisticCaptchaHtml("");
        assertTrue(handler.isCaptchaPage(html));
    }

    @Test
    void parseCaptcha_realisticMcmodPage_parsesSuccessfully() {
        String html = buildRealisticCaptchaHtml("");
        CaptchaContext ctx = handler.parseCaptcha(html, "https://www.mcmod.cn/s?key=test");
        assertNotNull(ctx);
        assertEquals("data:image/png;base64,iVBORw0KGgoAAAANSUhEUg", ctx.captchaImageUrl());
        assertEquals("图中有多少个苦力怕", ctx.captchaId());
        // Form has no action attribute → answerUrl = pageUrl (fallback)
        assertEquals("https://www.mcmod.cn/s?key=test", ctx.answerUrl());
    }

    @Test
    void parseCaptcha_realisticMcmodPage_withHiddenInputs() {
        // Simulate hidden inputs that mcmod.cn might include
        String html = buildRealisticCaptchaHtml(
            "  <input type=\"hidden\" name=\"captcha_id\" value=\"abc123\">\n"
            + "  <input type=\"hidden\" name=\"crumb\" value=\"csrf_token_here\">\n");
        CaptchaContext ctx = handler.parseCaptcha(html, "https://www.mcmod.cn/s?key=test");
        assertNotNull(ctx);
        // hidden id extraction should work
        assertEquals("abc123", ctx.hiddenFields().get("captcha_id"));
    }

    @Test
    void parseCaptcha_realisticMcmodPage_noActionFallback() {
        // Verify that form with no action falls back to pageUrl
        String html = buildRealisticCaptchaHtml("");
        CaptchaContext ctx = handler.parseCaptcha(html, "https://www.mcmod.cn/item/123.html");
        assertNotNull(ctx);
        assertEquals("https://www.mcmod.cn/item/123.html", ctx.answerUrl());
    }

    @Test
    void parseCaptcha_realisticMcmodPage_withRedirectAction() {
        // Verify that form WITH action attribute uses the action URL
        String html = "<!DOCTYPE html><html><body>"
            + "<form method=\"POST\" id=\"captchaForm\" action=\"/captcha/verify\">"
            + "  <div class=\"error-message\"></div>"
            + "  <div class=\"captcha-image-container\">"
            + "    <img id=\"captchaImage\" src=\"data:image/png;base64,abc\">"
            + "    <p class=\"captcha-question\">图中有多少个<b class=\"item\">爬行者</b>?</p>"
            + "  </div>"
            + "</form>"
            + "</body></html>";
        CaptchaContext ctx = handler.parseCaptcha(html, "https://www.mcmod.cn/s?key=test");
        assertNotNull(ctx);
        assertEquals("https://www.mcmod.cn/captcha/verify", ctx.answerUrl());
    }
}
