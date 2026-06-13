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
}
