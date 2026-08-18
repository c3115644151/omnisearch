package com.cy311.omnisearch.data.parser;

import com.cy311.omnisearch.OmnisearchMod;
import com.cy311.omnisearch.data.model.*;
import com.cy311.omnisearch.data.model.document.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses mcmod.cn HTML pages into structured data.model objects.
 * <p>
 * Pure Java, zero MC dependency. Only depends on Jsoup and data.model.
 */
public class McmodParser {

    private static final Pattern LINK_TEXT_PATTERN = Pattern.compile("^(.*?)\\s*-\\s*(.*)$");
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("^\\(([^)]+)\\)\\s*(.*)$");
    private static final Pattern PAREN_ENGLISH = Pattern.compile("\\s*\\([^)]*\\)");
    private static final Pattern COLOR_PATTERN = Pattern.compile("color\\s*:\\s*(#[0-9a-fA-F]{3,8})\\b");
    private static final Pattern PAGE_PARAM_PATTERN = Pattern.compile("[?&]page=(\\d+)");

    /**
     * Normalizes a URL by adding https: protocol to protocol-relative URLs.
     */
    private static String normalizeUrl(String url) {
        if (url == null || url.isBlank()) return url;
        if (url.startsWith("//")) return "https:" + url;
        return url;
    }

    /**
     * Extracts and normalizes image URL from element, handling data-src for lazy-loaded images.
     */
    private static String extractImageUrl(Element img) {
        // Try data-src first (for lazy-loaded images), using absUrl to resolve protocol-relative URLs
        String url = img.absUrl("data-src");
        if (isPlaceholderSrc(url)) url = "";
        if (url.isBlank()) url = img.attr("data-src");
        if (isPlaceholderSrc(url)) url = "";
        if (url.isBlank()) {
            url = img.absUrl("src");
        }
        if (isPlaceholderSrc(url)) url = "";
        if (url.isBlank()) {
            url = img.attr("src");
        }
        if (isPlaceholderSrc(url)) url = "";
        return normalizeUrl(url);
    }

    // ──────────────────────────────────────────────
    // Search Results
    // ──────────────────────────────────────────────

    /**
     * Parses mcmod.cn search results page HTML into a list of SearchHit.
     *
     * @param html Raw HTML string of the search results page
     * @return Parsed SearchHit list, empty list if no matches
     */
    public List<SearchHit> parseSearchResults(String html) {
        if (html == null || html.isBlank()) {
            return Collections.emptyList();
        }

        org.jsoup.nodes.Document doc = Jsoup.parse(html);
        Elements resultItems = doc.select(".search-result-list .result-item");
        if (resultItems.isEmpty()) {
            OmnisearchMod.LOGGER.debug("[McmodParser] no .result-item nodes found in search html");
            return Collections.emptyList();
        }

        List<SearchHit> results = new ArrayList<>();
        int missingHeadCount = 0;
        int missingLinkCount = 0;
        int unmatchedHrefCount = 0;
        List<String> unmatchedHrefSamples = new ArrayList<>();
        for (Element resultItem : resultItems) {
            Element headDiv = resultItem.selectFirst(".head");
            // Pick the primary title link. mcmod.cn renders a category icon link inside
            // .head > .class-category before the real result link; that icon has empty text
            // and an href containing "/category/" and must be skipped. Prefer the first
            // non-empty, non-category link inside .head; fall back to the whole result block.
            Element link = findPrimaryLink(
                headDiv != null ? headDiv.select("a[href]") : resultItem.select("a[href]"));
            if (link == null) {
                link = findPrimaryLink(resultItem.select("a[href]"));
            }
            if (link == null) {
                missingLinkCount++;
                continue;
            }

            String href = link.attr("href");
            String[] ref = extractItemRef(href);
            if (ref == null) {
                unmatchedHrefCount++;
                if (unmatchedHrefSamples.size() < 3) {
                    unmatchedHrefSamples.add(href);
                }
                continue;
            }

            String type = ref[0];   // "item" or "class"
            String id = type + "/" + ref[1];

            // Category tag is a text node in the parent .head div, before the <a> tag.
            // e.g. "(自然生成) <a>巫妖塔 - 暮色森林</a>"
            String category = null;
            if (headDiv == null) {
                missingHeadCount++;
            }
            String headText = (headDiv != null ? headDiv.text() : link.text()).trim();
            Matcher catMatcher = CATEGORY_PATTERN.matcher(headText);
            if (catMatcher.matches()) {
                category = catMatcher.group(1).trim();
            }

            String name = link.text().trim();
            String sourceMod = null;

            String parseText = headText;
            if (category != null && !category.isBlank()) {
                parseText = CATEGORY_PATTERN.matcher(headText).replaceFirst("$2").trim();
            }

            Matcher textMatcher = LINK_TEXT_PATTERN.matcher(parseText);
            if (textMatcher.matches()) {
                name = textMatcher.group(1).trim();
                sourceMod = textMatcher.group(2).trim();
            }

            // Strip English names in parentheses for cleaner display
            name = PAREN_ENGLISH.matcher(name).replaceAll("").trim();
            if (sourceMod != null) {
                sourceMod = PAREN_ENGLISH.matcher(sourceMod).replaceAll("").trim();
            }

            results.add(new SearchHit(id, name, type, sourceMod, category));
        }

        if (unmatchedHrefCount > 0) {
            OmnisearchMod.LOGGER.warn(
                "[McmodParser] could not extract id from {} of {} result-item links (samples={}) — mcmod.cn link format may have changed",
                unmatchedHrefCount,
                resultItems.size(),
                unmatchedHrefSamples
            );
        } else {
            OmnisearchMod.LOGGER.debug(
                "[McmodParser] parsed {} search hits from {} result items (missingHead={}, missingLink={})",
                results.size(),
                resultItems.size(),
                missingHeadCount,
                missingLinkCount
            );
        }

        return results;
    }

