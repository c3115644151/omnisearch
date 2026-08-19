package com.cy311.omnisearch.client.render.layout;

import com.cy311.omnisearch.data.model.document.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link LayoutEngine}, focusing on image handling in tables.
 * <p>
 * LayoutEngine is pure Java (no MC dependency), so we can construct a
 * {@link FontMetrics} directly with a simple lambda width measurer.
 */
class LayoutEngineTest {

    private static final int WIDTH = 200;
    private static final int LINE_HEIGHT = 9;
    private static final int PX_PER_CHAR = 6;

    private final FontMetrics metrics = new FontMetrics(LINE_HEIGHT, text ->
        text != null ? text.length() * PX_PER_CHAR : 0);

    private static final String WEBP_URL_1 =
        "https://i.mcmod.cn/editor/upload/20240424/1713897356_557759_yZAd.webp";
    private static final String WEBP_URL_2 =
        "https://i.mcmod.cn/editor/upload/20240424/1713897813_557759_xuvp.webp";

    // ── Helpers ──

    /**
     * Recursively collects all LayoutNodes of the given type from the layout tree.
     */
    private List<LayoutNode> findNodesByType(List<LayoutNode> nodes, LayoutType type) {
        List<LayoutNode> result = new java.util.ArrayList<>();
        for (LayoutNode node : nodes) {
            if (node.type == type) {
                result.add(node);
            }
            // Search children (table cells, paragraph block children)
            result.addAll(findNodesByType(node.children, type));
            // Search inline children (paragraph inline fragments)
            result.addAll(findNodesByType(node.inlineChildren, type));
        }
        return result;
    }

    // ── Tests ──

    @Test
    void tableWithImageNode_producesImageLayoutNode() {
        // Create a TableNode with two cells, each containing an ImageNode wrapped in ParagraphNode
        // (this matches what McmodParser.parseTable produces)
        ImageNode img1 = new ImageNode(WEBP_URL_1, "暗夜巫妖-第1张图片", null, 475, 250);
        ImageNode img2 = new ImageNode(WEBP_URL_2, "暗夜巫妖-第2张图片", null, 0, 0);

        // parseTable wraps cell content in ParagraphNode
        List<List<DocNode>> rows = List.of(
            List.of(
                new ParagraphNode(List.of(img1)),
                new ParagraphNode(List.of(img2))
            )
        );
        TableNode table = new TableNode(List.of(), rows);

        Document doc = new Document("test", null, null, List.of(table));

        LayoutEngine engine = new LayoutEngine(metrics, 0, 0, WIDTH);
        List<LayoutNode> layoutNodes = engine.layout(doc);

        // Find all IMAGE type LayoutNodes
        List<LayoutNode> imageNodes = findNodesByType(layoutNodes, LayoutType.IMAGE);
        assertEquals(2, imageNodes.size(),
            "Should have 2 IMAGE LayoutNodes, found: " + imageNodes.size()
                + " all types: " + layoutNodes.stream().map(n -> n.type).toList());

        // Verify the first image has the WebP URL
        boolean hasWebp1 = imageNodes.stream()
            .anyMatch(n -> WEBP_URL_1.equals(n.imageUrl));
        assertTrue(hasWebp1, "First IMAGE LayoutNode should have WebP URL: " + WEBP_URL_1
            + " actual URLs: " + imageNodes.stream().map(n -> n.imageUrl).toList());

        // Verify the second image has the WebP URL
        boolean hasWebp2 = imageNodes.stream()
            .anyMatch(n -> WEBP_URL_2.equals(n.imageUrl));
        assertTrue(hasWebp2, "Second IMAGE LayoutNode should have WebP URL: " + WEBP_URL_2
            + " actual URLs: " + imageNodes.stream().map(n -> n.imageUrl).toList());
    }

