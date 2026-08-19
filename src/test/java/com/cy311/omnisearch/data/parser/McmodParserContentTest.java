package com.cy311.omnisearch.data.parser;

import com.cy311.omnisearch.data.model.document.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class McmodParserContentTest {

    private final McmodParser parser = new McmodParser();

    // Helper: parse content HTML fragment inside a full page structure
    private List<DocNode> parseContent(String contentHtml) {
        String html = """
            <html><body>
            <div class="itemname"><h5>测试</h5></div>
            <div class="item-content common-text font14">
            %s
            </div>
            </body></html>
            """.formatted(contentHtml);
        var doc = parser.parseItemPage(html, "https://www.mcmod.cn/item/test.html");
        return doc.content();
    }

    @Test
    void headingParsing() {
        List<DocNode> content = parseContent("<h1>一级标题</h1><h2>二级标题</h2><h3>三级标题</h3>");

        assertEquals(3, content.size()); // no title heading in content anymore
        HeadingNode h1 = (HeadingNode) content.get(0);
        assertEquals(1, h1.getLevel());
        assertEquals("一级标题", ((TextNode) h1.getChildren().get(0)).getText());

        HeadingNode h2 = (HeadingNode) content.get(1);
        assertEquals(2, h2.getLevel());
        assertEquals("二级标题", ((TextNode) h2.getChildren().get(0)).getText());

        HeadingNode h3 = (HeadingNode) content.get(2);
        assertEquals(3, h3.getLevel());
        assertEquals("三级标题", ((TextNode) h3.getChildren().get(0)).getText());
    }

    @Test
    void paragraphWithText() {
        List<DocNode> content = parseContent("<p>这是一段文字。</p>");

        ParagraphNode para = (ParagraphNode) content.get(0);
        assertEquals(1, para.getChildren().size());
        assertEquals("这是一段文字。", ((TextNode) para.getChildren().get(0)).getText());
    }

    @Test
    void tableParsing() {
        List<DocNode> content = parseContent("""
            <table>
              <tr><th>属性</th><th>值</th></tr>
              <tr><td>类型</td><td>材料</td></tr>
              <tr><td>稀有度</td><td>普通</td></tr>
            </table>
            """);

        TableNode table = (TableNode) content.get(0);
        assertEquals(List.of("属性", "值"), table.getHeaders());
        assertEquals(2, table.getRows().size());
    }

    @Test
    void unorderedList() {
        List<DocNode> content = parseContent("<ul><li>第一项</li><li>第二项</li></ul>");

        ListNode list = (ListNode) content.get(0);
        assertFalse(list.isOrdered());
        assertEquals(2, list.getItems().size());
    }

    @Test
    void orderedList() {
        List<DocNode> content = parseContent("<ol><li>第一步</li><li>第二步</li></ol>");

        ListNode list = (ListNode) content.get(0);
        assertTrue(list.isOrdered());
        assertEquals(2, list.getItems().size());
    }

    @Test
    void linkNode() {
        List<DocNode> content = parseContent("<p>访问<a href=\"https://example.com\">示例</a>网站</p>");

        ParagraphNode para = (ParagraphNode) content.get(0);
        LinkNode link = (LinkNode) para.getChildren().stream()
            .filter(n -> n instanceof LinkNode)
            .findFirst().orElse(null);
        assertNotNull(link);
    }

    @Test
    void imageNode() {
        List<DocNode> content = parseContent("<p><img src=\"https://example.com/img.png\" alt=\"图片\"></p>");

        ParagraphNode para = (ParagraphNode) content.get(0);
        boolean hasImage = para.getChildren().stream().anyMatch(n -> n instanceof ImageInlineNode);
        assertTrue(hasImage);
    }

    @Test
    void dividerNode() {
        List<DocNode> content = parseContent("<hr>");

        boolean hasDivider = content.stream().anyMatch(n -> n instanceof DividerNode);
        assertTrue(hasDivider);
    }

    @Test
    void boldText() {
        List<DocNode> content = parseContent("<p><b>粗体文字</b></p>");

        ParagraphNode para = (ParagraphNode) content.get(0);
        StyledTextNode styled = (StyledTextNode) para.getChildren().get(0);
        assertEquals(TextStyle.BOLD, styled.getStyle());
        assertEquals("粗体文字", styled.getText());
    }

    @Test
    void strongText() {
        List<DocNode> content = parseContent("<p><strong>重要文字</strong></p>");

        ParagraphNode para = (ParagraphNode) content.get(0);
        StyledTextNode styled = (StyledTextNode) para.getChildren().get(0);
        assertEquals(TextStyle.BOLD, styled.getStyle());
        assertEquals("重要文字", styled.getText());
    }

    @Test
    void italicText() {
        List<DocNode> content = parseContent("<p><i>斜体文字</i></p>");

        ParagraphNode para = (ParagraphNode) content.get(0);
        StyledTextNode styled = (StyledTextNode) para.getChildren().get(0);
        assertEquals(TextStyle.ITALIC, styled.getStyle());
        assertEquals("斜体文字", styled.getText());
    }

    @Test
    void emphasizedText() {
        List<DocNode> content = parseContent("<p><em>强调文字</em></p>");

        ParagraphNode para = (ParagraphNode) content.get(0);
        StyledTextNode styled = (StyledTextNode) para.getChildren().get(0);
        assertEquals(TextStyle.ITALIC, styled.getStyle());
        assertEquals("强调文字", styled.getText());
    }

    @Test
    void coloredText() {
        List<DocNode> content = parseContent("<p><span style=\"color:#FFAA00\">橙色文字</span></p>");

        ParagraphNode para = (ParagraphNode) content.get(0);
        StyledTextNode styled = (StyledTextNode) para.getChildren().get(0);
        assertEquals("#FFAA00", styled.getStyle().color());
        assertTrue(styled.getStyle().bold() == false && styled.getStyle().italic() == false);
        assertEquals("橙色文字", styled.getText());
    }

    @Test
    void coloredText_rgbFunction() {
        // mcmod.cn uses rgb() for red warnings — previously dropped, now must be parsed
        List<DocNode> content = parseContent("<p><span style=\"color: rgb(255, 0, 0)\">红色警告</span></p>");

        ParagraphNode para = (ParagraphNode) content.get(0);
        StyledTextNode styled = (StyledTextNode) para.getChildren().get(0);
        assertEquals("#FF0000", styled.getStyle().color());
        assertEquals("红色警告", styled.getText());
    }

    @Test
    void coloredText_rgbaFunction() {
        List<DocNode> content = parseContent("<p><span style=\"color: rgba(0, 128, 0, 0.5)\">半透明绿</span></p>");

        ParagraphNode para = (ParagraphNode) content.get(0);
        StyledTextNode styled = (StyledTextNode) para.getChildren().get(0);
        assertEquals("#00800080", styled.getStyle().color());
    }

    @Test
    void coloredText_hslFunction() {
        List<DocNode> content = parseContent("<p><span style=\"color: hsl(0, 100%, 50%)\">红</span></p>");

        ParagraphNode para = (ParagraphNode) content.get(0);
        StyledTextNode styled = (StyledTextNode) para.getChildren().get(0);
        assertEquals("#FF0000", styled.getStyle().color());
    }

    @Test
    void coloredText_namedColor() {
        List<DocNode> content = parseContent("<p><span style=\"color: red\">命名红</span></p>");

        ParagraphNode para = (ParagraphNode) content.get(0);
        StyledTextNode styled = (StyledTextNode) para.getChildren().get(0);
        assertEquals("#FF0000", styled.getStyle().color());
    }

    @Test
    void mixedInlineContent() {
        List<DocNode> content = parseContent("<p>普通文字<b>加粗</b><i>斜体</i>结尾</p>");

        ParagraphNode para = (ParagraphNode) content.get(0);
        assertTrue(para.getChildren().size() >= 4);

        long boldCount = para.getChildren().stream()
            .filter(n -> n instanceof StyledTextNode && ((StyledTextNode) n).getStyle().bold())
            .count();
        long italicCount = para.getChildren().stream()
            .filter(n -> n instanceof StyledTextNode && ((StyledTextNode) n).getStyle().italic())
            .count();
        assertEquals(1, boldCount);
        assertEquals(1, italicCount);
    }

    @Test
    void headingH4H5H6() {
        List<DocNode> content = parseContent("<h4>四级</h4><h5>五级</h5><h6>六级</h6>");

        assertEquals(3, content.size());
        assertEquals(4, ((HeadingNode) content.get(0)).getLevel());
        assertEquals(5, ((HeadingNode) content.get(1)).getLevel());
        assertEquals(6, ((HeadingNode) content.get(2)).getLevel());
    }

    @Test
    void emptyParagraph_skipped() {
        List<DocNode> content = parseContent("<p></p><p>有内容</p>");
        assertEquals(1, content.size()); // only the non-empty paragraph
        assertInstanceOf(ParagraphNode.class, content.get(0));
    }

    @Test
    void spanWithoutStyle_isTextNode() {
        List<DocNode> content = parseContent("<p><span>无样式文本</span></p>");
        ParagraphNode para = (ParagraphNode) content.get(0);
        assertInstanceOf(TextNode.class, para.getChildren().get(0));
    }

    @Test
    void tableOnlyHeaderRow() {
        List<DocNode> content = parseContent("<table><tr><th>仅标题</th></tr></table>");
        TableNode table = (TableNode) content.get(0);
        assertEquals(List.of("仅标题"), table.getHeaders());
        assertTrue(table.getRows().isEmpty());
    }

    @Test
    void nestedList() {
        List<DocNode> content = parseContent("<ul><li>外层<li><ul><li>内层</li></ul></li></ul>");
        ListNode list = (ListNode) content.get(0);
        assertFalse(list.getItems().isEmpty());
    }

    @Test
    void imageBlockNode() {
        List<DocNode> content = parseContent("<img src=\"https://example.com/block.png\" alt=\"块级图片\">");
        boolean hasImage = content.stream().anyMatch(n -> n instanceof ImageNode);
        assertTrue(hasImage);
    }

    @Test
    void sectionHeading_notDuplicatedInChildren() {
        List<DocNode> content = parseContent("""
            <div>
              <h2>特性</h2>
              <p>特性描述</p>
            </div>
            """);
        SectionNode section = (SectionNode) content.get(0);
        assertEquals("特性", section.getTitle());
        assertFalse(section.getChildren().stream().anyMatch(
            n -> n instanceof HeadingNode), "heading should not appear in section children");
    }

    @Test
    void nullHtml() {
        var doc = parser.parseItemPage(null, "https://www.mcmod.cn/item/0.html");
        assertTrue(doc.content().isEmpty());
    }

    @Test
    void blankHtml() {
        var doc = parser.parseItemPage("", "https://www.mcmod.cn/item/0.html");
        assertTrue(doc.content().isEmpty());
    }

    @Test
    void svgIcon_parsedAsImageInlineNode() {
        List<DocNode> content = parseContent("""
            <p><strong>生命值：</strong>
            <span data-toggle="tooltip" data-original-title="100">
                <svg class="common-mcicon" aria-hidden="true">
                    <use xlink:href="#icon-health-full"></use>
                </svg>
            </span> × 50</p>""");

        assertFalse(content.isEmpty(), "should have content");
        ParagraphNode para = (ParagraphNode) content.get(0);
        assertFalse(para.getChildren().isEmpty(), "paragraph should have children");

        // Print children types for debugging
        for (DocNode child : para.getChildren()) {
            System.out.println("  child type: " + child.getClass().getSimpleName()
                + (child instanceof ImageInlineNode ? " url=" + ((ImageInlineNode)child).getUrl() : "")
                + (child instanceof TextNode ? " text=" + ((TextNode)child).getText() : ""));
        }

        boolean hasMcIcon = para.getChildren().stream()
            .anyMatch(n -> n instanceof ImageInlineNode
                && ((ImageInlineNode) n).getUrl() != null
                && ((ImageInlineNode) n).getUrl().startsWith("mc-icon://"));
        assertTrue(hasMcIcon, "SVG <use> should produce ImageInlineNode with mc-icon:// URL");
    }

    @Test
    void svgIcon_multipleIcons() {
        List<DocNode> content = parseContent("""
            <p><span data-toggle="tooltip">
                <svg class="common-mcicon"><use xlink:href="#icon-health-full"></use></svg>
                <svg class="common-mcicon"><use xlink:href="#icon-health-full"></use></svg>
                <svg class="common-mcicon"><use xlink:href="#icon-hunger-full"></use></svg>
            </span></p>""");

        assertFalse(content.isEmpty(), "should have content");
        assertInstanceOf(ParagraphNode.class, content.get(0), "first node should be paragraph");

        ParagraphNode para = (ParagraphNode) content.get(0);
        long mcIconCount = para.getChildren().stream()
            .filter(n -> n instanceof ImageInlineNode && ((ImageInlineNode) n).getUrl() != null
                && ((ImageInlineNode) n).getUrl().startsWith("mc-icon://"))
            .count();
        assertEquals(3, mcIconCount, "Should parse all 3 SVG icons");
    }

    @Test
    void lazyLoadImage_inTableCell_parsedAsImageNode_variantA_browserRenderedHtml() {
        // Variant A: browser JS has already replaced src with the real WebP URL.
        // This is what you see when inspecting the DOM in a browser.
        List<DocNode> content = parseContent("""
            <div class="table-scroll"><table class="table table-bordered text-nowrap"><tbody>
              <tr>
                <td style="word-break: break-all;"><span class="figure"><img alt="暗夜巫妖-第1张图片" class="lazy" src="https://i.mcmod.cn/editor/upload/20240424/1713897356_557759_yZAd.webp" data-src="https://i.mcmod.cn/editor/upload/20240424/1713897356_557759_yZAd.webp" data-error="//www.mcmod.cn/images/loadfail.gif" data-width="475" data-height="250" data-original="https://www.mcmod.cn/static/public/images/loading-colourful.gif" width="475" height="250"></span></td>
                <td><span class="figure"><img alt="暗夜巫妖-第2张图片" class="lazy" src="https://i.mcmod.cn/editor/upload/20240424/1713897813_557759_xuvp.webp" data-src="https://i.mcmod.cn/editor/upload/20240424/1713897813_557759_xuvp.webp" data-error="//www.mcmod.cn/images/loadfail.gif" data-original="https://www.mcmod.cn/static/public/images/loading-colourful.gif"></span></td>
              </tr>
            </tbody></table></div>
            """);

        long imageCount = countImagesRecursive(content);
        assertTrue(imageCount >= 2, "Variant A: Should find at least 2 ImageNodes with real WebP URL, found: " + imageCount
            + " content types: " + content.stream().map(n -> n.getClass().getSimpleName()).toList());
    }

    @Test
    void lazyLoadImage_inTableCell_parsedAsImageNode_variantB_serverOriginalHtml() {
        // Variant B: server-returned original HTML where src is the loading gif,
        // and the real WebP URL is in data-src. This is what the HTTP client receives.
        List<DocNode> content = parseContent("""
            <div class="table-scroll"><table class="table table-bordered text-nowrap"><tbody>
              <tr>
                <td style="word-break: break-all;"><span class="figure"><img alt="暗夜巫妖-第1张图片" class="lazy" src="https://www.mcmod.cn/static/public/images/loading-colourful.gif" data-src="https://i.mcmod.cn/editor/upload/20240424/1713897356_557759_yZAd.webp" data-error="//www.mcmod.cn/images/loadfail.gif" data-width="475" data-height="250" data-original="https://www.mcmod.cn/static/public/images/loading-colourful.gif" width="475" height="250"></span></td>
                <td><span class="figure"><img alt="暗夜巫妖-第2张图片" class="lazy" src="https://www.mcmod.cn/static/public/images/loading-colourful.gif" data-src="https://i.mcmod.cn/editor/upload/20240424/1713897813_557759_xuvp.webp" data-error="//www.mcmod.cn/images/loadfail.gif" data-original="https://www.mcmod.cn/static/public/images/loading-colourful.gif"></span></td>
              </tr>
            </tbody></table></div>
            """);

        long imageCount = countImagesRecursive(content);
        assertTrue(imageCount >= 2, "Variant B: Should find at least 2 ImageNodes with real WebP URL, found: " + imageCount
            + " content types: " + content.stream().map(n -> n.getClass().getSimpleName()).toList());
    }

    /** Recursively counts ImageNodes with editor/upload URL */
    private long countImagesRecursive(List<DocNode> nodes) {
        long count = 0;
        for (DocNode node : nodes) {
            if (node instanceof ImageNode im) {
                System.out.println("  found ImageNode: url=" + im.getUrl() + " w=" + im.getOrigWidth() + " h=" + im.getOrigHeight());
                if (im.getUrl().contains("editor/upload") && im.getUrl().endsWith(".webp")) {
                    count++;
                }
            } else if (node instanceof SectionNode sn) {
                count += countImagesRecursive(sn.getChildren());
            } else if (node instanceof TableNode tn) {
                for (List<DocNode> row : tn.getRows()) {
                    count += countImagesRecursive(row);
                }
            } else if (node instanceof ParagraphNode pn) {
                count += countImagesRecursive(pn.getChildren());
            }
        }
        return count;
    }

    @Test
    void lazyLoadImage_directInContent_parsedWithRealUrl() {
        // Simulates a lazy-loaded img directly in content (not in a table)
        List<DocNode> content = parseContent("""
            <img alt="巫妖壁垒-第1张图片" class="lazy" src="https://www.mcmod.cn/static/public/images/loading-colourful.gif" data-src="https://i.mcmod.cn/editor/upload/20200408/1586342720_79030_bCjC.webp" data-error="//www.mcmod.cn/images/loadfail.gif" data-original="https://www.mcmod.cn/static/public/images/loading-colourful.gif">
            """);

        for (DocNode node : content) {
            System.out.println("  content node: " + node.getClass().getSimpleName()
                + (node instanceof ImageNode im ? " url=" + im.getUrl() : ""));
        }

        assertFalse(content.isEmpty(), "should have content");
        ImageNode img = (ImageNode) content.stream()
            .filter(n -> n instanceof ImageNode)
            .findFirst()
            .orElse(null);
        assertNotNull(img, "Should find an ImageNode");
        assertTrue(img.getUrl().endsWith(".webp"), "URL should be the real WebP from data-src, got: " + img.getUrl());
        assertFalse(img.getUrl().contains("loading"), "URL should not be the loading placeholder");
    }

    @Test
    void lazyLoadImage_inParagraphSpanFigure_parsedWithRealUrl() {
        // Actual server-side HTML: <p><span class="figure"><img class="lazy" src="loading.gif" data-src="real.webp">
        List<DocNode> content = parseContent("""
            <p><span class="figure"><img class="lazy" src="//www.mcmod.cn/static/public/images/loading-colourful.gif" data-src="https://i.mcmod.cn/editor/upload/20260210/1770727137_1264075_piDy.webp" data-error="//www.mcmod.cn/images/loadfail.gif"></span></p>
            """);

        for (DocNode node : content) {
            System.out.println("  content node: " + node.getClass().getSimpleName()
                + (node instanceof ImageNode im ? " url=" + im.getUrl() : ""));
        }

        assertFalse(content.isEmpty(), "should have content");
        // Search recursively through paragraph children
        long imgCount = countImagesRecursive(content);
        assertTrue(imgCount > 0, "Should find at least 1 ImageNode with WebP URL, found: " + imgCount);
    }

    @Test
    void paragraphWithTextIndent_isMarked() {
        // mcmod.cn body paragraphs carry CSS text-indent:2em — must be captured so the
        // layout can render the first-line indent.
        List<DocNode> content = parseContent("<p style=\"text-indent: 2em; text-wrap: wrap;\">首行缩进的正文</p>");
        ParagraphNode para = (ParagraphNode) content.get(0);
        assertTrue(para.isFirstLineIndent());
    }

    @Test
    void paragraphWithoutTextIndent_isNotMarked() {
        List<DocNode> content = parseContent("<p>普通段落，无缩进</p>");
        ParagraphNode para = (ParagraphNode) content.get(0);
        assertFalse(para.isFirstLineIndent());
    }

    @Test
    void paragraphWithEditorTypedSpaces_isMarkedAndStripped() {
        // Editors type full-width spaces (U+3000) for indent — ASCII spaces are stripped by
        // the HTML parser, but ideographic spaces survive. MC renders them ~zero-width too,
        // so they must be converted to a real indent flag and stripped from the text.
        List<DocNode> content = parseContent("<p>　　编辑者手动空格缩进的正文</p>");
        ParagraphNode para = (ParagraphNode) content.get(0);
        assertTrue(para.isFirstLineIndent(), "typed full-width spaces must mark the paragraph as indented");
        TextNode first = (TextNode) para.getChildren().get(0);
        assertFalse(first.getText().startsWith("　"), "leading full-width spaces must be stripped from text");
        assertEquals("编辑者手动空格缩进的正文", first.getText());
    }

    @Test
    void paragraphWithSingleLeadingSpace_notMarked() {
        // A single leading space is usually incidental (e.g. " 【标题】" prefix) — not an indent
        List<DocNode> content = parseContent("<p> 单个空格开头不算缩进</p>");
        ParagraphNode para = (ParagraphNode) content.get(0);
        assertFalse(para.isFirstLineIndent());
    }

    @Test
    void commonTextTitle_parsedAsHeadingWithLevel() {
        // mcmod.cn encodes headings as <span class="common-text-title common-text-title-N">
        // inside <p>; N is the hierarchy level (1 = top section, 2 = subsection)
        List<DocNode> content = parseContent("""
            <p><span class="common-text-title common-text-title-1">基础介绍</span></p>
            <p><span class="common-text-title common-text-title-2">冲撞</span></p>
            """);
        assertEquals(2, content.size());
        assertInstanceOf(HeadingNode.class, content.get(0));
        HeadingNode h1 = (HeadingNode) content.get(0);
        assertEquals(1, h1.getLevel());
        assertEquals("基础介绍", ((TextNode) h1.getChildren().get(0)).getText());

        assertInstanceOf(HeadingNode.class, content.get(1));
        HeadingNode h2 = (HeadingNode) content.get(1);
        assertEquals(2, h2.getLevel());
        assertEquals("冲撞", ((TextNode) h2.getChildren().get(0)).getText());
    }

    @Test
    void paragraphWithTitleSpan_headingNotDuplicatedInBody() {
        // The title span must not ALSO appear as body text — the whole <p> becomes the heading
        List<DocNode> content = parseContent("<p><span class=\"common-text-title common-text-title-1\">能力</span>后接正文</p>");
        assertEquals(2, content.size());
        assertInstanceOf(HeadingNode.class, content.get(0));
        assertInstanceOf(ParagraphNode.class, content.get(1));
        // heading text is just the span text
        HeadingNode h = (HeadingNode) content.get(0);
        assertEquals("能力", ((TextNode) h.getChildren().get(0)).getText());
        // paragraph contains the remaining body text
        String body = ((ParagraphNode) content.get(1)).getChildren().stream()
            .filter(n -> n instanceof TextNode)
            .map(n -> ((TextNode) n).getText())
            .reduce("", String::concat);
        assertTrue(body.contains("后接正文"));
    }

    @Test
    void paragraphCenterAligned_parsed() {
        List<DocNode> content = parseContent("<p style=\"text-align: center;\">居中的标题说明</p>");
        ParagraphNode para = (ParagraphNode) content.get(0);
        assertEquals(ParagraphNode.Align.CENTER, para.getAlign());
    }

    @Test
    void paragraphLeftAligned_default() {
        List<DocNode> content = parseContent("<p>默认左对齐</p>");
        ParagraphNode para = (ParagraphNode) content.get(0);
        assertEquals(ParagraphNode.Align.NONE, para.getAlign());
    }

    @Test
    void fieldsetLegend_becomesHeadingThenContent() {
        List<DocNode> content = parseContent("""
            <fieldset><legend>资料标题</legend><p>资料正文内容</p></fieldset>
            """);
        assertEquals(2, content.size());
        assertInstanceOf(HeadingNode.class, content.get(0));
        HeadingNode heading = (HeadingNode) content.get(0);
        assertEquals(3, heading.getLevel());
        assertEquals("资料标题", ((TextNode) heading.getChildren().get(0)).getText());
        assertInstanceOf(ParagraphNode.class, content.get(1));
    }
}