    /**
     * Selects the primary result link from a candidate set, skipping the category-icon link
     * that mcmod.cn renders before the real result title link.
     * <p>
     * The category icon is an {@code <a>} with empty visible text whose href contains
     * {@code "/category/"} (e.g. {@code /class/category/3-1.html}). Selecting it would yield
     * an empty name and a bogus id. We therefore prefer links whose href points at a real
     * {@code /item/} or {@code /class/} target (not a category listing) with non-blank text,
     * then fall back to the first non-category item/class link regardless of text.
     *
     * @return the primary link, or null if none qualifies
     */
    static Element findPrimaryLink(Elements links) {
        if (links == null || links.isEmpty()) {
            return null;
        }
        Element fallback = null;
        for (Element a : links) {
            String href = a.attr("href");
            if (href == null || href.isBlank()) continue;
            String lower = href.toLowerCase();
            boolean isItemOrClass = lower.contains("/item/") || lower.contains("/class/");
            boolean isCategory = lower.contains("/category/");
            if (!isItemOrClass || isCategory) continue;
            if (!a.text().isBlank()) {
                return a;
            }
            if (fallback == null) {
                fallback = a;
            }
        }
        return fallback;
    }

    /**
     * <p>
     * The link selector already guarantees the href contains "/item/" or "/class/", so we
     * locate that marker and read the target token up to the first separator
     * ({@code / ? # &}, whitespace, or quote), stripping a trailing ".html". This is robust
     * against any suffix mcmod.cn may append (e.g. tracking params joined with {@code &}),
     * which a strict terminator-anchored regex would silently reject.
     *
     * @return a 2-element String array {type, id}, or null if the marker is absent / token empty
     */
    static String[] extractItemRef(String href) {
        if (href == null || href.isBlank()) {
            return null;
        }
        String lower = href.toLowerCase();
        int marker = -1;
        String type = null;
        int itemIdx = lower.indexOf("/item/");
        int classIdx = lower.indexOf("/class/");
        if (itemIdx >= 0 && (classIdx < 0 || itemIdx < classIdx)) {
            marker = itemIdx + "/item/".length();
            type = "item";
        } else if (classIdx >= 0) {
            marker = classIdx + "/class/".length();
            type = "class";
        } else {
            return null;
        }
        int end = marker;
        while (end < href.length()) {
            char c = href.charAt(end);
            if (c == '/' || c == '?' || c == '#' || c == '&' || c == ';'
                    || c == ' ' || c == '"' || c == '\'' || c == '<' || c == '>') {
                break;
            }
            end++;
        }
        String id = href.substring(marker, end);
        if (id.toLowerCase().endsWith(".html")) {
            id = id.substring(0, id.length() - ".html".length());
        }
        if (id.isBlank()) {
            return null;
        }
        return new String[]{type, id};
    }

    public SearchPageBatch parseSearchPage(String html, String pageUrl) {
        if (html == null || html.isBlank()) {
            return new SearchPageBatch(List.of(), null);
        }
        org.jsoup.nodes.Document doc = Jsoup.parse(html, pageUrl);
        return new SearchPageBatch(parseSearchResults(html), extractNextSearchPageUrl(doc, pageUrl));
    }

    // ──────────────────────────────────────────────
    // Item Page
    // ──────────────────────────────────────────────

