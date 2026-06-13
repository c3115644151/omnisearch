package com.cy311.omnisearch.data.parser;

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

    private static final Pattern HREF_ITEM_PATTERN = Pattern.compile("/(item|class)/(\\d+)");
    private static final Pattern LINK_TEXT_PATTERN = Pattern.compile("^(.*?)\\s*-\\s*(.*)$");
    private static final Pattern PAREN_ENGLISH = Pattern.compile("\\s*\\([^)]*\\)");
    private static final Pattern COLOR_PATTERN = Pattern.compile("color\\s*:\\s*(#[0-9a-fA-F]{3,8})\\b");

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
        Elements links = doc.select(
            ".search-result-list .result-item .head a[href*='/item/'], " +
            ".search-result-list .result-item .head a[href*='/class/']"
        );

        if (links.isEmpty()) {
            return Collections.emptyList();
        }

        List<SearchHit> results = new ArrayList<>();
        for (Element link : links) {
            String href = link.attr("href");
            Matcher m = HREF_ITEM_PATTERN.matcher(href);
            if (!m.find()) {
                continue;
            }

            String type = m.group(1);   // "item" or "class"
            String id = type + "/" + m.group(2);

            String linkText = link.text().trim();
            String name = linkText;
            String sourceMod = null;

            Matcher textMatcher = LINK_TEXT_PATTERN.matcher(linkText);
            if (textMatcher.matches()) {
                name = textMatcher.group(1).trim();
                sourceMod = textMatcher.group(2).trim();
            }

            // Strip English names in parentheses for cleaner display
            name = PAREN_ENGLISH.matcher(name).replaceAll("").trim();
            if (sourceMod != null) {
                sourceMod = PAREN_ENGLISH.matcher(sourceMod).replaceAll("").trim();
            }

            results.add(new SearchHit(id, name, type, sourceMod));
        }

        return results;
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

        org.jsoup.nodes.Document doc = Jsoup.parse(html);

        // Title
        String title = "";
        Element titleEl = doc.selectFirst("div.itemname h5");
        if (titleEl != null) {
            title = titleEl.text().trim();
        }

        // Source mod
        String sourceMod = null;
        Elements modLinks = doc.select(".common-nav a.item[href*='/class/']");
        if (!modLinks.isEmpty()) {
            sourceMod = modLinks.last().text().trim();
        }

        // Content
        Element contentEl = doc.selectFirst(".item-content.common-text.font14");
        List<DocNode> content = new ArrayList<>();

        if (titleEl != null && !title.isBlank()) {
            TextNode titleText = new TextNode(title);
            content.add(new HeadingNode(1, java.util.List.of(titleText)));
        }

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

        org.jsoup.nodes.Document doc = Jsoup.parse(html);

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
            sourceMod = modLinks.last().text().trim();
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
                TableNode table = parseTable(el);
                yield table != null ? java.util.List.of(table) : Collections.emptyList();
            }
            case "ul" -> java.util.List.of(new ListNode(false, parseListItems(el)));
            case "ol" -> java.util.List.of(new ListNode(true, parseListItems(el)));
            case "hr" -> java.util.List.of(new DividerNode());
            case "img" -> {
                String src = el.absUrl("src");
                if (src.isEmpty()) src = el.attr("src");
                String alt = el.attr("alt");
                yield java.util.List.of(new ImageNode(src, alt, null));
            }
            case "div" -> {
                List<DocNode> sectionResult = tryParseSection(el);
                yield sectionResult;
            }
            case "br" -> Collections.emptyList();
            default -> {
                String text = el.text().trim();
                if (text.isBlank()) {
                    yield Collections.emptyList();
                }
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
                String src = el.absUrl("src");
                if (src.isEmpty()) src = el.attr("src");
                String alt = el.attr("alt");
                if (src.isBlank()) yield Collections.emptyList();
                yield java.util.List.of(new ImageInlineNode(src, alt));
            }
            case "br" -> Collections.emptyList();
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

        // First row as headers
        Element headerRow = rows.first();
        List<String> headers = new ArrayList<>();
        for (Element th : headerRow.select("th, td")) {
            headers.add(th.text().trim());
        }

        // Remaining rows as data
        List<List<DocNode>> dataRows = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<DocNode> rowCells = new ArrayList<>();
            for (Element cell : rows.get(i).select("th, td")) {
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

        // Process children as block content
        List<DocNode> children = new ArrayList<>();
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
}