    @Test
    void tableWithImageNode_imageLayoutNodeUrlIsWebP_notPlaceholder() {
        // Ensure the image URL in the layout is the WebP URL, not a loading gif or placeholder
        ImageNode img = new ImageNode(WEBP_URL_1, "test image", null, 475, 250);

        List<List<DocNode>> rows = List.of(
            List.of(new ParagraphNode(List.of(img)))
        );
        TableNode table = new TableNode(List.of(), rows);
        Document doc = new Document("test", null, null, List.of(table));

        LayoutEngine engine = new LayoutEngine(metrics, 0, 0, WIDTH);
        List<LayoutNode> layoutNodes = engine.layout(doc);

        List<LayoutNode> imageNodes = findNodesByType(layoutNodes, LayoutType.IMAGE);
        assertFalse(imageNodes.isEmpty(), "Should have at least 1 IMAGE LayoutNode");

        for (LayoutNode imgNode : imageNodes) {
            assertNotNull(imgNode.imageUrl, "IMAGE LayoutNode imageUrl should not be null");
            assertTrue(imgNode.imageUrl.endsWith(".webp"),
                "IMAGE LayoutNode imageUrl should end with .webp, got: " + imgNode.imageUrl);
            assertFalse(imgNode.imageUrl.contains("loading"),
                "IMAGE LayoutNode imageUrl should not be a loading placeholder, got: " + imgNode.imageUrl);
        }
    }

    @Test
    void standaloneImageNode_producesImageLayoutNode() {
        // A standalone ImageNode (not in a table) should also produce an IMAGE LayoutNode
        ImageNode img = new ImageNode(WEBP_URL_1, "standalone image", null, 475, 250);
        Document doc = new Document("test", null, null, List.of(img));

        LayoutEngine engine = new LayoutEngine(metrics, 0, 0, WIDTH);
        List<LayoutNode> layoutNodes = engine.layout(doc);

        List<LayoutNode> imageNodes = findNodesByType(layoutNodes, LayoutType.IMAGE);
        assertEquals(1, imageNodes.size(),
            "Standalone ImageNode should produce exactly 1 IMAGE LayoutNode");
        assertEquals(WEBP_URL_1, imageNodes.get(0).imageUrl,
            "IMAGE LayoutNode should have the WebP URL");
    }

    @Test
    void imageInParagraph_producesImageLayoutNode() {
        // ImageNode inside a ParagraphNode (as a child, not wrapped separately)
        ImageNode img = new ImageNode(WEBP_URL_1, "para image", null, 475, 250);
        ParagraphNode para = new ParagraphNode(List.of(img));
        Document doc = new Document("test", null, null, List.of(para));

        LayoutEngine engine = new LayoutEngine(metrics, 0, 0, WIDTH);
        List<LayoutNode> layoutNodes = engine.layout(doc);

        List<LayoutNode> imageNodes = findNodesByType(layoutNodes, LayoutType.IMAGE);
        assertFalse(imageNodes.isEmpty(),
            "ImageNode in ParagraphNode should produce at least 1 IMAGE LayoutNode");
        assertEquals(WEBP_URL_1, imageNodes.get(0).imageUrl,
            "IMAGE LayoutNode should have the WebP URL");
    }

