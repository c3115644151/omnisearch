package com.cy311.omnisearch.data.source;

import com.cy311.omnisearch.data.model.CaptchaContext;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.HashMap;
import java.util.Map;

/**
 * Detects and parses mcmod.cn CAPTCHA challenge pages.
 * <p>
 * Uses Jsoup for robust HTML parsing instead of fragile Regex.
 */
public class McmodCaptchaHandler {

    /**
     * Detects if the given HTML is a mcmod.cn CAPTCHA challenge page.
     *
     * @param html Raw HTML response
     * @return true if the page is a CAPTCHA challenge
     */
    public boolean isCaptchaPage(String html) {
        if (html == null) return false;
        return html.contains("安全验证") && html.contains("captcha");
    }

    /**
     * Parses a CAPTCHA page HTML into a CaptchaContext.
     *
     * @param html    The CAPTCHA page HTML
     * @param pageUrl The URL that triggered the CAPTCHA
     * @return CaptchaContext with image data and question, or null if parsing fails
     */
    public CaptchaContext parseCaptcha(String html, String pageUrl) {
        if (html == null || html.isBlank()) {
            return null;
        }

        Document doc = Jsoup.parse(html, pageUrl);

        // Find image: <img src="data:image/png;base64,...">
        Element img = doc.selectFirst("img[src^=data:image/png;base64]");
        if (img == null) {
            // fallback: any img with captcha in class/id
            img = doc.selectFirst("img[class*=captcha], img[id*=captcha]");
            if (img == null || !img.attr("src").startsWith("data:image/png")) {
                return null;
            }
        }
        String dataUri = img.attr("src");

        // Find question text — normalize whitespace, strip trailing "?"
        // eslint-disable-next-line
        // E.g. "图中有多少个 苦力怕 ?" → "图中有多少个苦力怕"
        String question = null;
        Element questionEl = doc.selectFirst("p:contains(图中有多少个)");
        if (questionEl != null) {
            question = questionEl.text().replaceAll("\\s+", ""); // squash whitespace
        } else {
            String bodyText = doc.body().text();
            int idx = bodyText.indexOf("图中有多少个");
            if (idx != -1) {
                question = bodyText.substring(idx).replaceAll("\\s+", "");
            }
        }
        if (question == null || question.isBlank()) return null;
        // Strip trailing question marks (both half/full width)
        question = question.replaceAll("[？?]+$", "").trim();

        // Action URL and hidden fields
        Element form = doc.selectFirst("form");
        String answerUrl = pageUrl;
        Map<String, String> hiddenFields = new HashMap<>();
        if (form != null) {
            if (form.hasAttr("action")) {
                answerUrl = form.absUrl("action");
                if (answerUrl.isBlank()) answerUrl = pageUrl;
            }
            for (Element input : form.select("input[type=hidden]")) {
                hiddenFields.put(input.attr("name"), input.attr("value"));
            }
        }

        return new CaptchaContext(dataUri, question, answerUrl, hiddenFields);
    }
}
