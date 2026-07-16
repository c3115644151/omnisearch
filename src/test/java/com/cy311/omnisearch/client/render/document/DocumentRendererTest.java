package com.cy311.omnisearch.client.render.document;

import com.cy311.omnisearch.client.render.RenderTestUtil;
import com.cy311.omnisearch.client.render.image.ImageManager;
import com.cy311.omnisearch.gui.theme.OmniTheme;
import com.cy311.omnisearch.data.model.document.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link DocumentRenderer}.
 *
 * <p>Uses mock Font where {@code font.lineHeight} is 0 (because lineHeight is a final field
 * and Mockito cannot stub fields). This means all drawString calls land at the same Y
 * within a block-level node. Y advancement is still observable via paragraph spacing (4px)
 * and the returned Y offsets.
 */
class DocumentRendererTest {

    private static final int X = 0;
    private static final int Y = 0;
    private static final int WIDTH = 200;

    // ---- helpers ----

    private static DocumentRenderer createRenderer(Font font) {
        return new DocumentRenderer(font, null);
    }

    private static void renderDocument(DocumentRenderer renderer, GuiGraphics gui, Document doc) {
        renderDocument(renderer, gui, doc, X, Y);
    }

    private static void renderDocument(DocumentRenderer renderer, GuiGraphics gui, Document doc, int offsetX, int offsetY) {
        var layout = renderer.prepare(doc, WIDTH);
        renderer.paint(gui, layout, offsetX, offsetY);
    }

    // ===========================================================
    // 1. TextNode
    // ===========================================================

    @Test
    void textNode_rendersPlainText() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var doc = new Document("test", null, null,
            List.of(new TextNode("Hello World")));

        renderDocument(renderer, gui, doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertEquals(1, calls.size());
        assertEquals("Hello World", calls.get(0).text());
        assertEquals(0xFFFFFFFF, calls.get(0).color());
    }

    @Test
    void textNode_rendersAtCorrectPosition() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var doc = new Document("test", null, null,
            List.of(new TextNode("Hello")));