    /**
     * Parses mcmod.cn item detail page HTML into a Document.
     *
     * @param html Raw HTML string of the detail page
     * @param url  Page URL (filled into Document.sourceUrl)
     * @return Parsed Document
     */
    public Document parseItemPage(String html, String url) {
        if (html == null || html.isBlank()) {
            return new Document("", null, url, List.of());
        }

        // Extract numeric item ID from URL
        String itemId = "";
        java.util.regex.Matcher idMatcher = java.util.regex.Pattern.compile("/item/(\\d+)").matcher(url);
        if (idMatcher.find()) itemId = idMatcher.group(1);

        org.jsoup.nodes.Document doc = Jsoup.parse(html, url);

        // Title
        String title = "";
        Element titleEl = doc.selectFirst("div.itemname h5");
        if (titleEl != null) {
            title = titleEl.text().trim();
        }

        // Source mod + URL (store as "name|url" for DetailPanelWidget)
        String sourceMod = null;
        Elements modLinks = doc.select(".common-nav a.item[href*='/class/']");
        if (!modLinks.isEmpty()) {
            Element lastMod = modLinks.last();
            String name = lastMod.text().trim();
            String modUrl = resolveModUrl(lastMod, url);
            OmnisearchMod.LOGGER.info("[McmodParser] Item page modLinks count={} lastText='{}' lastHref='{}' absUrl='{}' resolved='{}'",
                modLinks.size(), name, lastMod.attr("href"), lastMod.absUrl("href"), modUrl);
            sourceMod = modUrl != null && !modUrl.isBlank()
                ? name + "|" + modUrl
                : name;
        } else {
            OmnisearchMod.LOGGER.info("[McmodParser] Item page NO modLinks found. url={} urlLen={}", url, url.length());
        }

        // Content
        Element contentEl = doc.selectFirst(".item-content.common-text.font14");
        List<DocNode> content = new ArrayList<>();

        // ── Step 1: Extract item icon ──
        String iconUrl = null;
        String iconAlt = "";
        Elements iconImgCandidates = doc.select(
            ".item-icon img, .itemicon img, .item-img img, .itempic img, " +
            "div.itemname img, .main-icon img, .icont img, " +
            "a[href*='/item/" + itemId + "'] img"
        );
        for (Element img : iconImgCandidates) {
            String src = extractImageUrl(img);
            if (!src.isBlank() && !src.contains("avatar") && !src.contains("data:")) {
                iconUrl = src;
                iconAlt = img.attr("alt");
                break;
            }
        }
        if (iconUrl == null) {
            Elements allImgs = doc.select("img");
            for (Element img : allImgs) {
                String src = extractImageUrl(img);
                if (src.contains("/item/icon/") || src.contains("/item_icon/")) {
                    iconUrl = src;
                    iconAlt = img.attr("alt");
                    break;
                }
            }
        }

        // ── Step 2: Add item icon ──
        if (iconUrl != null) {
            content.add(new ImageNode(iconUrl, iconAlt, null));
        }

        // ── Step 3: Parse text content ──
        if (contentEl != null) {
            // Remove unwanted elements
            contentEl.select(".common-text-menu").remove();
            contentEl.select(".uknowtoomuch").remove();

            for (Node child : contentEl.childNodesCopy()) {
                List<DocNode> parsed = parseBlockNode(child);
                content.addAll(parsed);
            }
        }

        if (content.isEmpty()) {
            content.add(new TextNode("(empty content)"));
        }

        return new Document(title, sourceMod, url, content);
    }

    // ──────────────────────────────────────────────
    // Mod Page
    // ──────────────────────────────────────────────

    /**
     * Parses mcmod.cn mod detail page HTML into a Document.
     *
     * @param html Raw HTML string of the detail page
     * @param url  Page URL
     * @return Parsed Document
     */
    public Document parseModPage(String html, String url) {
        if (html == null || html.isBlank()) {
            return new Document("", null, url, List.of());
        }

        org.jsoup.nodes.Document doc = Jsoup.parse(html, url);

        // Title — try multiple possible selectors
        String title = "";
        Element titleEl = doc.selectFirst("div.modname h2, div.modname h5, div.itemname h5, h1.mod-title");
        if (titleEl != null) {
            title = titleEl.text().trim();
        }

        // Source mod (mod page refers to itself, or may have common-nav)
        String sourceMod = null;
        Elements modLinks = doc.select(".common-nav a.item[href*='/class/']");
        if (!modLinks.isEmpty()) {
            Element lastMod = modLinks.last();
            String name = lastMod.text().trim();
            String modUrl = resolveModUrl(lastMod, url);
            sourceMod = modUrl != null && !modUrl.isBlank()
                ? name + "|" + modUrl
                : name;
        }

        // Content
        Element contentEl = doc.selectFirst(".item-content.common-text.font14, .mod-content.common-text.font14");
        List<DocNode> content = new ArrayList<>();

        if (titleEl != null && !title.isBlank()) {
            TextNode titleText = new TextNode(title);
            content.add(new HeadingNode(1, java.util.List.of(titleText)));
        }

        if (contentEl != null) {
            contentEl.select(".common-text-menu").remove();
            contentEl.select(".uknowtoomuch").remove();

            for (Node child : contentEl.childNodesCopy()) {
                List<DocNode> parsed = parseBlockNode(child);
                content.addAll(parsed);
            }
        }

        if (content.isEmpty()) {
            content.add(new TextNode("(empty content)"));
        }

        return new Document(title, sourceMod, url, content);
    }