    @Test
    void endToEnd_parsedTableWithImages_producesImageLayoutNodes() {
        // End-to-end: parse HTML with McmodParser, then feed to LayoutEngine
        // This tests that parser output and layout engine input are compatible
        String html = """
            <html><body>
            <div class="itemname"><h5>暗夜巫妖</h5></div>
            <div class="item-content common-text font14">
            <div class="table-scroll"><table class="table table-bordered text-nowrap"><tbody>
              <tr>
                <td style="word-break: break-all;"><span class="figure"><img alt="暗夜巫妖-第1张图片" class="lazy" src="https://www.mcmod.cn/static/public/images/loading-colourful.gif" data-src="%s" data-error="//www.mcmod.cn/images/loadfail.gif" data-width="475" data-height="250" data-original="https://www.mcmod.cn/static/public/images/loading-colourful.gif" width="475" height="250"></span></td>
                <td><span class="figure"><img alt="暗夜巫妖-第2张图片" class="lazy" src="https://www.mcmod.cn/static/public/images/loading-colourful.gif" data-src="%s" data-error="//www.mcmod.cn/images/loadfail.gif" data-original="https://www.mcmod.cn/static/public/images/loading-colourful.gif"></span></td>
              </tr>
            </tbody></table></div>
            </div>
            </body></html>
            """.formatted(WEBP_URL_1, WEBP_URL_2);

        com.cy311.omnisearch.data.parser.McmodParser parser = new com.cy311.omnisearch.data.parser.McmodParser();
        Document doc = parser.parseItemPage(html, "https://www.mcmod.cn/item/123.html");

        LayoutEngine engine = new LayoutEngine(metrics, 0, 0, WIDTH);
        List<LayoutNode> layoutNodes = engine.layout(doc);

        List<LayoutNode> imageNodes = findNodesByType(layoutNodes, LayoutType.IMAGE);
        assertTrue(imageNodes.size() >= 2,
            "End-to-end: Should have at least 2 IMAGE LayoutNodes, found: " + imageNodes.size()
                + " all top-level types: " + layoutNodes.stream().map(n -> n.type).toList());

        // Verify WebP URLs are present in the layout
        long webpCount = imageNodes.stream()
            .filter(n -> n.imageUrl != null && n.imageUrl.endsWith(".webp"))
            .count();
        assertTrue(webpCount >= 2,
            "End-to-end: At least 2 IMAGE LayoutNodes should have .webp URLs, found: " + webpCount
                + " URLs: " + imageNodes.stream().map(n -> n.imageUrl).toList());
    }

    // ── Image sizing (ImageSizeProvider integration) ──

    @Test
    void imageNode_usesRealDecodedSizeWhenAvailable() {
        // Image already loaded (size known): layout must reserve the box from the real
        // size, NOT the (possibly wrong) HTML width/height attributes.
        ImageNode img = new ImageNode("https://i.mcmod.cn/icon.png", "icon", null, 100, 50); // HTML says 100x50
        Document doc = new Document("t", null, null, List.of(img));
        // Real decoded size differs from HTML attrs (the common mcmod case)
        ImageSizeProvider provider = url -> new com.cy311.omnisearch.client.render.image.ImageDimensions(100, 60);
        LayoutEngine engine = new LayoutEngine(metrics, 0, 0, WIDTH, provider);
        List<LayoutNode> nodes = engine.layout(doc);
        LayoutNode imageNode = findNodesByType(nodes, LayoutType.IMAGE).get(0);
        // Real 100x60 wins over HTML 100x50 (height 60, not 50); fits width so no resize
        assertEquals(100, imageNode.w);
        assertEquals(60, imageNode.h);
    }

    @Test
    void imageNode_fallsBackToHtmlDimensionsWhenSizeUnknown() {
        ImageNode img = new ImageNode("https://i.mcmod.cn/a.png", "a", null, 120, 60);
        Document doc = new Document("t", null, null, List.of(img));
        // No provider (or provider returns null): use HTML width/height
        LayoutEngine engine = new LayoutEngine(metrics, 0, 0, WIDTH);
        List<LayoutNode> nodes = engine.layout(doc);
        LayoutNode imageNode = findNodesByType(nodes, LayoutType.IMAGE).get(0);
        assertEquals(120, imageNode.w);
        assertEquals(60, imageNode.h);
    }

