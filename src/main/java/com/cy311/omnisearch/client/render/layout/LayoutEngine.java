package com.cy311.omnisearch.client.render.layout;

import com.cy311.omnisearch.data.model.document.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-Java layout engine that converts a {@link Document} (DocNode tree)
 * into a list of positioned {@link LayoutNode}s.
 * <p>
 * No MC dependency — can be unit tested independently.
 */
public class LayoutEngine {

    private static final int PARAGRAPH_SPACING = 8;
    private static final int PARAGRAPH_INDENT = 0;
    private static final int HEADING_SPACING = 6;
    private static final int LIST_INDENT = 15;
    private static final int TABLE_PADDING = 4;
    private static final int IMAGE_PLACEHOLDER_W = 64;
    private static final int IMAGE_PLACEHOLDER_H = 48;
    private static final int DIVIDER_HEIGHT = 10;

    private final FontMetrics metrics;
    private int contentX;
    private int currentY;
    private int contentWidth;

    // Inline layout state
    private int inlineCursorX;
    private int inlineBaseY;
    private int inlineLineHeight;

    private record InlineFragment(
        LayoutType type,
        @Nullable String text,
        @Nullable String imageUrl,
        @Nullable String alt,
        boolean bold,
        int color,
        @Nullable String linkUrl
    ) {}

    private record PendingInline(InlineFragment fragment, int width, int height, int advance) {}

    public LayoutEngine(FontMetrics metrics, int x, int y, int width) {
        this.metrics = metrics;
        this.contentX = x;
        this.currentY = y;
        this.contentWidth = width;
        this.inlineCursorX = x;
        this.inlineBaseY = y;
        this.inlineLineHeight = metrics.lineHeight();
    }

    /**
     * Computes layout for the entire document.
     */
    public List<LayoutNode> layout(Document doc) {
        List<LayoutNode> result = new ArrayList<>();
        for (DocNode node : doc.content()) {
            List<LayoutNode> nodes = layoutNode(node);
            result.addAll(nodes);
        }
        return result;
    }

    /**
     * Returns the total used height from the starting Y position.
     */
    public int getHeight() {
        return currentY;
    }

    // ── Node layout dispatch ──

    private List<LayoutNode> layoutNode(DocNode node) {
        if (node instanceof HeadingNode hn) {
            return List.of(layoutHeading(hn));
        }
        if (node instanceof ParagraphNode pn) {
            return List.of(layoutParagraph(pn));
        }
        if (node instanceof ImageNode im) {
            return List.of(layoutImage(im));
        }
        if (node instanceof TableNode tn) {
            return List.of(layoutTable(tn));
        }
        if (node instanceof ListNode ln) {
            return List.of(layoutList(ln));
        }
        if (node instanceof DividerNode) {
            return List.of(layoutDivider());
        }
        if (node instanceof TextNode tn) {
            return List.of(layoutInlineText(tn));
        }
        if (node instanceof StyledTextNode stn) {
            return List.of(layoutStyledText(stn));
        }
        if (node instanceof ImageInlineNode iin) {
            return List.of(layoutInlineImage(iin));
        }
        if (node instanceof LinkNode ln) {
            return List.of(layoutLink(ln));
        }
        if (node instanceof SectionNode sn) {
            return layoutSection(sn);
        }
        // Fallback: render as text
        return List.of();
    }

    // ── Block-level layout ──

    private LayoutNode layoutHeading(HeadingNode node) {
        String headingText = extractText(node);
        int level = node.getLevel();
        float scale = level == 1 ? 1.5f : level == 2 ? 1.2f : 1.0f;
        int h = (int) (metrics.lineHeight() * scale) + HEADING_SPACING;
        LayoutNode ln = new LayoutNode(LayoutType.HEADING, headingText)
            .at(contentX, currentY, contentWidth, h)
            .withHeadingLevel(level);
        currentY += h;
        return ln;
    }

    private List<LayoutNode> layoutSection(SectionNode node) {
        List<LayoutNode> result = new ArrayList<>();
        // Title as heading level 2
        LayoutNode heading = new LayoutNode(LayoutType.HEADING, node.getTitle())
            .at(contentX, currentY, contentWidth, (int)(metrics.lineHeight() * 1.2f) + HEADING_SPACING)
            .withHeadingLevel(2);
        currentY += (int)(metrics.lineHeight() * 1.2f) + HEADING_SPACING;
        result.add(heading);

        // Children with indent
        int savedX = contentX;
        int savedW = contentWidth;
        contentX += 4;
        contentWidth -= 4;
        inlineCursorX = contentX;
        inlineBaseY = currentY;

        for (DocNode child : node.getChildren()) {
            for (LayoutNode cn : layoutNode(child)) {
                result.add(cn);
                currentY += cn.h;
            }
        }

        contentX = savedX;
        contentWidth = savedW;
        return result;
    }

