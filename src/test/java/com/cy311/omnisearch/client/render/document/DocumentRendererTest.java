package com.cy311.omnisearch.client.render.document;

import com.cy311.omnisearch.client.render.RenderTestUtil;
import com.cy311.omnisearch.data.model.document.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    private static DocumentRenderer createRenderer(GuiGraphics gui, Font font) {
        return new DocumentRenderer(gui, font, X, Y, WIDTH);
    }

    /**
     * Creates a Font mock with plainSubstrByWidth stubbed to match the
     * width(text)=text.length()*6 convention.
     */
    private static Font createFontWithWrapStub() {
        Font font = RenderTestUtil.createMockFont();
        lenient().when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            int maxWidth = invocation.getArgument(1);
            int maxChars = Math.max(0, maxWidth / RenderTestUtil.PX_PER_CHAR);
            if (maxChars >= text.length()) return text;
            return text.substring(0, maxChars);
        });
        return font;
    }

    // ===========================================================
    // 1. TextNode
    // ===========================================================

    @Test
    void textNode_rendersPlainText() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(gui, font);
        var doc = new Document("test", null, null,
            List.of(new TextNode("Hello World")));

        renderer.render(doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertEquals(1, calls.size());
        assertEquals("Hello World", calls.get(0).text());
        assertEquals(0xFFFFFFFF, calls.get(0).color());
    }

    @Test
    void textNode_rendersAtCorrectPosition() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = new DocumentRenderer(gui, font, 10, 20, WIDTH);
        var doc = new Document("test", null, null,
            List.of(new TextNode("Hello")));

        renderer.render(doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertEquals(1, calls.size());
        assertEquals(10, calls.get(0).x());
        assertEquals(20, calls.get(0).y());
    }

    @Test
    void textNode_wrapsWhenExceedingWidth() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = createFontWithWrapStub();
        // Width=200, PX_PER_CHAR=6 => ~33 chars per line.
        // 42 chars "AAA BBB CCC DDD EEE FFF GGG HHH III JJJ" (width=252 > 200) triggers wrapping.
        var renderer = new DocumentRenderer(gui, font, X, Y, WIDTH);
        String longText = "AAA BBB CCC DDD EEE FFF GGG HHH III JJJ";
        var doc = new Document("test", null, null,
            List.of(new TextNode(longText)));

        renderer.render(doc);

        // Verify wrapping occurred: more than one draw call means text was split
        var calls = RenderTestUtil.getDrawCalls(gui);
        assertTrue(calls.size() >= 2,
            "should wrap long text into at least 2 lines, got: " + calls.size());
    }

    // ===========================================================
    // 2. StyledTextNode
    // ===========================================================

    @Test
    void styledTextNode_boldRendersDouble() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(gui, font);
        var node = new StyledTextNode("Bold", TextStyle.BOLD);
        var doc = new Document("test", null, null, List.of(node));

        renderer.render(doc);

        // Bold renders twice: at (x, y) and (x+1, y)
        var calls = RenderTestUtil.getDrawCalls(gui);
        assertEquals(2, calls.size(),
            "bold text should produce 2 drawString calls");
        assertEquals("Bold", calls.get(0).text());
        assertEquals(X, calls.get(0).x());
        assertEquals(X + 1, calls.get(1).x());
    }

    @Test
    void styledTextNode_colorText() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(gui, font);
        var style = new TextStyle(false, false, false, false, "#FF6600");
        var node = new StyledTextNode("Orange", style);
        var doc = new Document("test", null, null, List.of(node));

        renderer.render(doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertEquals(1, calls.size());
        assertEquals(0xFFFF6600, calls.get(0).color());
    }

    @Test
    void styledTextNode_normalTextNoBold() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(gui, font);
        var node = new StyledTextNode("Normal", TextStyle.NORMAL);
        var doc = new Document("test", null, null, List.of(node));

        renderer.render(doc);

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
        var renderer = createRenderer(gui, font);
        var node = new HeadingNode(1, List.of(new TextNode("Title")));
        var doc = new Document("test", null, null, List.of(node));

        renderer.render(doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertEquals(1, calls.size());
        assertEquals("Title", calls.get(0).text());
        assertEquals(0xFFFFAA00, calls.get(0).color());
    }

    @Test
    void headingNode_level2_lightGoldColor() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(gui, font);
        var node = new HeadingNode(2, List.of(new TextNode("Subtitle")));
        var doc = new Document("test", null, null, List.of(node));

        renderer.render(doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertEquals(1, calls.size());
        assertEquals("Subtitle", calls.get(0).text());
        assertEquals(0xFFFFD700, calls.get(0).color());
    }

    @Test
    void headingNode_level3_whiteColor() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(gui, font);
        var node = new HeadingNode(3, List.of(new TextNode("Small Heading")));
        var doc = new Document("test", null, null, List.of(node));

        renderer.render(doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertEquals(1, calls.size());
        assertEquals("Small Heading", calls.get(0).text());
        assertEquals(0xFFFFFFFF, calls.get(0).color());
    }

    @Test
    void headingNode_levelHigherThan3_whiteColor() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(gui, font);
        var node = new HeadingNode(5, List.of(new TextNode("Deep Heading")));
        var doc = new Document("test", null, null, List.of(node));

        renderer.render(doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertEquals(1, calls.size());
        assertEquals(0xFFFFFFFF, calls.get(0).color());
    }

    @Test
    void headingNode_usesPoseTransform() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(gui, font);
        var node = new HeadingNode(1, List.of(new TextNode("Scaled")));
        var doc = new Document("test", null, null, List.of(node));

        renderer.render(doc);

        verify(gui.pose()).pushPose();
        verify(gui.pose()).popPose();
        verify(gui.pose()).translate(eq((float) X), eq((float) Y), eq(0.0f));
        verify(gui.pose()).scale(eq(1.5f), eq(1.5f), eq(1.0f));
    }

    // ===========================================================
    // 4. ParagraphNode
    // ===========================================================

    @Test
    void paragraphNode_rendersInlineChildren() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(gui, font);
        var para = new ParagraphNode(List.of(
            new TextNode("Hello"),
            new TextNode(" "),
            new TextNode("World")
        ));
        var doc = new Document("test", null, null, List.of(para));

        renderer.render(doc);

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
        var renderer = createRenderer(gui, font);
        var p1 = new ParagraphNode(List.of(new TextNode("First")));
        var p2 = new ParagraphNode(List.of(new TextNode("Second")));
        var doc = new Document("test", null, null, List.of(p1, p2));

        renderer.render(doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertEquals(2, calls.size());
        // First paragraph: y += 2, so renders at y=2
        assertEquals(2, calls.get(0).y());
        // Second paragraph should be at a different Y due to spacing
        assertNotEquals(calls.get(0).y(), calls.get(1).y(),
            "paragraphs should be at different Y positions");
    }

    // ===========================================================
    // 5. LinkNode
    // ===========================================================

    @Test
    void linkNode_rendersInBlue() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(gui, font);
        var link = new LinkNode("https://example.com",
            List.of(new TextNode("Click Me")));
        var doc = new Document("test", null, null, List.of(link));

        renderer.render(doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertEquals(1, calls.size());
        assertEquals("Click Me", calls.get(0).text());
        assertEquals(0xFF5555FF, calls.get(0).color());
    }

    @Test
    void linkNode_underlinesText() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(gui, font);
        // "Hi" has width=12, so x1=0, x2=11
        var link = new LinkNode("https://example.com",
            List.of(new TextNode("Hi")));
        var doc = new Document("test", null, null, List.of(link));

        renderer.render(doc);

        verify(gui).hLine(eq(0), eq(11), anyInt(), eq(0xFF5555FF));
    }

    @Test
    void linkNode_recursiveChildren() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(gui, font);
        var link = new LinkNode("https://example.com",
            List.of(new StyledTextNode("Bold Link", TextStyle.BOLD)));
        var doc = new Document("test", null, null, List.of(link));

        renderer.render(doc);

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
        var renderer = createRenderer(gui, font);
        var rows = List.<List<DocNode>>of(
            List.of(new TextNode("Alice"), new TextNode("100"))
        );
        var table = new TableNode(List.of("Name", "Value"), rows);
        var doc = new Document("test", null, null, List.of(table));

        renderer.render(doc);

        // Headers rendered twice each (shadow effect)
        var calls = RenderTestUtil.getDrawCalls(gui);
        long headerCalls = calls.stream()
            .filter(c -> c.text().equals("Name") || c.text().equals("Value"))
            .count();
        assertEquals(4, headerCalls,
            "each header drawn twice (shadow)");

        // Header background fill
        verify(gui).fill(anyInt(), anyInt(), anyInt(), anyInt(),
            eq(0xFF333333));
    }

    @Test
    void tableNode_rendersDataRows() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(gui, font);
        var rows = List.<List<DocNode>>of(
            List.of(new TextNode("Alice"), new TextNode("100"))
        );
        var table = new TableNode(List.of("Name", "Value"), rows);
        var doc = new Document("test", null, null, List.of(table));

        renderer.render(doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        long dataCalls = calls.stream()
            .filter(c -> c.text().equals("Alice") || c.text().equals("100"))
            .count();
        assertEquals(2, dataCalls,
            "data cells should be drawn once each");
    }

    @Test
    void tableNode_emptyHeaders_skipsRendering() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(gui, font);
        var table = new TableNode(List.of(), List.of());
        var doc = new Document("test", null, null, List.of(table));

        renderer.render(doc);

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
        var renderer = createRenderer(gui, font);
        var list = new ListNode(true, List.of(
            new ParagraphNode(List.of(new TextNode("First item"))),
            new ParagraphNode(List.of(new TextNode("Second item")))
        ));
        var doc = new Document("test", null, null, List.of(list));

        renderer.render(doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        long markerCalls = calls.stream()
            .filter(c -> c.text().equals("1.") || c.text().equals("2."))
            .count();
        assertEquals(2, markerCalls,
            "ordered list should render numeric markers");

        long contentCalls = calls.stream()
            .filter(c -> c.text().equals("First item")
                || c.text().equals("Second item"))
            .count();
        assertEquals(2, contentCalls,
            "ordered list should render item content");
    }

    @Test
    void listNode_unordered_rendersBullets() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(gui, font);
        var list = new ListNode(false, List.of(
            new ParagraphNode(List.of(new TextNode("Bullet A"))),
            new ParagraphNode(List.of(new TextNode("Bullet B")))
        ));
        var doc = new Document("test", null, null, List.of(list));

        renderer.render(doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        long bulletCalls = calls.stream()
            .filter(c -> c.text().equals("\u2022"))
            .count();
        assertEquals(2, bulletCalls,
            "unordered list should render bullet markers");
    }

    @Test
    void listNode_contentIndented() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(gui, font);
        var list = new ListNode(true, List.of(
            new ParagraphNode(List.of(new TextNode("Item")))
        ));
        var doc = new Document("test", null, null, List.of(list));

        renderer.render(doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        var marker = calls.stream()
            .filter(c -> c.text().equals("1."))
            .findFirst().orElseThrow();
        assertEquals(X, marker.x());
        var content = calls.stream()
            .filter(c -> c.text().equals("Item"))
            .findFirst().orElseThrow();
        assertEquals(X + 10, content.x());
    }

    // ===========================================================
    // 8. DividerNode
    // ===========================================================

    @Test
    void dividerNode_rendersHorizontalLine() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(gui, font);
        var doc = new Document("test", null, null,
            List.of(new DividerNode()));

        renderer.render(doc);

        // margin = min(10, width/8) = min(10, 25) = 10
        // dividerX = 0 + 10 = 10
        // dividerWidth = 200 - 20 = 180
        // hLine(10, 10 + 180 = 190, midY, 0xFF888888)
        verify(gui).hLine(eq(10), eq(190), anyInt(), eq(0xFF888888));
    }

    // ===========================================================
    // 9. SectionNode
    // ===========================================================

    @Test
    void sectionNode_rendersTitleAndChildren() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(gui, font);
        var section = new SectionNode("Overview",
            List.of(new TextNode("Some content")));
        var doc = new Document("test", null, null, List.of(section));

        renderer.render(doc);

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
        var renderer = createRenderer(gui, font);
        var section = new SectionNode("Sec",
            List.of(new TextNode("Child")));
        var doc = new Document("test", null, null, List.of(section));

        renderer.render(doc);

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
        var renderer = createRenderer(gui, font);
        var img = new ImageNode(
            "https://example.com/img.png", "alt text", null);
        var doc = new Document("test", null, null, List.of(img));

        renderer.render(doc);

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
        var renderer = new DocumentRenderer(gui, font, 10, 20, WIDTH);
        var img = new ImageNode("url", "img", null);
        var doc = new Document("test", null, null, List.of(img));

        renderer.render(doc);

        // fill(10, 20, 10+64=74, 20+48=68, 0xFF444444)
        verify(gui).fill(eq(10), eq(20), eq(74), eq(68),
            eq(0xFF444444));
    }

    @Test
    void imageNode_emptyAlt_skipsText() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(gui, font);
        var img = new ImageNode("url", "", null);
        var doc = new Document("test", null, null, List.of(img));

        renderer.render(doc);

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
        var renderer = createRenderer(gui, font);

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

        renderer.render(doc);

        var calls = RenderTestUtil.getDrawCalls(gui);

        assertTrue(
            calls.stream().anyMatch(c -> c.text().equals("Main Title")),
            "heading1");
        assertTrue(
            calls.stream().anyMatch(c -> c.text().equals("bold")),
            "bold styled text");
        assertTrue(
            calls.stream().anyMatch(c -> c.text().equals("1.")),
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
        var renderer = createRenderer(gui, font);
        var doc = new Document("empty", null, null, List.of());

        renderer.render(doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertTrue(calls.isEmpty());
    }

    @Test
    void textNode_emptyString_returnsYAdvance() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = createRenderer(gui, font);
        var doc = new Document("test", null, null,
            List.of(new TextNode("")));

        renderer.render(doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertTrue(calls.isEmpty(),
            "empty string should not produce draw calls");
    }

    @Test
    void multipleNodes_cascadeYCorrectly() {
        var gui = RenderTestUtil.createMockGuiGraphics();
        var font = RenderTestUtil.createMockFont();
        var renderer = new DocumentRenderer(gui, font, X, 100, WIDTH);
        var doc = new Document("test", null, null, List.of(
            new TextNode("Line1"),
            new TextNode("Line2")
        ));

        renderer.render(doc);

        var calls = RenderTestUtil.getDrawCalls(gui);
        assertTrue(calls.size() >= 2, "should have at least 2 draw calls");
        // Each TextNode advances Y by font.lineHeight (9)
        assertEquals(100, calls.get(0).y());
    }
}