    // ══════════════════════════════════════════════
    // Internal: Block-level node parsing
    // ══════════════════════════════════════════════

    /**
     * Parses a single block-level node (or text node) into zero or more DocNode.
     */
    private List<DocNode> parseBlockNode(Node node) {
        if (node instanceof org.jsoup.nodes.TextNode tn) {
            String text = tn.text().trim();
            if (text.isBlank()) {
                return Collections.emptyList();
            }
            return java.util.List.of(new TextNode(text));
        }

        if (!(node instanceof Element el)) {
            return Collections.emptyList();
        }

        return switch (el.tagName()) {
            case "h1" -> java.util.List.of(new HeadingNode(1, parseInlineChildren(el)));
            case "h2" -> java.util.List.of(new HeadingNode(2, parseInlineChildren(el)));
            case "h3" -> java.util.List.of(new HeadingNode(3, parseInlineChildren(el)));
            case "h4" -> java.util.List.of(new HeadingNode(4, parseInlineChildren(el)));
            case "h5" -> java.util.List.of(new HeadingNode(5, parseInlineChildren(el)));
            case "h6" -> java.util.List.of(new HeadingNode(6, parseInlineChildren(el)));
            case "p" -> {
                List<DocNode> children = parseInlineChildren(el);
                if (children.isEmpty()) {
                    yield Collections.emptyList();
                }
                yield java.util.List.of(new ParagraphNode(children));
            }
            case "table" -> {
                // Check if this table contains only images (gallery table)
                Elements imgs = el.select("img");
                Elements textCells = el.select("td, th");
                boolean isGallery = !imgs.isEmpty() && textCells.stream().allMatch(cell -> {
                    String text = cell.text().trim();
                    return text.isEmpty() || cell.select("img").size() > 0;
                });
                
                if (isGallery) {
                    // Extract images as block-level ImageNodes
                    List<DocNode> imgNodes = new ArrayList<>();
                    for (Element img : imgs) {
                        String src = extractImageUrl(img);
                        String alt = img.attr("alt");
                        if (!src.isBlank()) {
                            int[] imgDims = parseImgDims(img);
                            imgNodes.add(new ImageNode(src, alt, null, imgDims[0], imgDims[1]));
                        }
                    }
                    yield imgNodes;
                } else {
                    // Regular table
                    TableNode table = parseTable(el);
                    yield table != null ? java.util.List.of(table) : Collections.emptyList();
                }
            }
            case "ul" -> java.util.List.of(new ListNode(false, parseListItems(el)));
            case "ol" -> java.util.List.of(new ListNode(true, parseListItems(el)));
            case "hr" -> java.util.List.of(new DividerNode());
            case "img" -> {
                String src = extractImageUrl(el);
                String alt = el.attr("alt");
                if (src.isBlank()) yield Collections.emptyList();
                int[] imgDims = parseImgDims(el);
                yield java.util.List.of(new ImageNode(src, alt, null, imgDims[0], imgDims[1]));
            }
            case "div" -> {
                List<DocNode> sectionResult = tryParseSection(el);
                yield sectionResult;
            }
            case "br" -> List.of(new TextNode("\n"));
            case "svg" -> parseInlineSvg(el);
            case "use" -> {
                String href = el.attr("xlink:href");
                if (href.isEmpty()) href = el.attr("href");
                if (!href.startsWith("#")) yield Collections.emptyList();
                String iconName = href.substring(1);
                yield java.util.List.of(new ImageInlineNode("mc-icon://" + iconName, iconName));
            }
            default -> {
                // Check for CSS background-image (e.g. icon sprites, mc icons)
                String bgSrc = extractBackgroundImage(el);
                if (bgSrc != null) {
                    String alt = el.attr("alt");
                    if (alt.isBlank()) alt = el.text();
                    yield java.util.List.of(new ImageInlineNode(bgSrc, alt));
                }
                // Check for nested img tags (e.g. <a><img></a>, <figure><img></figure>, <span><img></span>)
                Elements nestedImgs = el.select("img");
                if (!nestedImgs.isEmpty()) {
                    List<DocNode> imgNodes = new ArrayList<>();
                    for (Element img : nestedImgs) {
                        String src = extractImageUrl(img);
                        String alt = img.attr("alt");
                        if (!src.isBlank()) {
                            int[] imgDims = parseImgDims(img);
                            imgNodes.add(new ImageNode(src, alt, null, imgDims[0], imgDims[1]));
                        }
                    }
                    if (!imgNodes.isEmpty()) yield imgNodes;
                }
                // Check for nested svg with <use> tags
                Elements nestedSvgs = el.select("svg");
                if (!nestedSvgs.isEmpty()) {
                    List<DocNode> svgNodes = new ArrayList<>();
                    for (Element svg : nestedSvgs) {
                        svgNodes.addAll(parseInlineSvg(svg));
                    }
                    if (!svgNodes.isEmpty()) yield svgNodes;
                }
                // Fallback: render element text (NOT children — block elements
                // are handled by parseBlockNode)
                String text = el.text().trim();
                if (text.isBlank()) yield Collections.emptyList();
                yield java.util.List.of(new TextNode(text));
            }
        };
    }