        renderDocument(renderer, gui, doc, 10, 20);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertEquals(1, calls.size());
        assertEquals(10, calls.get(0).x());
        assertEquals(20, calls.get(0).y());
    }

    @Test
    void textNode_wrapsWhenExceedingWidth() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        // With PX_PER_CHAR=6 and width=200, each "x " is 12px wide.
        // 16 items fit per line (200/12 = 16). Item 17 wraps to line 2.
        var words = new java.util.ArrayList<DocNode>();
        for (int i = 0; i < 30; i++) {
            words.add(new TextNode("x "));
        }
        var para = new ParagraphNode(words);
        var doc = new Document("test", null, null, List.of(para));

        renderDocument(renderer, gui, doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        // First item starts at x=0
        assertEquals(0, calls.get(0).x());
        int firstLineY = calls.get(0).y();
        var wrappedCall = calls.stream()
            .filter(c -> c.y() > firstLineY)
            .findFirst()
            .orElseThrow(() -> new AssertionError("expected wrapped content on a later line"));
        assertEquals(0, wrappedCall.x(),
            "wrapped line should restart from the left edge");
    }

    // ===========================================================
    // 2. StyledTextNode
    // ===========================================================

    @Test
    void styledTextNode_boldRendersViaComponent() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var node = new StyledTextNode("Bold", TextStyle.BOLD);
        var doc = new Document("test", null, null, List.of(node));

        renderDocument(renderer, gui, doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertEquals(1, calls.size(),
            "bold text should produce 1 drawString call via Component API");
        assertEquals("Bold", calls.get(0).text());
        assertEquals(X, calls.get(0).x());
        assertEquals(Y, calls.get(0).y());
    }

    @Test
    void styledTextNode_colorText() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var style = new TextStyle(false, false, false, false, "#FF6600");
        var node = new StyledTextNode("Orange", style);
        var doc = new Document("test", null, null, List.of(node));

        renderDocument(renderer, gui, doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertEquals(1, calls.size());
        assertEquals(0xFFFF6600, calls.get(0).color());
    }

    @Test
    void styledTextNode_normalTextNoBold() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var node = new StyledTextNode("Normal", TextStyle.NORMAL);
        var doc = new Document("test", null, null, List.of(node));

        renderDocument(renderer, gui, doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertEquals(1, calls.size(),
            "normal styled text should produce 1 drawString call");
        assertEquals("Normal", calls.get(0).text());
    }

    // ===========================================================
    // 3. HeadingNode
    // ===========================================================

    @Test
    void headingNode_level1_goldColor() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var node = new HeadingNode(1, List.of(new TextNode("Title")));
        var doc = new Document("test", null, null, List.of(node));

        renderDocument(renderer, gui, doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertEquals(1, calls.size());
        assertEquals("Title", calls.get(0).text());
        assertEquals(0xFFFFAA00, calls.get(0).color());
    }

    @Test
    void headingNode_level2_lightGoldColor() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var node = new HeadingNode(2, List.of(new TextNode("Subtitle")));
        var doc = new Document("test", null, null, List.of(node));

        renderDocument(renderer, gui, doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertEquals(1, calls.size());
        assertEquals("Subtitle", calls.get(0).text());
        assertEquals(0xFFFFD700, calls.get(0).color());
    }

    @Test
    void headingNode_level3_whiteColor() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var node = new HeadingNode(3, List.of(new TextNode("Small Heading")));
        var doc = new Document("test", null, null, List.of(node));

        renderDocument(renderer, gui, doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertEquals(1, calls.size());
        assertEquals("Small Heading", calls.get(0).text());
        assertEquals(0xFFFFFFFF, calls.get(0).color());
    }

    @Test
    void headingNode_levelHigherThan3_whiteColor() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var node = new HeadingNode(5, List.of(new TextNode("Deep Heading")));
        var doc = new Document("test", null, null, List.of(node));

        renderDocument(renderer, gui, doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertEquals(1, calls.size());
        assertEquals(0xFFFFFFFF, calls.get(0).color());
    }

    // ===========================================================
    // 4. ParagraphNode
    // ===========================================================

    @Test
    void paragraphNode_rendersInlineChildren() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var para = new ParagraphNode(List.of(
            new TextNode("Hello"),
            new TextNode(" "),
            new TextNode("World")
        ));
        var doc = new Document("test", null, null, List.of(para));

        renderDocument(renderer, gui, doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertEquals(3, calls.size());
        assertEquals("Hello", calls.get(0).text());
        assertEquals(" ", calls.get(1).text());
        assertEquals("World", calls.get(2).text());
    }

    @Test
    void paragraphNode_addsSpacingAfter() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var p1 = new ParagraphNode(List.of(new TextNode("First")));
        var p2 = new ParagraphNode(List.of(new TextNode("Second")));
        var doc = new Document("test", null, null, List.of(p1, p2));

        renderDocument(renderer, gui, doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertEquals(2, calls.size());
        // First paragraph renders at y=0 (no inner offset)
        assertEquals(0, calls.get(0).y());
        // Second paragraph should be at a different Y due to spacing
        assertNotEquals(calls.get(0).y(), calls.get(1).y(),
            "paragraphs should be at different Y positions");
    }

    @Test
    void paragraphNode_inlineIconConsumesInlineSpace() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var para = new ParagraphNode(List.of(
            new TextNode("A"),
            new ImageInlineNode("mc-icon://icon-health-full", "health"),
            new TextNode("B")
        ));
        var doc = new Document("test", null, null, List.of(para));

        renderDocument(renderer, gui, doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        var sprites = RenderTestUtil.getSpriteCalls(gui);
        assertEquals(2, calls.size());
        assertEquals(1, sprites.size());
        assertEquals("A", calls.get(0).text());
        assertEquals("B", calls.get(1).text());
        assertEquals(0, calls.get(0).x());
        assertTrue(sprites.get(0).width() > 0);
        assertEquals(calls.get(0).x() + RenderTestUtil.PX_PER_CHAR, sprites.get(0).x());
        assertEquals(
            sprites.get(0).x() + sprites.get(0).width() + 1,
            calls.get(1).x(),
            "icon should advance inline flow before the next text fragment"
        );
    }

    @Test
    void paragraphNode_linkWrappedAcrossLinesExtractsMultipleHits() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var longLinkText = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMN";
        var link = new LinkNode("https://example.com/test", List.of(new TextNode(longLinkText)));
        var doc = new Document("test", null, null, List.of(new ParagraphNode(List.of(link))));

        var layout = renderer.prepare(doc, 60);
        renderer.paint(gui, layout, X, Y);

        var hits = layout.extractLinks();
        assertTrue(hits.size() >= 2, "wrapped link should produce one hit per rendered fragment");
        assertTrue(hits.stream().map(DocumentRenderer.LinkHit::y).distinct().count() >= 2);
        var calls = RenderTestUtil.getDrawCalls(gui);
        assertTrue(calls.stream().allMatch(c -> c.color() == 0xFF5555FF));
    }

    // ===========================================================
    // 5. LinkNode
    // ===========================================================

    @Test
    void linkNode_rendersInBlue() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var link = new LinkNode("https://example.com",
            List.of(new TextNode("Click Me")));
        var doc = new Document("test", null, null, List.of(link));

        renderDocument(renderer, gui, doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertEquals(1, calls.size());
        assertEquals("Click Me", calls.get(0).text());
        assertEquals(0xFF5555FF, calls.get(0).color());
    }

    @Test
    void linkNode_underlinesTextOnHover() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        // "Hi" has textWidth=2*6=12, so x1=0, x2=11
        var link = new LinkNode("https://example.com",
            List.of(new TextNode("Hi")));
        var doc = new Document("test", null, null, List.of(link));

        // Paint with mouse hovering over the link (X=0..12, Y=0..9)
        var layout = renderer.prepare(doc, WIDTH);
        renderer.paint(gui, layout, X, Y, X + 1, Y + 1);

        // Link underline only shows on hover, in white
        verify(gui).hLine(eq(X), eq(X + RenderTestUtil.PX_PER_CHAR * 2 - 1), anyInt(), eq(OmniTheme.TEXT_WHITE));
    }

    @Test
    void linkNode_recursiveChildren() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var link = new LinkNode("https://example.com",
            List.of(new StyledTextNode("Bold Link", TextStyle.BOLD)));
        var doc = new Document("test", null, null, List.of(link));

        renderDocument(renderer, gui, doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertTrue(calls.size() >= 1);
        calls.forEach(c ->
            assertEquals(0xFF5555FF, c.color(), "link children should be blue"));
    }

    // ===========================================================
    // 6. TableNode
    // ===========================================================

    @Test
    void tableNode_rendersHeader() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var rows = List.<List<DocNode>>of(
            List.of(new TextNode("Alice"), new TextNode("100"))
        );
        var table = new TableNode(List.of("Name", "Value"), rows);
        var doc = new Document("test", null, null, List.of(table));

        renderDocument(renderer, gui, doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        long headerCalls = calls.stream()
            .filter(c -> c.text().equals("Name") || c.text().equals("Value"))
            .count();
        assertEquals(2, headerCalls,
            "each header drawn once via Component API");

        // Header background fill (2 header cells)
        verify(gui, times(2)).fill(anyInt(), anyInt(), anyInt(), anyInt(),
            eq(0xFF333333));
    }

    @Test
    void tableNode_rendersDataRows() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var rows = List.<List<DocNode>>of(
            List.of(new TextNode("Alice"), new TextNode("100"))
        );
        var table = new TableNode(List.of("Name", "Value"), rows);
        var doc = new Document("test", null, null, List.of(table));

        renderDocument(renderer, gui, doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        long dataCalls = calls.stream()
            .filter(c -> c.text().equals("Alice") || c.text().equals("100"))
            .count();
        assertEquals(2, dataCalls,
            "data cells drawn once in renderTableNode");
    }

    @Test
    void tableNode_emptyHeaders_skipsRendering() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var table = new TableNode(List.of(), List.of());
        var doc = new Document("test", null, null, List.of(table));

        renderDocument(renderer, gui, doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertTrue(calls.isEmpty());
    }

    // ===========================================================
    // 7. ListNode
    // ===========================================================

    @Test
    void listNode_ordered_rendersNumbers() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var list = new ListNode(true, List.of(
            new ParagraphNode(List.of(new TextNode("First item"))),
            new ParagraphNode(List.of(new TextNode("Second item")))
        ));
        var doc = new Document("test", null, null, List.of(list));

        renderDocument(renderer, gui, doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        long markerCalls = calls.stream()
            .filter(c -> c.text().equals("1. ") || c.text().equals("2. "))
            .count();
        assertEquals(2, markerCalls,
            "ordered list markers rendered once (renderListNode)");

        long contentCalls = calls.stream()
            .filter(c -> c.text().equals("First item")
                || c.text().equals("Second item"))
            .count();
        assertEquals(2, contentCalls,
            "ordered list content rendered once (renderListNode)");
    }

    @Test
    void listNode_unordered_rendersBullets() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var list = new ListNode(false, List.of(
            new ParagraphNode(List.of(new TextNode("Bullet A"))),
            new ParagraphNode(List.of(new TextNode("Bullet B")))
        ));
        var doc = new Document("test", null, null, List.of(list));

        renderDocument(renderer, gui, doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        long bulletCalls = calls.stream()
            .filter(c -> c.text().equals("\u2022 "))
            .count();
        assertEquals(2, bulletCalls,
            "unordered list bullets rendered once (renderListNode)");
    }

    @Test
    void listNode_contentIndented() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var list = new ListNode(true, List.of(
            new ParagraphNode(List.of(new TextNode("Item")))
        ));
        var doc = new Document("test", null, null, List.of(list));

        renderDocument(renderer, gui, doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        var marker = calls.stream()
            .filter(c -> c.text().equals("1. "))
            .findFirst().orElseThrow();
        assertEquals(X, marker.x());
        var content = calls.stream()
            .filter(c -> c.text().equals("Item"))
            .findFirst().orElseThrow();
        assertEquals(X + 13, content.x());
    }

    // ===========================================================
    // 8. DividerNode
    // ===========================================================

    @Test
    void dividerNode_rendersHorizontalLine() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var doc = new Document("test", null, null,
            List.of(new DividerNode()));

        renderDocument(renderer, gui, doc);

        // LayoutEngine places divider at (contentX=0, currentY=0) with width=200
        // hLine(0, 0+200=200, midY, 0xFF888888)
        verify(gui).hLine(eq(0), eq(200), anyInt(), eq(0xFF888888));
    }

    // ===========================================================
    // 9. SectionNode
    // ===========================================================

    @Test
    void sectionNode_rendersTitleAndChildren() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var section = new SectionNode("Overview",
            List.of(new TextNode("Some content")));
        var doc = new Document("test", null, null, List.of(section));

        renderDocument(renderer, gui, doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertEquals(2, calls.size());
        assertEquals("Overview", calls.get(0).text());
        assertEquals(0xFFFFD700, calls.get(0).color());
        assertEquals("Some content", calls.get(1).text());
    }

    @Test
    void sectionNode_indentsChildren() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var section = new SectionNode("Sec",
            List.of(new TextNode("Child")));
        var doc = new Document("test", null, null, List.of(section));

        renderDocument(renderer, gui, doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertEquals("Sec", calls.get(0).text());
        assertEquals(X, calls.get(0).x());
        assertEquals("Child", calls.get(1).text());
        assertEquals(X + 4, calls.get(1).x());
    }

    // ===========================================================
    // 10. ImageNode
    // ===========================================================

    @Test
    void imageNode_rendersPlaceholder() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        // Return null future (image not yet loaded), so placeholder fallback fires
        var imgMgr = mock(ImageManager.class);
        when(imgMgr.getImage(anyString())).thenReturn(CompletableFuture.completedFuture(null));
        var renderer = new DocumentRenderer(font, imgMgr);
        var img = new ImageNode(
            "https://example.com/img.png", "alt text", null);
        var doc = new Document("test", null, null, List.of(img));

        renderDocument(renderer, gui, doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertEquals("alt text", calls.get(0).text());
        assertEquals(0xFFAAAAAA, calls.get(0).color());
        verify(gui).fill(anyInt(), anyInt(), anyInt(), anyInt(),
            eq(0xFF444444));
    }

    @Test
    void imageNode_placeholderDimensions() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        // Return null future (image not yet loaded), so placeholder fallback fires
        var imgMgr = mock(ImageManager.class);
        when(imgMgr.getImage(anyString())).thenReturn(CompletableFuture.completedFuture(null));
        var renderer = new DocumentRenderer(font, imgMgr);
        var img = new ImageNode("url", "img", null);
        var doc = new Document("test", null, null, List.of(img));

        renderDocument(renderer, gui, doc, 10, 20);

        // lineHeight=9: imageTopMargin=max(2,9/2)=4, imgH=9*4=36, imgW=min(36*3/2,200)=54
        // node.y = 0 + imageTopMargin = 4, node.h = 36 (no margins in h)
        // fill with offset(10,20): fill(10, 20+4=24, 10+54=64, 24+36=60, 0xFF444444)
        verify(gui).fill(eq(10), eq(24), eq(64), eq(60),
            eq(0xFF444444));
    }

    @Test
    void imageNode_emptyAlt_skipsText() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var img = new ImageNode("url", "", null);
        var doc = new Document("test", null, null, List.of(img));

        renderDocument(renderer, gui, doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertTrue(calls.isEmpty(),
            "no drawString for empty alt text");
    }

    // ===========================================================
    // 11. Document overall
    // ===========================================================

    @Test
    void document_renderAllNodeTypes() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        // Use null-returning ImageManager so image alt text renders as placeholder
        var imgMgr2 = mock(ImageManager.class);
        when(imgMgr2.getImage(anyString())).thenReturn(CompletableFuture.completedFuture(null));
        var renderer = new DocumentRenderer(font, imgMgr2);

        var doc = new Document("Full Doc", "TestMod", null, List.of(
            new HeadingNode(1,
                List.of(new TextNode("Main Title"))),
            new ParagraphNode(List.of(
                new TextNode("This is a paragraph with "),
                new StyledTextNode("bold", TextStyle.BOLD),
                new TextNode(" text.")
            )),
            new DividerNode(),
            new HeadingNode(2,
                List.of(new TextNode("Details"))),
            new ListNode(true, List.of(
                new ParagraphNode(
                    List.of(new TextNode("First point"))),
                new ParagraphNode(
                    List.of(new TextNode("Second point")))
            )),
            new TableNode(List.of("Key", "Value"), List.of(
                List.of(new TextNode("A"), new TextNode("1")),
                List.of(new TextNode("B"), new TextNode("2"))
            )),
            new LinkNode("https://example.com",
                List.of(new TextNode("Read more"))),
            new SectionNode("Appendix", List.of(
                new TextNode("Extra content")
            )),
            new ImageNode(
                "https://example.com/pic.png",
                "screenshot", null
            )
        ));

        renderDocument(renderer, gui, doc);

        var calls = RenderTestUtil.getDrawCalls(gui);

        assertTrue(
            calls.stream().anyMatch(c -> c.text().equals("Main Title")),
            "heading1");
        assertTrue(
            calls.stream().anyMatch(c -> c.text().equals("bold")),
            "bold styled text");
        assertTrue(
            calls.stream().anyMatch(c -> c.text().equals("1. ")),
            "list marker");
        assertTrue(
            calls.stream().anyMatch(c -> c.text().equals("Read more")),
            "link");
        assertTrue(
            calls.stream().anyMatch(c -> c.text().equals("Details")),
            "heading2");
        assertTrue(
            calls.stream().anyMatch(c ->
                c.text().equals("Extra content")),
            "section child");
        assertTrue(
            calls.stream().anyMatch(c ->
                c.text().equals("screenshot")),
            "image alt");

        verify(gui, atLeastOnce())
            .fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        verify(gui, atLeastOnce())
            .hLine(anyInt(), anyInt(), anyInt(), anyInt());
    }

    // ===========================================================
    // Edge cases
    // ===========================================================

    @Test
    void document_emptyContent_noRenderCalls() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var doc = new Document("empty", null, null, List.of());

        renderDocument(renderer, gui, doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertTrue(calls.isEmpty());
    }

    @Test
    void textNode_emptyString_returnsYAdvance() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var doc = new Document("test", null, null,
            List.of(new TextNode("")));

        renderDocument(renderer, gui, doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertTrue(calls.isEmpty(),
            "empty string should not produce draw calls");
    }

    @Test
    void multipleNodes_cascadeYCorrectly() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(font);
        var doc = new Document("test", null, null, List.of(
            new TextNode("Line1"),
            new TextNode("Line2")
        ));

        renderDocument(renderer, gui, doc, X, 100);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertTrue(calls.size() >= 2, "should have at least 2 draw calls");
        assertEquals(100, calls.get(0).y());
    }

    // ===========================================================
    // 12. Image rendering with ImageManager (WebP end-to-end)
    // ===========================================================

    private static final String WEBP_URL =
        "https://i.mcmod.cn/editor/upload/20240424/1713897356_557759_yZAd.webp";

    /**
     * Standalone ImageNode with a WebP URL: verify that renderImageNode
     * calls imageManager.getImage(webpUrl).
     */
    @Test
    void imageNode_webpUrl_callsImageManagerGetImage() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var imageManager = mock(ImageManager.class);
        // Return a completed future with null (image not yet loaded -> placeholder path)
        lenient().when(imageManager.getImage(anyString()))
            .thenReturn(CompletableFuture.completedFuture(null));

        var renderer = new DocumentRenderer(font, imageManager);
        var img = new ImageNode(WEBP_URL, "暗夜巫妖-第1张图片", null, 475, 250);
        var doc = new Document("test", null, null, List.of(img));

        renderDocument(renderer, gui, doc);

        // Verify imageManager.getImage was called with the WebP URL
        verify(imageManager).getImage(WEBP_URL);
    }

    /**
     * ImageNode inside a TableNode (the actual mcmod.cn structure):
     * verify that renderImageNode is reached for images inside table cells
     * and imageManager.getImage(webpUrl) is called.
     */
    @Test
    void imageNode_inTable_webpUrl_callsImageManagerGetImage() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var imageManager = mock(ImageManager.class);
        lenient().when(imageManager.getImage(anyString()))
            .thenReturn(CompletableFuture.completedFuture(null));

        var renderer = new DocumentRenderer(font, imageManager);

        // Build a TableNode matching parser output: each cell is a ParagraphNode containing an ImageNode
        ImageNode img1 = new ImageNode(WEBP_URL, "暗夜巫妖-第1张图片", null, 475, 250);
        ImageNode img2 = new ImageNode(
            "https://i.mcmod.cn/editor/upload/20240424/1713897813_557759_xuvp.webp",
            "暗夜巫妖-第2张图片", null, 0, 0);

        List<List<DocNode>> rows = List.of(
            List.of(
                new ParagraphNode(List.of(img1)),
                new ParagraphNode(List.of(img2))
            )
        );
        TableNode table = new TableNode(List.of(), rows);
        var doc = new Document("test", null, null, List.of(table));

        renderDocument(renderer, gui, doc);

        // Verify imageManager.getImage was called with both WebP URLs
        verify(imageManager).getImage(WEBP_URL);
        verify(imageManager).getImage(
            "https://i.mcmod.cn/editor/upload/20240424/1713897813_557759_xuvp.webp");
    }

    /**
     * End-to-end: parse HTML with McmodParser, layout with DocumentRenderer.prepare(),
     * paint with mock ImageManager. Verify imageManager.getImage is called with WebP URL.
     */
    @Test
    void endToEnd_parsedWebpImage_callsImageManagerGetImage() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var imageManager = mock(ImageManager.class);
        lenient().when(imageManager.getImage(anyString()))
            .thenReturn(CompletableFuture.completedFuture(null));

        var renderer = new DocumentRenderer(font, imageManager);

        String html = """
            <html><body>
            <div class="itemname"><h5>暗夜巫妖</h5></div>
            <div class="item-content common-text font14">
            <div class="table-scroll"><table class="table table-bordered text-nowrap"><tbody>
              <tr>
                <td style="word-break: break-all;"><span class="figure"><img alt="暗夜巫妖-第1张图片" class="lazy" src="https://www.mcmod.cn/static/public/images/loading-colourful.gif" data-src="%s" data-error="//www.mcmod.cn/images/loadfail.gif" data-width="475" data-height="250" data-original="https://www.mcmod.cn/static/public/images/loading-colourful.gif" width="475" height="250"></span></td>
              </tr>
            </tbody></table></div>
            </div>
            </body></html>
            """.formatted(WEBP_URL);

        com.cy311.omnisearch.data.parser.McmodParser parser =
            new com.cy311.omnisearch.data.parser.McmodParser();
        var doc = parser.parseItemPage(html, "https://www.mcmod.cn/item/123.html");

        renderDocument(renderer, gui, doc);

        // The critical assertion: ImageManager.getImage must be called with the WebP URL.
        // If this fails, it means the WebP URL was lost somewhere between parse -> layout -> render.
        verify(imageManager).getImage(WEBP_URL);
    }

    /**
     * ImageNode with a loaded image (non-null ResourceLocation from ImageManager):
     * verify that gui.blit is called to render the texture.
     */
    @Test
    void imageNode_loadedImage_callsBlit() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var imageManager = mock(ImageManager.class);
        var mockLoc = mock(ResourceLocation.class);
        when(imageManager.getImage(WEBP_URL))
            .thenReturn(CompletableFuture.completedFuture(mockLoc));

        var renderer = new DocumentRenderer(font, imageManager);
        var img = new ImageNode(WEBP_URL, "", null, 475, 250);
        var doc = new Document("test", null, null, List.of(img));

        renderDocument(renderer, gui, doc);

        // When image is loaded, blit should be called to render it
        verify(gui).blit(eq(mockLoc), anyInt(), anyInt(), anyFloat(), anyFloat(),
            anyInt(), anyInt(), anyInt(), anyInt());
    }
}
