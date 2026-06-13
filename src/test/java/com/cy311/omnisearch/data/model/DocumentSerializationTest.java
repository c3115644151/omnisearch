package com.cy311.omnisearch.data.model;

import com.cy311.omnisearch.data.model.document.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentSerializationTest {

    private Gson gson;

    @BeforeEach
    void setUp() {
        gson = new GsonBuilder()
            .registerTypeAdapterFactory(new DocNodeAdapterFactory())
            .create();
    }

    @Test
    void serialize_deserialize_textNode() {
        var original = new TextNode("hello world");
        var json = gson.toJson(original, DocNode.class);
        var restored = gson.fromJson(json, DocNode.class);
        assertEquals(original, restored);
    }

    @Test
    void serialize_deserialize_headingNode() {
        var original = new HeadingNode(2, List.of(new TextNode("title")));
        var json = gson.toJson(original, DocNode.class);
        var restored = gson.fromJson(json, DocNode.class);
        assertEquals(original, restored);
    }

    @Test
    void serialize_deserialize_paragraphNode() {
        var original = new ParagraphNode(List.of(
            new TextNode("hello"),
            new StyledTextNode("bold", TextStyle.BOLD)
        ));
        var json = gson.toJson(original, DocNode.class);
        var restored = gson.fromJson(json, DocNode.class);
        assertEquals(original, restored);
    }

    @Test
    void serialize_deserialize_tableNode() {
        var headers = List.of("Name", "Value");
        var rows = List.<List<DocNode>>of(
            List.of(new TextNode("a"), new TextNode("1")),
            List.of(new TextNode("b"), new TextNode("2"))
        );
        var original = new TableNode(headers, rows);
        var json = gson.toJson(original, DocNode.class);
        var restored = gson.fromJson(json, DocNode.class);
        assertEquals(original, restored);
    }

    @Test
    void serialize_deserialize_listNode() {
        var original = new ListNode(true, List.of(
            new TextNode("item1"),
            new TextNode("item2")
        ));
        var json = gson.toJson(original, DocNode.class);
        var restored = gson.fromJson(json, DocNode.class);
        assertEquals(original, restored);
    }

    @Test
    void serialize_deserialize_imageNode() {
        var original = new ImageNode("https://example.com/img.png", "alt text", "/local/path.png");
        var json = gson.toJson(original, DocNode.class);
        var restored = gson.fromJson(json, DocNode.class);
        assertEquals(original, restored);
    }

    @Test
    void serialize_deserialize_imageNode_nullLocalPath() {
        var original = new ImageNode("https://example.com/img.png", "alt", null);
        var json = gson.toJson(original, DocNode.class);
        var restored = gson.fromJson(json, DocNode.class);
        assertEquals(original, restored);
    }

    @Test
    void serialize_deserialize_linkNode() {
        var original = new LinkNode("https://example.com", List.of(new TextNode("click")));
        var json = gson.toJson(original, DocNode.class);
        var restored = gson.fromJson(json, DocNode.class);
        assertEquals(original, restored);
    }

    @Test
    void serialize_deserialize_dividerNode() {
        var original = new DividerNode();
        var json = gson.toJson(original, DocNode.class);
        var restored = gson.fromJson(json, DocNode.class);
        assertEquals(original, restored);
    }

    @Test
    void serialize_deserialize_sectionNode() {
        var original = new SectionNode("My Section", List.of(
            new ParagraphNode(List.of(new TextNode("content")))
        ));
        var json = gson.toJson(original, DocNode.class);
        var restored = gson.fromJson(json, DocNode.class);
        assertEquals(original, restored);
    }

    @Test
    void serialize_deserialize_styledTextNode() {
        var original = new StyledTextNode("bold text", TextStyle.BOLD);
        var json = gson.toJson(original, DocNode.class);
        var restored = gson.fromJson(json, DocNode.class);
        assertEquals(original, restored);
    }

    @Test
    void serialize_deserialize_imageInlineNode() {
        var original = new ImageInlineNode("https://example.com/icon.png", "icon");
        var json = gson.toJson(original, DocNode.class);
        var restored = gson.fromJson(json, DocNode.class);
        assertEquals(original, restored);
    }

    @Test
    void serialize_deserialize_document() {
        var doc = new Document(
            "Test Title",
            "TestMod",
            "https://example.com/item/1",
            List.of(
                new HeadingNode(1, List.of(new TextNode("Heading"))),
                new ParagraphNode(List.of(
                    new TextNode("Some "),
                    new StyledTextNode("styled", TextStyle.BOLD),
                    new TextNode(" text.")
                )),
                new TableNode(
                    List.of("A", "B"),
                    List.of(List.of(new TextNode("1"), new TextNode("2")))
                ),
                new DividerNode(),
                new LinkNode("https://example.com", List.of(new TextNode("link")))
            )
        );

        var json = gson.toJson(doc);
        var restored = gson.fromJson(json, Document.class);
        assertEquals(doc, restored);
    }

    @Test
    void serialize_deserialize_document_nullableFields() {
        var doc = new Document(
            "Title", null, null,
            List.of(new TextNode("content"))
        );
        var json = gson.toJson(doc);
        var restored = gson.fromJson(json, Document.class);
        assertEquals(doc, restored);
    }

    @Test
    void serialization_roundTrip_preservesTypeDiscriminator() {
        var json = gson.toJson(new TextNode("hello"), DocNode.class);
        assertTrue(json.contains("\"type\""));
        assertTrue(json.contains("\"text\""));
    }

    @Test
    void deserialization_unknownType_throws() {
        var json = "{\"type\":\"unknown_type\",\"text\":\"hello\"}";
        assertThrows(com.google.gson.JsonParseException.class,
            () -> gson.fromJson(json, DocNode.class));
    }

    @Test
    void deserialization_missingType_throws() {
        var json = "{\"text\":\"hello\"}";
        assertThrows(com.google.gson.JsonParseException.class,
            () -> gson.fromJson(json, DocNode.class));
    }
}