    // ══════════════════════════════════════════════
    // Internal: Inline node parsing
    // ══════════════════════════════════════════════

    /**
     * Parses children of an element as inline content (text, styled text, links, images).
     * Jsoup TextNode → TextNode; Element children → recursive inline mapping.
     */
    private List<DocNode> parseInlineChildren(Element parent) {
        List<DocNode> result = new ArrayList<>();
        for (Node child : parent.childNodesCopy()) {
            List<DocNode> parsed = parseInlineNode(child);
            result.addAll(parsed);
        }
        return result;
    }

    /**
     * Parses a single inline-level node.
     */
    private List<DocNode> parseInlineNode(Node node) {
        if (node instanceof org.jsoup.nodes.TextNode tn) {
            String text = tn.text();
            if (text.isBlank()) {
                return Collections.emptyList();
            }
            return java.util.List.of(new TextNode(text));
        }

        if (!(node instanceof Element el)) {
            return Collections.emptyList();
        }

        return switch (el.tagName()) {
            case "b", "strong" -> {
                List<DocNode> children = parseInlineChildren(el);
                if (children.isEmpty()) yield Collections.emptyList();
                yield applyStyle(children, TextStyle.BOLD);
            }
            case "i", "em" -> {
                List<DocNode> children = parseInlineChildren(el);
                if (children.isEmpty()) yield Collections.emptyList();
                yield applyStyle(children, TextStyle.ITALIC);
            }
            case "span" -> {
                String style = el.attr("style");
                String color = extractColor(style);
                List<DocNode> children = parseInlineChildren(el);
                if (children.isEmpty()) {
                    yield Collections.emptyList();
                }
                if (color == null) {
                    yield children; // no color style, pass children through as-is
                }
                TextStyle colorStyle = new TextStyle(false, false, false, false, color);
                yield applyStyle(children, colorStyle);
            }
            case "a" -> {
                String href = el.absUrl("href");
                if (href.isEmpty()) href = el.attr("href");
                List<DocNode> linkChildren = parseInlineChildren(el);
                if (linkChildren.isEmpty()) {
                    String linkText = el.text().trim();
                    if (linkText.isBlank()) yield Collections.emptyList();
                    linkChildren = java.util.List.of(new TextNode(linkText));
                }
                yield java.util.List.of(new LinkNode(href, linkChildren));
            }
            case "img" -> {
                String src = extractImageUrl(el);
                String alt = el.attr("alt");
                if (src.isBlank()) yield Collections.emptyList();
                // Check if this is a content image (has width/height attrs) -> block-level ImageNode
                if (isContentImage(el)) {
                    int[] imgDims = parseImgDims(el);
                    yield java.util.List.of(new ImageNode(src, alt, null, imgDims[0], imgDims[1]));
                }
                yield java.util.List.of(new ImageInlineNode(src, alt));
            }
            case "br" -> List.of(new TextNode("\n"));
            case "svg" -> parseInlineSvg(el);
            case "use" -> {
                String href = el.attr("xlink:href");
                if (href.isEmpty()) href = el.attr("href");
                if (!href.startsWith("#")) yield Collections.emptyList();
                String iconName = href.substring(1);
                yield java.util.List.of(new ImageInlineNode("mc-icon://" + iconName, iconName));
            }
            default -> {
                List<DocNode> children = parseInlineChildren(el);
                if (children.isEmpty()) {
                    String text = el.text().trim();
                    if (text.isBlank()) yield Collections.emptyList();
                    yield java.util.List.of(new TextNode(text));
                }
                yield children;
            }
        };
    }