    @Test
    void imageNode_smallImageEnforcedMinimumDisplaySize() {
        // A tiny 16x16 icon should be enlarged to a readable minimum instead of a 2px dot
        ImageNode img = new ImageNode("https://i.mcmod.cn/tiny.png", "tiny", null, 16, 16);
        Document doc = new Document("t", null, null, List.of(img));
        ImageSizeProvider provider = url -> new com.cy311.omnisearch.client.render.image.ImageDimensions(16, 16);
        LayoutEngine engine = new LayoutEngine(metrics, 0, 0, WIDTH, provider);
        List<LayoutNode> nodes = engine.layout(doc);
        LayoutNode imageNode = findNodesByType(nodes, LayoutType.IMAGE).get(0);
        assertTrue(imageNode.w >= metrics.lineHeight() * 3, "small image width should be enlarged, was " + imageNode.w);
        assertTrue(imageNode.h >= metrics.lineHeight() * 2, "small image height should be enlarged, was " + imageNode.h);
    }

    @Test
    void imageNode_noDimensions_placeholderBox() {
        ImageNode img = new ImageNode("https://i.mcmod.cn/unknown.png", "u", null, 0, 0);
        Document doc = new Document("t", null, null, List.of(img));
        LayoutEngine engine = new LayoutEngine(metrics, 0, 0, WIDTH);
        List<LayoutNode> nodes = engine.layout(doc);
        LayoutNode imageNode = findNodesByType(nodes, LayoutType.IMAGE).get(0);
        // No known size: 4 lines tall, 3:2 aspect
        assertEquals(metrics.lineHeight() * 4, imageNode.h);
        assertEquals(metrics.lineHeight() * 4 * 3 / 2, imageNode.w);
    }

    @Test
    void imageNode_centeredHorizontally() {
        ImageNode img = new ImageNode("https://i.mcmod.cn/c.png", "c", null, 100, 50);
        Document doc = new Document("t", null, null, List.of(img));
        LayoutEngine engine = new LayoutEngine(metrics, 0, 0, WIDTH);
        List<LayoutNode> nodes = engine.layout(doc);
        LayoutNode imageNode = findNodesByType(nodes, LayoutType.IMAGE).get(0);
        // Centered: x + w/2 == WIDTH/2
        assertEquals(WIDTH / 2, imageNode.x + imageNode.w / 2);
    }

    @Test
    void paragraphWithFirstLineIndent_startsIndented() {
        // mcmod.cn body paragraphs (text-indent:2em): the first text fragment must be
        // shifted right by the indent; the paragraph width stays the same.
        ParagraphNode para = new ParagraphNode(
            List.of(new TextNode("这是一段带首行缩进的正文内容，长度足够换行。")),
            true
        );
        Document doc = new Document("t", null, null, List.of(para));
        LayoutEngine engine = new LayoutEngine(metrics, 0, 0, WIDTH);
        List<LayoutNode> nodes = engine.layout(doc);
        LayoutNode paraNode = findNodesByType(nodes, LayoutType.PARAGRAPH).get(0);
        assertFalse(paraNode.inlineChildren.isEmpty());
        LayoutNode first = paraNode.inlineChildren.get(0);
        // first line starts at contentX + 2*lineHeight (indent = 2em)
        assertEquals(2 * LINE_HEIGHT, first.x);
    }

    @Test
    void paragraphWithoutIndent_startsAtContentX() {
        ParagraphNode para = new ParagraphNode(List.of(new TextNode("无缩进段落。")), false);
        Document doc = new Document("t", null, null, List.of(para));
        LayoutEngine engine = new LayoutEngine(metrics, 0, 0, WIDTH);
        List<LayoutNode> nodes = engine.layout(doc);
        LayoutNode paraNode = findNodesByType(nodes, LayoutType.PARAGRAPH).get(0);
        assertFalse(paraNode.inlineChildren.isEmpty());
        assertEquals(0, paraNode.inlineChildren.get(0).x);
    }

    // ── Short heading paragraphs (mcmod section titles) ──