    private LayoutNode layoutParagraph(ParagraphNode node) {
        LayoutNode para = new LayoutNode(LayoutType.PARAGRAPH);
        para.x = contentX + PARAGRAPH_INDENT;
        para.y = currentY;

        int paragraphWidth = Math.max(1, contentWidth - PARAGRAPH_INDENT);
        List<InlineFragment> fragments = collectInlineFragments(node.getChildren(), null);
        int usedHeight = layoutInlineFragmentsIntoParagraph(para, fragments, para.x, currentY, paragraphWidth);
        para.h = usedHeight + PARAGRAPH_SPACING;
        para.w = paragraphWidth;
        currentY += para.h;
        return para;
    }

    private LayoutNode layoutInline(String text, boolean styled) {
        return layoutInline(text, styled, false, -1);
    }

    private LayoutNode layoutInline(String text, boolean styled, boolean bold, int color) {
        int maxW = contentX + contentWidth - inlineCursorX;
        if (maxW <= 0) {
            inlineBaseY += inlineLineHeight;
            inlineCursorX = contentX;
            maxW = contentWidth;
        }

        String displayText = text;
        int textW = metrics.textWidth(displayText);
        int advW = textW;

        // Check if we need to wrap (handle first word overflow too)
        if (inlineCursorX + textW > contentX + contentWidth && (inlineCursorX > contentX || textW > contentWidth)) {
            inlineBaseY += inlineLineHeight;
            inlineCursorX = contentX;
            // Re-check if it fits on new line
            if (textW > contentWidth) {
                advW = contentWidth - 2;
            }
        }

        LayoutType type = styled ? LayoutType.STYLED_TEXT : LayoutType.TEXT;
        LayoutNode ln = new LayoutNode(type, displayText);
        ln.at(inlineCursorX, inlineBaseY, advW, inlineLineHeight);
        if (bold) ln = ln.withBold(true);
        if (color != -1) ln = ln.withColor(color);
        inlineCursorX += advW;
        return ln;
    }

    private LayoutNode layoutImage(ImageNode node) {
        int imgW = IMAGE_PLACEHOLDER_W;
        int imgH = IMAGE_PLACEHOLDER_H;
        String alt = node.getAlt();
        int altH = alt.isEmpty() ? 0 : metrics.lineHeight() + 1;
        int totalH = Math.max(imgH, alt.isEmpty() ? 0 : imgH + altH) + PARAGRAPH_SPACING;

        LayoutNode ln = new LayoutNode(LayoutType.IMAGE, null, node.getUrl(), null, node.getAlt());
        ln.at(contentX, currentY, imgW, totalH);
        currentY += totalH;
        return ln;
    }

    private LayoutNode layoutInlineImage(ImageInlineNode node) {
        int iconSize = Math.max(1, inlineLineHeight - 1);
        if (inlineCursorX + iconSize > contentX + contentWidth && inlineCursorX > contentX) {
            inlineBaseY += inlineLineHeight;
            inlineCursorX = contentX;
        }
        LayoutNode ln = new LayoutNode(LayoutType.INLINE_IMAGE, null, node.getUrl(), null, node.getAlt());
        ln.at(inlineCursorX, inlineBaseY, iconSize, iconSize);
        inlineCursorX += iconSize + 1;
        return ln;
    }

    private LayoutNode layoutLink(LinkNode node) {
        LayoutNode para = new LayoutNode(LayoutType.PARAGRAPH);
        para.x = contentX;
        para.y = currentY;
        int usedHeight = layoutInlineFragmentsIntoParagraph(
            para,
            collectInlineFragments(List.of(node), null),
            contentX,
            currentY,
            contentWidth
        );
        para.w = contentWidth;
        para.h = usedHeight + PARAGRAPH_SPACING;
        currentY += para.h;
        return para;
    }

    private LayoutNode layoutInlineLink(LinkNode node) {
        return layoutLink(node); // Same logic, just different parent handling
    }