    // ══════════════════════════════════════════════
    // Internal: Specialized parsers
    // ══════════════════════════════════════════════

    /**
     * Parses a <table> element into TableNode.
     * First row becomes headers, remaining rows become data rows.
     */
    private TableNode parseTable(Element tableEl) {
        Elements rows = tableEl.select("tr");
        if (rows.isEmpty()) {
            return null;
        }

        // First row as headers only if it has <th> elements OR its cells have text content
        // (mcmod.cn attribute tables use <td> for headers)
        // But if cells only contain images (no text), treat as data row
        Element headerRow = rows.first();
        boolean hasTh = !headerRow.select("th").isEmpty();
        boolean hasHeaderText = !headerRow.text().trim().isEmpty();
        List<String> headers = new ArrayList<>();
        int startRow = 0;
        if (hasTh || hasHeaderText) {
            startRow = 1;
            for (Element th : headerRow.select("th, td")) {
                headers.add(th.text().trim());
            }
        }

        // Data rows
        List<List<DocNode>> dataRows = new ArrayList<>();
        for (int i = startRow; i < rows.size(); i++) {
            List<DocNode> rowCells = new ArrayList<>();
            Elements cells = rows.get(i).select("> th, > td");
            if (cells.isEmpty()) {
                // Row has no th/td directly - try any th/td (nested tables)
                cells = rows.get(i).select("th, td");
            }
            for (Element cell : cells) {
                List<DocNode> cellContent = parseInlineChildren(cell);
                if (cellContent.isEmpty()) {
                    cellContent = java.util.List.of(new TextNode(cell.text().trim()));
                }
                // Wrap multiple inline nodes in a ParagraphNode for cell structure
                rowCells.add(new ParagraphNode(cellContent));
            }
            if (!rowCells.isEmpty()) {
                dataRows.add(rowCells);
            }
        }

        return new TableNode(headers, dataRows);
    }

    /**
     * Parses list item elements (<li>) inside <ul> or <ol>.
     */
    private List<DocNode> parseListItems(Element listEl) {
        List<DocNode> items = new ArrayList<>();
        for (Element li : listEl.select("> li")) {
            List<DocNode> children = parseInlineChildren(li);
            if (children.isEmpty()) {
                children = java.util.List.of(new TextNode(li.text().trim()));
            }
            if (!children.isEmpty()) {
                items.add(new ParagraphNode(children));
            }
        }
        return items;
    }

    /**
     * Attempts to parse a <div> as a SectionNode.
     * If the div contains a heading element, uses its text as title.
     * Otherwise, flattens children as block content.
     */
    private List<DocNode> tryParseSection(Element divEl) {
        // Try to find a heading title
        Element headingEl = divEl.selectFirst("h1, h2, h3, h4, h5, h6");
        String sectionTitle = "";
        if (headingEl != null) {
            sectionTitle = headingEl.text().trim();
        }

        // Check for a known section class
        String classAttr = divEl.className();
        if (!classAttr.isBlank() && headingEl == null) {
            sectionTitle = classAttr.replaceAll("[-_]", " ");
        }

        // Check for CSS background-image on the div itself (e.g. icon sprite divs)
        List<DocNode> children = new ArrayList<>();
        String bgSrc = extractBackgroundImage(divEl);
        if (bgSrc != null) {
            String alt = divEl.attr("alt");
            if (alt.isBlank()) alt = divEl.text();
            children.add(new ImageInlineNode(bgSrc, alt));
        }

        // Process children as block content
        for (Node child : divEl.childNodesCopy()) {
            // childNodesCopy() creates new Node instances, so we compare by tag + text,
            // not by reference equality, to skip the heading already used as section title.
            if (child instanceof Element childEl && headingEl != null
                && childEl.tagName().equals(headingEl.tagName())
                && childEl.text().trim().equals(headingEl.text().trim())) {
                continue;
            }
            children.addAll(parseBlockNode(child));
        }

        if (!sectionTitle.isBlank() && !children.isEmpty()) {
            return java.util.List.of(new SectionNode(sectionTitle, children));
        }

        // Fall back: just return the parsed children
        return children;
    }

    // ══════════════════════════════════════════════
    // Internal: Helpers
    // ══════════════════════════════════════════════