    @Test
    void shortTextParagraph_isMarkedAsHeading() {
        // mcmod.cn section titles are short standalone paragraphs ("魔弹", "召唤方式")
        ParagraphNode para = new ParagraphNode(List.of(new TextNode("魔弹")));
        Document doc = new Document("t", null, null, List.of(para));
        LayoutEngine engine = new LayoutEngine(metrics, 0, 0, WIDTH);
        List<LayoutNode> nodes = engine.layout(doc);
        LayoutNode paraNode = findNodesByType(nodes, LayoutType.PARAGRAPH).get(0);
        assertEquals(3, paraNode.headingLevel, "short pure-text paragraph should be heading-marked");
    }

    @Test
    void longTextParagraph_notMarkedAsHeading() {
        ParagraphNode para = new ParagraphNode(List.of(new TextNode("这是一段很长的正文描述内容，绝对不是标题段落。")));
        Document doc = new Document("t", null, null, List.of(para));
        LayoutEngine engine = new LayoutEngine(metrics, 0, 0, WIDTH);
        List<LayoutNode> nodes = engine.layout(doc);
        LayoutNode paraNode = findNodesByType(nodes, LayoutType.PARAGRAPH).get(0);
        assertEquals(0, paraNode.headingLevel);
    }

    @Test
    void paragraphWithInlineMarkup_notMarkedAsHeading() {
        // Body text with emphasis is not a title
        ParagraphNode para = new ParagraphNode(List.of(new StyledTextNode("重要", TextStyle.BOLD)));
        Document doc = new Document("t", null, null, List.of(para));
        LayoutEngine engine = new LayoutEngine(metrics, 0, 0, WIDTH);
        List<LayoutNode> nodes = engine.layout(doc);
        LayoutNode paraNode = findNodesByType(nodes, LayoutType.PARAGRAPH).get(0);
        assertEquals(0, paraNode.headingLevel);
    }

    // ── Table natural width ──

    @Test
    void narrowTable_spansContentWidth() {
        // Tables span the full content width so they align with body text (a floating
        // narrow table looks disconnected from the page). Column widths stay content-proportional.
        TableNode table = new TableNode(
            List.of("HP", "DMG"),
            List.of(List.of(new ParagraphNode(List.of(new TextNode("20"))),
                             new ParagraphNode(List.of(new TextNode("5")))))
        );
        Document doc = new Document("t", null, null, List.of(table));
        LayoutEngine engine = new LayoutEngine(metrics, 0, 0, WIDTH);
        List<LayoutNode> nodes = engine.layout(doc);
        LayoutNode tableNode = findNodesByType(nodes, LayoutType.TABLE).get(0);
        assertEquals(WIDTH, tableNode.w, "table should span the full content width");
        assertEquals(0, tableNode.x);
    }

    @Test
    void tableWithColspan_spansMergedColumns() {
        // mcmod.cn merges cells with colspan (e.g. "6 点（难度相同）" spanning 3 columns).
        // The merged cell must span the combined column widths; other rows stay aligned.
        TableNode table = new TableNode(
            List.of("A", "B", "C"),
            List.of(
                List.of(new ParagraphNode(List.of(new TextNode("x"))),
                        new ParagraphNode(List.of(new TextNode("y"))),
                        new ParagraphNode(List.of(new TextNode("z")))),
                // colspan=3 cell spanning all columns
                List.of(new ParagraphNode(List.of(new TextNode("merged"))))
            ),
            List.of(
                List.of(1, 1, 1),
                List.of(3)
            )
        );
        Document doc = new Document("t", null, null, List.of(table));
        LayoutEngine engine = new LayoutEngine(metrics, 0, 0, WIDTH);
        List<LayoutNode> nodes = engine.layout(doc);
        LayoutNode tableNode = findNodesByType(nodes, LayoutType.TABLE).get(0);
        // children are row containers: header row, data row, merged row
        assertEquals(3, tableNode.children.size());
        LayoutNode mergedRow = tableNode.children.get(2);
        assertEquals(1, mergedRow.children.size());
        LayoutNode merged = mergedRow.children.get(0);
        LayoutNode headerRow = tableNode.children.get(0);
        assertEquals(headerRow.children.get(0).x, merged.x);
        assertEquals(tableNode.w, merged.w);
    }

