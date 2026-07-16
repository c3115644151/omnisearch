package com.cy311.omnisearch.client.render.layout;

import com.cy311.omnisearch.data.model.document.*;
import com.cy311.omnisearch.gui.theme.OmniTheme;
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

    private static final int DIVIDER_HEIGHT = 6;

    private final FontMetrics metrics;
    private int contentX;
    private int currentY;
    private int contentWidth;

    // Spacing values derived from lineHeight - scale with font size and UI scale
    private final int paragraphSpacing;
    private final int headingSpacing;
    private final int imageTopMargin;
    private final int imageBottomMargin;
    private final int tablePadding;
    private final int listIndent;

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
        int lh = metrics.lineHeight();
        this.paragraphSpacing = Math.max(2, lh / 3);
        this.headingSpacing = Math.max(2, lh / 3);
        this.imageTopMargin = Math.max(2, lh / 2);
        this.imageBottomMargin = Math.max(2, lh / 2);
        this.tablePadding = Math.max(1, lh / 4);
        this.listIndent = Math.max(8, lh * 3 / 2);
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
        int level = node.getLevel();
        float scale = level == 1 ? 1.5f : level == 2 ? 1.2f : 1.0f;
        int scaledLineHeight = (int) (metrics.lineHeight() * scale);

        LayoutNode heading = new LayoutNode(LayoutType.HEADING)
            .withHeadingLevel(level);
        heading.x = contentX;
        heading.y = currentY;
        heading.w = contentWidth;

        List<InlineFragment> fragments = collectInlineFragments(node.getChildren(), null);
        int usedHeight = layoutInlineFragmentsIntoParagraph(heading, fragments, contentX, currentY, contentWidth);
        heading.h = usedHeight + headingSpacing;
        currentY += heading.h;
        return heading;
    }

    private List<LayoutNode> layoutSection(SectionNode node) {
        List<LayoutNode> result = new ArrayList<>();
        // Title as heading level 2
        LayoutNode heading = new LayoutNode(LayoutType.HEADING, node.getTitle())
            .at(contentX, currentY, contentWidth, (int)(metrics.lineHeight() * 1.2f) + headingSpacing)
            .withHeadingLevel(2);
        currentY += (int)(metrics.lineHeight() * 1.2f) + headingSpacing;
        result.add(heading);

        // Children with indent
        int savedX = contentX;
        int savedW = contentWidth;
        contentX += OmniTheme.PADDING;
        contentWidth -= OmniTheme.PADDING;
        inlineCursorX = contentX;
        inlineBaseY = currentY;

        for (DocNode child : node.getChildren()) {
            for (LayoutNode cn : layoutNode(child)) {
                result.add(cn);
            }
        }

        contentX = savedX;
        contentWidth = savedW;
        return result;
    }

    private LayoutNode layoutParagraph(ParagraphNode node) {
        LayoutNode para = new LayoutNode(LayoutType.PARAGRAPH);
        para.x = contentX;
        para.y = currentY;

        int paragraphWidth = Math.max(1, contentWidth);

        // Extract ImageNode children and layout them as block images
        List<DocNode> nonImageChildren = new ArrayList<>();
        for (DocNode child : node.getChildren()) {
            if (child instanceof ImageNode im) {
                para.add(layoutImage(im));
            } else {
                nonImageChildren.add(child);
            }
        }

        List<InlineFragment> fragments = collectInlineFragments(nonImageChildren, null);
        int usedHeight = layoutInlineFragmentsIntoParagraph(para, fragments, para.x, currentY, paragraphWidth);
        para.h = usedHeight + paragraphSpacing;
        para.w = paragraphWidth;
        currentY += para.h;
        return para;
    }

    private LayoutNode layoutInline(String text, boolean styled) {
        return layoutInline(text, styled, false, -1);
    }

    private LayoutNode layoutInline(String text, boolean styled, boolean bold, int color) {
        // Wrap bare inline text using the paragraph layout infrastructure so that
        // overlong text wraps instead of being truncated.
        LayoutType fragType = styled ? LayoutType.STYLED_TEXT : LayoutType.TEXT;
        InlineFragment fragment = new InlineFragment(fragType, text, null, null, bold, color, null);

        LayoutNode para = new LayoutNode(LayoutType.PARAGRAPH);
        para.x = contentX;
        para.y = currentY;
        para.w = contentWidth;

        int usedHeight = layoutInlineFragmentsIntoParagraph(para, List.of(fragment), contentX, currentY, contentWidth);
        para.h = usedHeight + paragraphSpacing;
        currentY += para.h;
        return para;
    }

    /**
     * Smart image sizing: uses lineHeight as the natural unit of measure,
     * so image dimensions scale proportionally with font size and UI scale.
     *
     * Layout structure: [topMargin][image (imgH)][bottomMargin]
     * node.h = imgH only (no margins) so renderer draws exactly the image area.
     * Margins are applied via currentY advancement, creating consistent visual gaps.
     */
    private LayoutNode layoutImage(ImageNode node) {
        int origW = node.getOrigWidth();
        int origH = node.getOrigHeight();
        int maxH = metrics.lineHeight() * 8;
        int imgW, imgH;
        if (origW > 0 && origH > 0) {
            imgW = Math.min(origW, contentWidth);
            imgH = (int) ((float) imgW / origW * origH);
            if (imgH > maxH) {
                imgH = maxH;
                imgW = Math.min((int) ((float) imgH / origH * origW), contentWidth);
            }
        } else {
            imgH = metrics.lineHeight() * 4;
            imgW = Math.min(imgH * 3 / 2, contentWidth);
        }

        // Apply top margin, place image, advance past bottom margin
        currentY += imageTopMargin;
        LayoutNode ln = new LayoutNode(LayoutType.IMAGE, null, node.getUrl(), null, node.getAlt());
        ln.at(contentX, currentY, imgW, imgH);
        currentY += imgH + imageBottomMargin;
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
        para.h = usedHeight + paragraphSpacing;
        currentY += para.h;
        return para;
    }

    private LayoutNode layoutTable(TableNode node) {
        List<String> headers = node.getHeaders();
        List<List<DocNode>> rows = node.getRows();
        // colCount should be the max cell count across all rows (including headers),
        // so that colspan rows don't shrink the column count.
        int colCount = Math.max(headers != null ? headers.size() : 0,
            rows != null ? rows.stream().mapToInt(List::size).max().orElse(1) : 1);
        if (colCount == 0) colCount = 1;

        // Calculate content-based column widths
        int[] maxContentWidths = new int[colCount];
        if (headers != null) {
            for (int i = 0; i < headers.size() && i < colCount; i++) {
                if (headers.size() > 1 || colCount == 1)
                    maxContentWidths[i] = Math.max(maxContentWidths[i], metrics.textWidth(headers.get(i)));
            }
        }
        if (rows != null) {
            for (List<DocNode> row : rows) {
                if (row.size() == 1 && colCount > 1) continue;
                for (int i = 0; i < row.size() && i < colCount; i++) {
                    List<DocNode> children = extractCellChildren(row.get(i));
                    List<InlineFragment> fragments = collectInlineFragments(children, null);
                    int cellW = 0;
                    for (InlineFragment f : fragments) {
                        cellW += f.type == LayoutType.INLINE_IMAGE
                            ? Math.max(1, metrics.lineHeight() - 1) + 1
                            : metrics.textWidth(f.text != null ? f.text : "");
                    }
                    maxContentWidths[i] = Math.max(maxContentWidths[i], cellW);
                }
            }
        }
        int availWidth = contentWidth - tablePadding * (colCount - 1);
        int[] colWidths = new int[colCount];
        int[] colX = new int[colCount];
        int totalContent = 0;
        for (int i = 0; i < colCount; i++) totalContent += Math.max(maxContentWidths[i], 20);
        colX[0] = contentX;
        for (int i = 0; i < colCount; i++) {
            colWidths[i] = Math.max(20, (int) ((float) Math.max(maxContentWidths[i], 20) / totalContent * availWidth));
            if (i > 0) colX[i] = colX[i - 1] + colWidths[i - 1] + tablePadding;
        }

        int rowH = metrics.lineHeight() + tablePadding * 2;
        LayoutNode table = new LayoutNode(LayoutType.TABLE);
        table.at(contentX, currentY, contentWidth, 0);

        if (headers != null) {
            if (headers.size() == 1 && colCount > 1) {
                LayoutNode cell = new LayoutNode(LayoutType.TEXT, headers.get(0)).withIsHeader(true);
                cell.at(contentX, currentY, contentWidth, rowH);
                table.add(cell);
            } else {
                for (int i = 0; i < headers.size() && i < colCount; i++) {
                    LayoutNode cell = new LayoutNode(LayoutType.TEXT, headers.get(i)).withIsHeader(true);
                    cell.at(colX[i], currentY, colWidths[i], rowH);
                    table.add(cell);
                }
            }
            currentY += rowH;
        }

        if (rows != null) {
            for (List<DocNode> row : rows) {
                int maxRowH = rowH;
                boolean singleCell = row.size() == 1 && colCount > 1;
                int cellCount = singleCell ? 1 : Math.min(row.size(), colCount);
                LayoutNode[] rowCells = new LayoutNode[cellCount];
                for (int i = 0; i < cellCount; i++) {
                    List<DocNode> cellChildren = extractCellChildren(row.get(i));
                    // Extract block-level ImageNodes before collectInlineFragments (which skips them)
                    List<DocNode> imageNodes = new ArrayList<>();
                    List<DocNode> nonImageChildren = new ArrayList<>();
                    for (DocNode child : cellChildren) {
                        if (child instanceof ImageNode im) {
                            imageNodes.add(im);
                        } else {
                            nonImageChildren.add(child);
                        }
                    }
                    List<InlineFragment> fragments = collectInlineFragments(nonImageChildren, null);
                    int cellX = singleCell ? contentX : colX[i];
                    int cellW = singleCell ? contentWidth : colWidths[i];
                    LayoutNode cell = new LayoutNode(LayoutType.PARAGRAPH);
                    cell.at(cellX, currentY, cellW, rowH);
                    // Layout block images: lineHeight-based constraints, preserve aspect ratio
                    int imgY = currentY;
                    int cellMaxH = metrics.lineHeight() * 5;
                    for (DocNode imgNode : imageNodes) {
                        if (imgNode instanceof ImageNode im) {
                            LayoutNode imgLayout = new LayoutNode(LayoutType.IMAGE, null, im.getUrl(), null, im.getAlt());
                            int oW = im.getOrigWidth(), oH = im.getOrigHeight();
                            int iW, iH;
                            if (oW > 0 && oH > 0) {
                                iW = Math.min(oW, cellW);
                                iH = (int) ((float) iW / oW * oH);
                                if (iH > cellMaxH) {
                                    iH = cellMaxH;
                                    iW = (int) ((float) iH / oH * oW);
                                }
                            } else {
                                // No HTML dimensions: 3 lines tall, 3:2 aspect ratio
                                iH = metrics.lineHeight() * 3;
                                iW = Math.min(iH * 3 / 2, cellW);
                            }
                            imgLayout.at(cellX, imgY, iW, iH);
                            cell.add(imgLayout);
                            imgY += iH + 2;
                        }
                    }
                    int textStartY = imgY > currentY ? imgY : currentY;
                    int cellHeight = layoutInlineFragmentsIntoParagraph(cell, fragments, cellX, textStartY, cellW);
                    cellHeight = Math.max(cellHeight, imgY - currentY + tablePadding * 2);
                    cell.h = cellHeight + tablePadding * 2;
                    maxRowH = Math.max(maxRowH, cell.h);
                    rowCells[i] = cell;
                }
                for (LayoutNode rc : rowCells) {
                    if (rc != null) { rc.h = maxRowH; table.add(rc); }
                }
                currentY += maxRowH;
            }
        }

        table.h = currentY - table.y + paragraphSpacing;
        currentY += paragraphSpacing;
        return table;
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

                // Bullet marker (rendered on the first line of the item)
                String marker = node.isOrdered() ? (i + 1) + ". " : "• ";
                int markerW = metrics.textWidth(marker);
                LayoutNode markerNode = new LayoutNode(LayoutType.TEXT, marker)
                    .at(contentX, currentY, markerW, metrics.lineHeight());
                listBox.add(markerNode);

                // Item text laid out as a wrapping paragraph with indent
                int itemX = contentX + listIndent;
                int itemWidth = Math.max(1, contentWidth - listIndent);
                LayoutNode itemPara = new LayoutNode(LayoutType.PARAGRAPH);
                itemPara.x = itemX;
                itemPara.y = currentY;
                itemPara.w = itemWidth;

                List<DocNode> itemChildren;
                if (item instanceof ParagraphNode pn) {
                    itemChildren = pn.getChildren();
                } else {
                    itemChildren = List.of(item);
                }
                List<InlineFragment> fragments = collectInlineFragments(itemChildren, null);
                int usedHeight = layoutInlineFragmentsIntoParagraph(itemPara, fragments, itemX, currentY, itemWidth);
                itemPara.h = Math.max(metrics.lineHeight(), usedHeight) + 2;
                listBox.add(itemPara);
                currentY += itemPara.h;
            }
        }

        listBox.h = currentY - listBox.y + paragraphSpacing;
        currentY += paragraphSpacing;
        return listBox;
    }

    private LayoutNode layoutDivider() {
        LayoutNode dn = new LayoutNode(LayoutType.DIVIDER);
        dn.at(contentX, currentY, contentWidth, DIVIDER_HEIGHT);
        currentY += DIVIDER_HEIGHT + paragraphSpacing;
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
                // Handle explicit line breaks (\n from <br> tags)
                int nlIdx = remaining.indexOf('\n');
                if (nlIdx >= 0) {
                    // Flush text before \n as a segment
                    if (nlIdx > 0) {
                        String before = remaining.substring(0, nlIdx);
                        int segWidth = metrics.textWidth(before);
                        line.add(new PendingInline(fragmentWithText(fragment, before), segWidth, Math.max(1, metrics.lineHeight()), segWidth));
                        lineX += segWidth;
                        lineHeight = Math.max(lineHeight, Math.max(1, metrics.lineHeight()));
                    }
                    // Force line break
                    lineY = flushLine(paragraph, line, startX, lineY, lineHeight);
                    lineX = startX;
                    lineHeight = Math.max(1, metrics.lineHeight());
                    remaining = remaining.substring(nlIdx + 1);
                    continue;
                }

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
        // Punctuation hanging: if the next char after the break is a closing punctuation
        // (period, comma, etc.), pull it onto the current line even if it slightly overflows.
        // This prevents orphaned punctuation at the start of a line.
        if (best > 0 && best < text.length() && isClosingPunctuation(text.charAt(best))) {
            best++;
        }
        return best;
    }

    /**
     * Checks if a character is closing punctuation that should not start a line.
     * Includes CJK and common ASCII punctuation.
     */
    private static boolean isClosingPunctuation(char c) {
        return c == '.' || c == ',' || c == '!' || c == '?' || c == ';' || c == ':'
            || c == ')' || c == ']' || c == '}'
            || c == '\u3002'  // 。
            || c == '\uFF0C'  // ，
            || c == '\uFF01'  // ！
            || c == '\uFF1F'  // ？
            || c == '\uFF1B'  // ；
            || c == '\uFF1A'  // ：
            || c == '\uFF09'  // ）
            || c == '\u3011'  // 】
            || c == '\u300B'; // 』
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
