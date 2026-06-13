package com.cy311.omnisearch.data.model;

import com.cy311.omnisearch.data.model.document.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocNodeCreationTest {

    @Test
    void headingNode_createsWithLevelAndChildren() {
        var child = new TextNode("hello");
        var node = new HeadingNode(2, List.of(child));
        assertEquals(2, node.getLevel());
        assertEquals(1, node.getChildren().size());
        assertEquals(child, node.getChildren().get(0));
        assertEquals("heading", node.getType());
    }

    @Test
    void headingNode_rejectsNullChildren() {
        assertThrows(NullPointerException.class, () -> new HeadingNode(1, null));
    }

    @Test
    void headingNode_childrenIsUnmodifiable() {
        var children = new ArrayList<DocNode>(List.of(new TextNode("a")));
        var node = new HeadingNode(1, children);
        children.add(new TextNode("b"));
        assertEquals(1, node.getChildren().size(),
            "external list mutation should not affect node");
    }

    @Test
    void paragraphNode_createsWithChildren() {
        var child = new TextNode("text");
        var node = new ParagraphNode(List.of(child));
        assertEquals(1, node.getChildren().size());
        assertEquals(child, node.getChildren().get(0));
        assertEquals("paragraph", node.getType());
    }

    @Test
    void paragraphNode_rejectsNullChildren() {
        assertThrows(NullPointerException.class, () -> new ParagraphNode(null));
    }

    @Test
    void paragraphNode_emptyChildrenAllowed() {
        var node = new ParagraphNode(List.of());
        assertTrue(node.getChildren().isEmpty());
    }

    @Test
    void tableNode_createsWithHeadersAndRows() {
        var headers = List.of("Name", "Value");
        var row = List.<DocNode>of(new TextNode("foo"), new TextNode("bar"));
        var node = new TableNode(headers, List.of(row));
        assertEquals(headers, node.getHeaders());
        assertEquals(1, node.getRows().size());
        assertEquals("table", node.getType());
    }

    @Test
    void tableNode_rejectsNullHeaders() {
        assertThrows(NullPointerException.class,
            () -> new TableNode(null, List.of()));
    }

    @Test
    void tableNode_rejectsNullRows() {
        assertThrows(NullPointerException.class,
            () -> new TableNode(List.of(), null));
    }

    @Test
    void listNode_ordered() {
        var items = List.<DocNode>of(new TextNode("one"), new TextNode("two"));
        var node = new ListNode(true, items);
        assertTrue(node.isOrdered());
        assertEquals(2, node.getItems().size());
        assertEquals("list", node.getType());
    }

    @Test
    void listNode_unordered() {
        var node = new ListNode(false, List.of());
        assertFalse(node.isOrdered());
    }

    @Test
    void listNode_rejectsNullItems() {
        assertThrows(NullPointerException.class, () -> new ListNode(true, null));
    }

    @Test
    void imageNode_withLocalPath() {
        var node = new ImageNode("https://example.com/img.png", "alt text", "/cache/img.png");
        assertEquals("https://example.com/img.png", node.getUrl());
        assertEquals("alt text", node.getAlt());
        assertEquals("/cache/img.png", node.getLocalPath());
        assertEquals("image", node.getType());
    }

    @Test
    void imageNode_nullLocalPath() {
        var node = new ImageNode("https://example.com/img.png", "alt", null);
        assertNull(node.getLocalPath());
    }

    @Test
    void linkNode_createsWithUrlAndChildren() {
        var child = new TextNode("click me");
        var node = new LinkNode("https://example.com", List.of(child));
        assertEquals("https://example.com", node.getUrl());
        assertEquals(1, node.getChildren().size());
        assertEquals("link", node.getType());
    }

    @Test
    void linkNode_rejectsNullUrl() {
        assertThrows(NullPointerException.class,
            () -> new LinkNode(null, List.of()));
    }

    @Test
    void dividerNode_creates() {
        var node = new DividerNode();
        assertEquals("divider", node.getType());
    }

    @Test
    void sectionNode_createsWithTitleAndChildren() {
        var child = new TextNode("content");
        var node = new SectionNode("Section1", List.of(child));
        assertEquals("Section1", node.getTitle());
        assertEquals(1, node.getChildren().size());
        assertEquals("section", node.getType());
    }

    @Test
    void sectionNode_rejectsNullTitle() {
        assertThrows(NullPointerException.class,
            () -> new SectionNode(null, List.of()));
    }

    @Test
    void textNode_creates() {
        var node = new TextNode("hello world");
        assertEquals("hello world", node.getText());
        assertEquals("text", node.getType());
    }

    @Test
    void textNode_rejectsNull() {
        assertThrows(NullPointerException.class, () -> new TextNode(null));
    }

    @Test
    void styledTextNode_createsWithStyle() {
        var node = new StyledTextNode("bold text", TextStyle.BOLD);
        assertEquals("bold text", node.getText());
        assertEquals(TextStyle.BOLD, node.getStyle());
        assertEquals("styled_text", node.getType());
    }

    @Test
    void styledTextNode_rejectsNullText() {
        assertThrows(NullPointerException.class,
            () -> new StyledTextNode(null, TextStyle.NORMAL));
    }

    @Test
    void styledTextNode_rejectsNullStyle() {
        assertThrows(NullPointerException.class,
            () -> new StyledTextNode("text", null));
    }

    @Test
    void imageInlineNode_creates() {
        var node = new ImageInlineNode("https://example.com/icon.png", "icon");
        assertEquals("https://example.com/icon.png", node.getUrl());
        assertEquals("icon", node.getAlt());
        assertEquals("image_inline", node.getType());
    }

    @Test
    void imageInlineNode_rejectsNullUrl() {
        assertThrows(NullPointerException.class,
            () -> new ImageInlineNode(null, "alt"));
    }

    @Test
    void textStyle_normal() {
        var s = TextStyle.NORMAL;
        assertFalse(s.bold());
        assertFalse(s.italic());
        assertFalse(s.underline());
        assertFalse(s.strikethrough());
        assertNull(s.color());
    }

    @Test
    void textStyle_bold() {
        var s = TextStyle.BOLD;
        assertTrue(s.bold());
        assertNull(s.color());
    }

    @Test
    void textStyle_customColor() {
        var s = new TextStyle(false, false, false, false, "#FFAA00");
        assertEquals("#FFAA00", s.color());
    }

    // --- Visitor Tests ---

    @Test
    void docNode_acceptVisitor_visitsCorrectNodeType() {
        var textNode = new TextNode("hello");
        var result = textNode.accept(new DocNodeVisitor<>() {
            @Override public String visitHeading(HeadingNode node) { return "heading"; }
            @Override public String visitParagraph(ParagraphNode node) { return "paragraph"; }
            @Override public String visitTable(TableNode node) { return "table"; }
            @Override public String visitList(ListNode node) { return "list"; }
            @Override public String visitImage(ImageNode node) { return "image"; }
            @Override public String visitLink(LinkNode node) { return "link"; }
            @Override public String visitDivider(DividerNode node) { return "divider"; }
            @Override public String visitSection(SectionNode node) { return "section"; }
            @Override public String visitText(TextNode node) { return "text"; }
            @Override public String visitStyledText(StyledTextNode node) { return "styled_text"; }
            @Override public String visitImageInline(ImageInlineNode node) { return "image_inline"; }
        });
        assertEquals("text", result);
    }

    @Test
    void docNode_acceptVisitor_allNodeTypes() {
        List<DocNode> nodes = List.of(
            new HeadingNode(1, List.of(new TextNode("h"))),
            new ParagraphNode(List.of(new TextNode("p"))),
            new TableNode(List.of("h"), List.of(List.of(new TextNode("c")))),
            new ListNode(false, List.of(new TextNode("i"))),
            new ImageNode("u", "a", null),
            new LinkNode("u", List.of(new TextNode("l"))),
            new DividerNode(),
            new SectionNode("s", List.of(new TextNode("c"))),
            new TextNode("t"),
            new StyledTextNode("st", TextStyle.NORMAL),
            new ImageInlineNode("u", "a")
        );

        for (var node : nodes) {
            var result = node.accept(new DocNodeVisitor<>() {
                @Override public String visitHeading(HeadingNode n) { return n.getType(); }
                @Override public String visitParagraph(ParagraphNode n) { return n.getType(); }
                @Override public String visitTable(TableNode n) { return n.getType(); }
                @Override public String visitList(ListNode n) { return n.getType(); }
                @Override public String visitImage(ImageNode n) { return n.getType(); }
                @Override public String visitLink(LinkNode n) { return n.getType(); }
                @Override public String visitDivider(DividerNode n) { return n.getType(); }
                @Override public String visitSection(SectionNode n) { return n.getType(); }
                @Override public String visitText(TextNode n) { return n.getType(); }
                @Override public String visitStyledText(StyledTextNode n) { return n.getType(); }
                @Override public String visitImageInline(ImageInlineNode n) { return n.getType(); }
            });
            assertEquals(node.getType(), result,
                "visitor should return the correct type for " + node.getClass().getSimpleName());
        }
    }

    // --- Document Tests ---

    @Test
    void document_createsWithAllFields() {
        var content = List.<DocNode>of(new TextNode("hello"));
        var doc = new Document("Title", "Mod", "https://example.com", content);
        assertEquals("Title", doc.title());
        assertEquals("Mod", doc.sourceMod());
        assertEquals("https://example.com", doc.sourceUrl());
        assertEquals(content, doc.content());
    }

    @Test
    void document_nullableFieldsCanBeNull() {
        var content = List.<DocNode>of();
        var doc = new Document("Title", null, null, content);
        assertNull(doc.sourceMod());
        assertNull(doc.sourceUrl());
    }

    @Test
    void document_equality() {
        var content = List.<DocNode>of(new TextNode("a"));
        var d1 = new Document("T", "M", "U", content);
        var d2 = new Document("T", "M", "U", content);
        assertEquals(d1, d2);
        assertEquals(d1.hashCode(), d2.hashCode());
    }

    @Test
    void document_notEqualWithDifferentContent() {
        var d1 = new Document("T", null, null, List.of(new TextNode("a")));
        var d2 = new Document("T", null, null, List.of(new TextNode("b")));
        assertNotEquals(d1, d2);
    }

    @Test
    void document_emptyContentAllowed() {
        var doc = new Document("T", null, null, List.of());
        assertTrue(doc.content().isEmpty());
    }

    // --- Boundary Tests ---

    @Test
    void tableNode_emptyHeadersAllowed() {
        var node = new TableNode(List.of(), List.of(List.of(new TextNode("cell"))));
        assertTrue(node.getHeaders().isEmpty());
        assertEquals(1, node.getRows().size());
    }

    @Test
    void tableNode_emptyRowsAllowed() {
        var node = new TableNode(List.of("h1"), List.of());
        assertTrue(node.getRows().isEmpty());
    }

    @Test
    void linkNode_emptyChildrenAllowed() {
        var node = new LinkNode("https://example.com", List.of());
        assertTrue(node.getChildren().isEmpty());
    }

    @Test
    void sectionNode_emptyChildrenAllowed() {
        var node = new SectionNode("title", List.of());
        assertTrue(node.getChildren().isEmpty());
    }

    @Test
    void headingNode_negativeLevelAllowed() {
        var node = new HeadingNode(-1, List.of(new TextNode("text")));
        assertEquals(-1, node.getLevel());
    }

    @Test
    void headingNode_zeroLevelAllowed() {
        var node = new HeadingNode(0, List.of(new TextNode("text")));
        assertEquals(0, node.getLevel());
    }

    @Test
    void dividerNode_allInstancesEqual() {
        assertEquals(new DividerNode(), new DividerNode());
        assertEquals(new DividerNode().hashCode(), new DividerNode().hashCode());
    }

    @Test
    void imageNode_rejectsNullUrl() {
        assertThrows(NullPointerException.class,
            () -> new ImageNode(null, "alt", null));
    }

    @Test
    void imageNode_rejectsNullAlt() {
        assertThrows(NullPointerException.class,
            () -> new ImageNode("url", null, null));
    }

    @Test
    void imageInlineNode_rejectsNullAlt() {
        assertThrows(NullPointerException.class,
            () -> new ImageInlineNode("url", null));
    }

    @Test
    void textStyle_emptyColorString() {
        var s = new TextStyle(false, false, false, false, "");
        assertEquals("", s.color());
    }

    @Test
    void textNode_unicodeContent() {
        var node = new TextNode("娜迦鳞片 🐉 日本語");
        assertEquals("娜迦鳞片 🐉 日本語", node.getText());
    }

    @Test
    void paragraphNode_largeChildren() {
        var children = new java.util.ArrayList<DocNode>();
        for (int i = 0; i < 1000; i++) {
            children.add(new TextNode("word" + i));
        }
        var node = new ParagraphNode(children);
        assertEquals(1000, node.getChildren().size());
    }
}