    /**
     * Applies a TextStyle to a list of DocNode children.
     * TextNode → StyledTextNode with the style.
     * StyledTextNode → merge styles (e.g. bold + italic = bold+italic).
     * Other nodes (LinkNode, ImageInlineNode) → passed through as-is.
     */
    private List<DocNode> applyStyle(List<DocNode> nodes, TextStyle style) {
        List<DocNode> result = new ArrayList<>();
        for (DocNode node : nodes) {
            if (node instanceof TextNode tn) {
                result.add(new StyledTextNode(tn.getText(), style));
            } else if (node instanceof StyledTextNode stn) {
                result.add(new StyledTextNode(stn.getText(), mergeStyles(style, stn.getStyle())));
            } else {
                result.add(node);
            }
        }
        return result;
    }

    /**
     * Merges two TextStyles. A property is "on" if either has it on.
     * Inner color wins over outer color (inner = more specific).
     */
    private TextStyle mergeStyles(TextStyle outer, TextStyle inner) {
        return new TextStyle(
            outer.bold() || inner.bold(),
            outer.italic() || inner.italic(),
            outer.underline() || inner.underline(),
            outer.strikethrough() || inner.strikethrough(),
            inner.color() != null ? inner.color() : outer.color()
        );
    }

    /**
     * Extracts a hex color from a CSS style attribute value.
     * E.g. "color:#FFAA00" → "#FFAA00", "color: red" → null for named colors.
     */
    private String extractColor(String style) {
        if (style == null || style.isBlank()) {
            return null;
        }
        Matcher m = COLOR_PATTERN.matcher(style);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * Parses img dimensions from width/height or data-width/data-height attributes.
     * Returns [width, height], or [0, 0] if unknown.
     */
    private static int[] parseImgDims(Element el) {
        int w = parseDimAttr(el, "width", "data-width");
        int h = parseDimAttr(el, "height", "data-height");
        return new int[]{w, h};
    }

    private static int parseDimAttr(Element el, String attr, String dataAttr) {
        String v = el.attr(attr);
        if (v.isEmpty()) v = el.attr(dataAttr);
        if (v.isEmpty()) return 0;
        try {
            return Integer.parseInt(v.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Checks if a src URL is a placeholder/loading image that should fall back to data-src.
     */
    private static boolean isPlaceholderSrc(String src) {
        if (src == null || src.isEmpty()) return true;
        return src.endsWith(".svg")
            || src.contains("placeholder")
            || src.contains("loading")
            || src.contains("loadfail")
            || src.contains("loading-colourful");
    }

    /**
     * Checks if an img element is a content image (not an icon) based on width/height attributes.
     * Content images get block-level layout; icons get inline layout.
     */
    private static boolean isContentImage(Element el) {
        String w = el.attr("width");
        String h = el.attr("height");
        if (!w.isEmpty()) {
            try {
                int wi = Integer.parseInt(w.replaceAll("[^0-9]", ""));
                if (wi >= 50) return true;
            } catch (NumberFormatException ignored) {}
        }
        if (!h.isEmpty()) {
            try {
                int hi = Integer.parseInt(h.replaceAll("[^0-9]", ""));
                if (hi >= 50) return true;
            } catch (NumberFormatException ignored) {}
        }
        // Check data-width/data-height (mcmod.cn lazy-load attrs)
        String dw = el.attr("data-width");
        if (!dw.isEmpty()) {
            try {
                int dwi = Integer.parseInt(dw.replaceAll("[^0-9]", ""));
                if (dwi >= 50) return true;
            } catch (NumberFormatException ignored) {}
        }
        // If img has data-src (lazy-loaded), it's a content image, not an icon
        if (!el.attr("data-src").isEmpty()) return true;
        return false;
    }

    /**
     * Extracts a URL from CSS background-image style, if present.
     */
    private static String extractBackgroundImage(Element el) {
        String style = el.attr("style");
        if (style == null || style.isEmpty()) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "background(?:-image)?\\s*:\\s*url\\(['\"]?([^)'\"]+)"
        ).matcher(style);
        if (m.find()) {
            String url = m.group(1);
            if (url.startsWith("//")) url = "https:" + url;
            if (url.startsWith("http")) return url;
        }
        return null;
    }

    /**
     * Resolves a mod link's URL, falling back to manual construction
     * when absUrl fails (e.g. when the base URI is not available).
     */
    private static String resolveModUrl(Element linkEl, String pageUrl) {
        String absUrl = linkEl.absUrl("href");
        if (absUrl != null && !absUrl.isBlank()) return absUrl;
        // Fallback: construct from raw href + page URL base
        String href = linkEl.attr("href");
        if (href == null || href.isBlank()) return null;
        if (href.startsWith("http")) return href;
        if (href.startsWith("//")) return "https:" + href;
        if (href.startsWith("/") && pageUrl != null && !pageUrl.isBlank()) {
            int slashIdx = pageUrl.indexOf("/", pageUrl.indexOf("://") + 3);
            if (slashIdx > 0) {
                return pageUrl.substring(0, slashIdx) + href;
            }
        }
        return href;
    }

    /**
     * Parses an SVG element — finds nested {@code <use>} and extracts icon name.
     */
    private static java.util.List<DocNode> parseInlineSvg(Element svgEl) {
        Element useEl = svgEl.selectFirst("use");
        if (useEl != null) {
            String href = useEl.attr("xlink:href");
            if (href.isEmpty()) href = useEl.attr("href");
            if (href.startsWith("#")) {
                String iconName = href.substring(1);
                return java.util.List.of(new ImageInlineNode("mc-icon://" + iconName, iconName));
            }
        }
        return java.util.Collections.emptyList();
    }

    /**
     * Extracts the next search page URL.
     * <p>
     * Rather than using the raw pagination href (which mcmod.cn emits as a query-only
     * relative URL with unencoded Chinese, e.g. {@code ?key=巫妖&filter=3&page=2} - a URL
     * that the server rejects when requested verbatim), this extracts the target page
     * NUMBER from the pagination DOM and rebuilds the URL from the current (properly
     * encoded) pageUrl by swapping in the page parameter.
     */
    private static String extractNextSearchPageUrl(org.jsoup.nodes.Document doc, String pageUrl) {
        int currentPage = extractPageNumber(pageUrl);
        int nextPage = -1;

        // Preferred: mcmod.cn renders <a class="page-link" data-page="N"> in .pagination
        for (Element a : doc.select("a.page-link[data-page]")) {
            int p = parsePageToken(a.attr("data-page"));
            if (p > currentPage && (nextPage == -1 || p < nextPage)) {
                nextPage = p;
            }
        }

        // Fallback 1: classic bootstrap-style .pagination with active page-item
        if (nextPage == -1) {
            Element activeNext = doc.selectFirst(".pagination .page-item.active + .page-item > a[href]");
            if (activeNext != null) {
                int p = parsePageToken(activeNext.attr("data-page"));
                if (p == -1) p = parsePageParam(activeNext.absUrl("href"));
                if (p == -1) p = parsePageParam(activeNext.attr("href"));
                if (p > currentPage) {
                    nextPage = p;
                }
            }
        }

        // Fallback 2: scan any search links for the smallest page number beyond current
        if (nextPage == -1) {
            for (Element link : doc.select("a[href]")) {
                String href = link.absUrl("href");
                if (href == null || href.isBlank()) {
                    href = normalizeUrl(link.attr("href"));
                }
                if (href == null || href.isBlank()) {
                    continue;
                }
                if (!href.contains("/s?") && !href.contains("?key=")) {
                    continue;
                }
                int p = parsePageParam(href);
                if (p > currentPage && (nextPage == -1 || p < nextPage)) {
                    nextPage = p;
                }
            }
        }

        return nextPage == -1 ? null : withPageParam(pageUrl, nextPage);
    }

    /**
     * Rebuilds a page URL with the given page number, replacing an existing page=
     * parameter or appending one. The base pageUrl is already properly encoded.
     */
    private static String withPageParam(String url, int page) {
        // PAGE_PARAM_PATTERN matches "[?&]page=(\d+)" - keep the separator, swap the digits
        Matcher m = PAGE_PARAM_PATTERN.matcher(url);
        if (m.find()) {
            String sepAndKey = m.group(0).replaceAll("\\d+$", "");
            return new StringBuilder(url).replace(m.start(), m.end(), sepAndKey + page).toString();
        }
        return url + "&page=" + page;
    }

    /** Parses a numeric page token like "2"; returns -1 if not a positive integer. */
    private static int parsePageToken(String token) {
        if (token == null || token.isBlank()) {
            return -1;
        }
        try {
            int p = Integer.parseInt(token.trim());
            return p > 0 ? p : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    /** Extracts the page= query parameter value; returns -1 if absent (unlike extractPageNumber). */
    private static int parsePageParam(String url) {
        if (url == null || url.isBlank()) {
            return -1;
        }
        Matcher matcher = PAGE_PARAM_PATTERN.matcher(url);
        if (!matcher.find()) {
            return -1;
        }
        return parsePageToken(matcher.group(1));
    }

    private static int extractPageNumber(String url) {
        if (url == null || url.isBlank()) {
            return 1;
        }
        Matcher matcher = PAGE_PARAM_PATTERN.matcher(url);
        if (!matcher.find()) {
            return 1;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }
}