    private LayoutNode layoutTable(TableNode node) {
        List<String> headers = node.getHeaders();
        List<List<DocNode>> rows = node.getRows();
        int colCount = Math.max(headers != null ? headers.size() : 0,
            rows != null && !rows.isEmpty() ? rows.getFirst().size() : 1);
        if (colCount == 0) colCount = 1;

        int colW = (contentWidth - TABLE_PADDING * (colCount - 1)) / colCount;
        int rowH = metrics.lineHeight() + TABLE_PADDING * 2;

        LayoutNode table = new LayoutNode(LayoutType.TABLE);
        table.at(contentX, currentY, contentWidth, 0);

        int headerY = currentY;
        if (headers != null) {
            for (int i = 0; i < headers.size() && i < colCount; i++) {
                String h = headers.get(i);
                LayoutNode cell = new LayoutNode(LayoutType.TEXT, h)
                    .withIsHeader(true);
                cell.at(contentX + i * (colW + TABLE_PADDING), headerY, colW, rowH);
                table.add(cell);
            }
            currentY += rowH;
        }

        if (rows != null) {
            for (List<DocNode> row : rows) {
                int maxRowH = rowH;
                LayoutNode[] rowCells = new LayoutNode[Math.min(row.size(), colCount)];
                for (int i = 0; i < row.size() && i < colCount; i++) {
                    DocNode cellNode = row.get(i);
                    List<DocNode> cellChildren = extractCellChildren(cellNode);
                    List<InlineFragment> fragments = collectInlineFragments(cellChildren, null);
                    boolean hasIcon = fragments.stream().anyMatch(f -> f.type == LayoutType.INLINE_IMAGE);

                    LayoutNode cell;
                    if (hasIcon) {
                        cell = new LayoutNode(LayoutType.PARAGRAPH);
                        cell.at(contentX + i * (colW + TABLE_PADDING), currentY, colW, rowH);
                        layoutTableCellContent(cell, fragments);
                    } else {
                        String cellText = extractTextFromFragments(fragments);
                        cell = new LayoutNode(LayoutType.TEXT, cellText);
                        cell.at(contentX + i * (colW + TABLE_PADDING), currentY, colW, rowH);
                    }
                    maxRowH = Math.max(maxRowH, Math.max(cell.h, rowH));
                    rowCells[i] = cell;
                }
                // Normalize row heights
                for (LayoutNode rc : rowCells) {
                    if (rc != null) {
                        rc.h = maxRowH;
                        table.add(rc);
                    }
                }
                currentY += maxRowH;
            }
        }

        table.h = currentY - table.y + PARAGRAPH_SPACING;
        currentY += PARAGRAPH_SPACING;
        return table;
    }

    /**
     * Lays out cell content (text and inline images) in a single line,
     * populating the cell's inlineChildren.
     */
    private void layoutTableCellContent(LayoutNode cell, List<InlineFragment> fragments) {
        int x = cell.x;
        int y = cell.y + Math.max(0, (cell.h - Math.max(1, metrics.lineHeight())) / 2);
        int maxH = Math.max(1, metrics.lineHeight());
        for (InlineFragment fragment : fragments) {
            if (fragment.type == LayoutType.INLINE_IMAGE) {
                int iconSize = Math.max(1, metrics.lineHeight() - 1);
                int advance = iconSize + 1;
                LayoutNode inline = new LayoutNode(LayoutType.INLINE_IMAGE, null, fragment.imageUrl, fragment.linkUrl, fragment.alt)
                    .at(x, y, iconSize, iconSize);
                cell.addInline(inline);
                x += advance;
                maxH = Math.max(maxH, iconSize);
            } else if (fragment.text != null && !fragment.text.isEmpty()) {
                int textW = metrics.textWidth(fragment.text);
                LayoutNode inline = new LayoutNode(
                    fragment.type == LayoutType.STYLED_TEXT ? LayoutType.STYLED_TEXT : LayoutType.INLINE_TEXT,
                    fragment.text, null, fragment.linkUrl, null)
                    .at(x, y, textW, Math.max(1, metrics.lineHeight()));
                if (fragment.bold) inline.withBold(true);
                if (fragment.color != -1) inline.withColor(fragment.color);
                cell.addInline(inline);
                x += textW;
            }
        }
        cell.w = Math.max(cell.w, x - cell.x);
        cell.h = Math.max(cell.h, maxH);
    }

    /** Concatenates text from inline fragments for pure-text layout. */
    private static String extractTextFromFragments(List<InlineFragment> fragments) {
        StringBuilder sb = new StringBuilder();
        for (InlineFragment f : fragments) {
            if (f.text != null) sb.append(f.text);
        }
        return sb.toString().trim();
    }

    /** Extracts children from a cell DocNode for layout. */
    private static List<DocNode> extractCellChildren(DocNode cellNode) {
        if (cellNode instanceof ParagraphNode pn) {
            return pn.getChildren();
        }
        return List.of(cellNode);
    }