    // ── Bold width measurement (regression: bold text used to overflow) ──

    @Test
    void boldText_wrapsBeforeOverflow() {
        // Bold glyphs are wider (+1px/char in the bold measurer). Wrapping must use the
        // bold width or the last chunk overflows the container (mcmod.cn "注:…" bug).
        // Each char = 6px regular, 7px bold. WIDTH=200 fits ~28 regular / ~24 bold chars.
        FontMetrics boldMetrics = new FontMetrics(
            LINE_HEIGHT,
            text -> text != null ? text.length() * PX_PER_CHAR : 0,
            text -> text != null ? text.length() * (PX_PER_CHAR + 1) : 0
        );

        StyledTextNode bold = new StyledTextNode("这是粗体文字，长度较长需要在容器内正确换行，不能超出边界",
            new TextStyle(true, false, false, false, null));
        ParagraphNode para = new ParagraphNode(List.of(bold));
        Document doc = new Document("t", null, null, List.of(para));

        LayoutEngine engine = new LayoutEngine(boldMetrics, 0, 0, WIDTH);
        List<LayoutNode> nodes = engine.layout(doc);
        LayoutNode paraNode = findNodesByType(nodes, LayoutType.PARAGRAPH).get(0);

        assertFalse(paraNode.inlineChildren.isEmpty());
        // Every inline chunk must sit fully inside [0, WIDTH] — no overflow past the edge.
        for (LayoutNode chunk : paraNode.inlineChildren) {
            int right = chunk.x + chunk.w;
            assertTrue(right <= WIDTH,
                "bold chunk overflows: x=" + chunk.x + " w=" + chunk.w + " right=" + right
                    + " (WIDTH=" + WIDTH + "), text='" + chunk.text + "'");
        }
    }

    @Test
    void boldText_wrapsEarlierThanRegularText() {
        // With the same container, bold (wider) text must produce more lines than regular.
        FontMetrics boldMetrics = new FontMetrics(
            LINE_HEIGHT,
            text -> text != null ? text.length() * PX_PER_CHAR : 0,
            text -> text != null ? text.length() * (PX_PER_CHAR + 1) : 0
        );

        // 34 chars: regular=34*6=204px > 200 (1 overflow), bold=34*7=238px (2 lines).
        String text = "这段文字会被折成多行因为内容确实比较长需要足够的长度来验证换行行为";
        StyledTextNode bold = new StyledTextNode(text, new TextStyle(true, false, false, false, null));
        TextNode regular = new TextNode(text);

        int boldLines = countParagraphLines(boldMetrics, regularPara(bold));
        int regularLines = countParagraphLines(boldMetrics, regularPara(regular));

        assertTrue(boldLines > regularLines,
            "bold should wrap into more lines than regular: bold=" + boldLines + " regular=" + regularLines);
    }

    private ParagraphNode regularPara(DocNode child) {
        return new ParagraphNode(List.of(child));
    }

    private int countParagraphLines(FontMetrics m, ParagraphNode para) {
        Document doc = new Document("t", null, null, List.of(para));
        LayoutEngine engine = new LayoutEngine(m, 0, 0, WIDTH);
        List<LayoutNode> nodes = engine.layout(doc);
        LayoutNode paraNode = findNodesByType(nodes, LayoutType.PARAGRAPH).get(0);
        // Count distinct Y positions (one per wrapped line)
        java.util.Set<Integer> ys = new java.util.HashSet<>();
        for (LayoutNode chunk : paraNode.inlineChildren) {
            ys.add(chunk.y);
        }
        return Math.max(1, ys.size());
    }
}