    private LayoutNode layoutList(ListNode node) {
        LayoutNode listBox = new LayoutNode(LayoutType.LIST);
        listBox.at(contentX, currentY, contentWidth, 0);

        List<DocNode> items = node.getItems();
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                DocNode item = items.get(i);

                // Bullet marker
                String marker = node.isOrdered() ? (i + 1) + ". " : "• ";
                LayoutNode markerNode = new LayoutNode(LayoutType.TEXT, marker);
                markerNode.at(contentX, currentY, metrics.textWidth(marker), metrics.lineHeight());

                // Item text
                int textX = contentX + LIST_INDENT;
                inlineCursorX = textX;
                inlineBaseY = currentY;
                inlineLineHeight = metrics.lineHeight();

                String itemText = extractText(item);
                LayoutNode textNode = new LayoutNode(LayoutType.TEXT, itemText);
                int textW = Math.min(metrics.textWidth(itemText), contentWidth - LIST_INDENT);
                textNode.at(textX, currentY, textW, metrics.lineHeight());

                int itemH = metrics.lineHeight() + 2;
                listBox.add(markerNode);
                listBox.add(textNode);
                currentY += itemH;
            }
        }

        listBox.h = currentY - listBox.y + PARAGRAPH_SPACING;
        currentY += PARAGRAPH_SPACING;
        return listBox;
    }

    private LayoutNode layoutDivider() {
        LayoutNode dn = new LayoutNode(LayoutType.DIVIDER);
        dn.at(contentX, currentY, contentWidth, DIVIDER_HEIGHT);
        currentY += DIVIDER_HEIGHT + PARAGRAPH_SPACING;
        return dn;
    }

    private LayoutNode layoutInlineText(TextNode node) {
        return layoutInline(node.getText(), false);
    }

    private LayoutNode layoutStyledText(StyledTextNode node) {
        TextStyle style = node.getStyle();
        boolean bold = style != null && style.bold();
        int color = -1;
        if (style != null && style.color() != null && !style.color().isEmpty()) {
            try {
                String hex = style.color().startsWith("#") ? style.color().substring(1) : style.color();
                color = (int) Long.parseLong(hex, 16);
                if (color <= 0xFFFFFF) color |= 0xFF000000; // add alpha if missing
            } catch (NumberFormatException ignored) {}
        }
        return layoutInline(node.getText(), true, bold, color);
    }

    private int layoutInlineFragmentsIntoParagraph(
        LayoutNode paragraph,
        List<InlineFragment> fragments,
        int startX,
        int startY,
        int maxWidth
    ) {
        if (fragments.isEmpty()) {
            return metrics.lineHeight();
        }

        List<PendingInline> line = new ArrayList<>();
        int lineX = startX;
        int lineY = startY;
        int lineHeight = Math.max(1, metrics.lineHeight());

        for (InlineFragment fragment : fragments) {
            if (fragment.type == LayoutType.INLINE_IMAGE) {
                int iconSize = Math.max(1, metrics.lineHeight() - 1);
                int advance = iconSize + 1;
                if (lineX + advance > startX + maxWidth && !line.isEmpty()) {
                    lineY = flushLine(paragraph, line, startX, lineY, lineHeight);
                    lineX = startX;
                    lineHeight = Math.max(1, metrics.lineHeight());
                }
                line.add(new PendingInline(fragment, iconSize, iconSize, advance));
                lineX += advance;
                lineHeight = Math.max(lineHeight, iconSize);
                continue;
            }

            String remaining = fragment.text != null ? fragment.text : "";
            while (!remaining.isEmpty()) {
                int available = (startX + maxWidth) - lineX;
                if (available <= 0 && !line.isEmpty()) {
                    lineY = flushLine(paragraph, line, startX, lineY, lineHeight);
                    lineX = startX;
                    lineHeight = Math.max(1, metrics.lineHeight());
                    continue;
                }

                int fitLength = maxFittingPrefixLength(remaining, Math.max(1, available));
                if (fitLength == 0) {
                    if (!line.isEmpty()) {
                        lineY = flushLine(paragraph, line, startX, lineY, lineHeight);
                        lineX = startX;
                        lineHeight = Math.max(1, metrics.lineHeight());
                        continue;
                    }
                    fitLength = 1;
                }

                String segment = remaining.substring(0, fitLength);
                int segmentWidth = metrics.textWidth(segment);
                line.add(new PendingInline(fragmentWithText(fragment, segment), segmentWidth, Math.max(1, metrics.lineHeight()), segmentWidth));
                lineX += segmentWidth;
                lineHeight = Math.max(lineHeight, Math.max(1, metrics.lineHeight()));
                remaining = remaining.substring(fitLength);

                if (!remaining.isEmpty()) {
                    lineY = flushLine(paragraph, line, startX, lineY, lineHeight);
                    lineX = startX;
                    lineHeight = Math.max(1, metrics.lineHeight());
                }
            }
        }

        if (!line.isEmpty()) {
            lineY = flushLine(paragraph, line, startX, lineY, lineHeight);
        }
        return Math.max(Math.max(1, metrics.lineHeight()), lineY - startY);
    }

    private int flushLine(LayoutNode paragraph, List<PendingInline> line, int lineStartX, int lineY, int lineHeight) {
        int x = lineStartX;
        for (PendingInline pending : line) {
            InlineFragment fragment = pending.fragment();
            int itemY = lineY + Math.max(0, lineHeight - pending.height());
            LayoutNode node = new LayoutNode(fragment.type, fragment.text, fragment.imageUrl, fragment.linkUrl, fragment.alt)
                .at(x, itemY, pending.width(), pending.height());
            if (fragment.bold) node.withBold(true);
            if (fragment.color != -1) node.withColor(fragment.color);
            paragraph.addInline(node);
            x += pending.advance();
        }
        line.clear();
        return lineY + Math.max(1, lineHeight);
    }

    private List<InlineFragment> collectInlineFragments(List<DocNode> nodes, @Nullable String inheritedLinkUrl) {
        List<InlineFragment> result = new ArrayList<>();
        for (DocNode node : nodes) {
            if (node instanceof TextNode tn) {
                if (!tn.getText().isEmpty()) {
                    result.add(new InlineFragment(LayoutType.TEXT, tn.getText(), null, null, false, -1, inheritedLinkUrl));
                }
            } else if (node instanceof StyledTextNode stn) {
                TextStyle style = stn.getStyle();
                boolean bold = style != null && style.bold();
                int color = resolveTextColor(style);
                if (!stn.getText().isEmpty()) {
                    result.add(new InlineFragment(LayoutType.STYLED_TEXT, stn.getText(), null, null, bold, color, inheritedLinkUrl));
                }
            } else if (node instanceof ImageInlineNode iin) {
                result.add(new InlineFragment(LayoutType.INLINE_IMAGE, null, iin.getUrl(), iin.getAlt(), false, -1, inheritedLinkUrl));
            } else if (node instanceof LinkNode ln) {
                result.addAll(collectInlineFragments(ln.getChildren(), ln.getUrl()));
            } else if (node instanceof ParagraphNode pn) {
                result.addAll(collectInlineFragments(pn.getChildren(), inheritedLinkUrl));
            }
        }
        return result;
    }

    private static InlineFragment fragmentWithText(InlineFragment fragment, String text) {
        return new InlineFragment(
            fragment.type,
            text,
            fragment.imageUrl,
            fragment.alt,
            fragment.bold,
            fragment.color,
            fragment.linkUrl
        );
    }

    private int maxFittingPrefixLength(String text, int maxWidth) {
        if (text.isEmpty()) return 0;
        if (metrics.textWidth(text) <= maxWidth) return text.length();
        int low = 1;
        int high = text.length();
        int best = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int width = metrics.textWidth(text.substring(0, mid));
            if (width <= maxWidth) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    private static int resolveTextColor(@Nullable TextStyle style) {
        if (style == null || style.color() == null || style.color().isEmpty()) {
            return -1;
        }
        try {
            String hex = style.color().startsWith("#") ? style.color().substring(1) : style.color();
            int color = (int) Long.parseLong(hex, 16);
            return color <= 0xFFFFFF ? color | 0xFF000000 : color;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    // ── Helpers ──

    private static String extractText(DocNode node) {
        if (node instanceof TextNode tn) return tn.getText();
        if (node instanceof StyledTextNode stn) return stn.getText();
        if (node instanceof HeadingNode hn) {
            return extractTextFromList(hn.getChildren());
        }
        StringBuilder sb = new StringBuilder();
        if (node instanceof ParagraphNode pn) {
            for (DocNode c : pn.getChildren()) sb.append(extractText(c));
        }
        if (node instanceof LinkNode ln) {
            for (DocNode c : ln.getChildren()) sb.append(extractText(c));
        }
        return sb.toString();
    }

    private static String extractTextFromList(List<DocNode> nodes) {
        StringBuilder sb = new StringBuilder();
        for (DocNode n : nodes) sb.append(extractText(n));
        return sb.toString();
    }
}
