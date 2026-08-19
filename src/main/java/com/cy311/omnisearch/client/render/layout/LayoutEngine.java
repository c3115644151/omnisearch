package com.cy311.omnisearch.client.render.layout;

import com.cy311.omnisearch.client.render.image.ImageDimensions;
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
    @Nullable
    private final ImageSizeProvider imageSizeProvider;

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
        boolean italic,
        boolean underline,
        boolean strikethrough,
        int color,
        @Nullable String linkUrl
    ) {}

    private record PendingInline(InlineFragment fragment, int width, int height, int advance) {}

    public LayoutEngine(FontMetrics metrics, int x, int y, int width) {
        this(metrics, x, y, width, null);
    }

    public LayoutEngine(FontMetrics metrics, int x, int y, int width, @Nullable ImageSizeProvider imageSizeProvider) {
        this.metrics = metrics;
        this.contentX = x;
        this.currentY = y;
        this.contentWidth = width;
        this.imageSizeProvider = imageSizeProvider;
        this.inlineCursorX = x;
        this.inlineBaseY = y;
        this.inlineLineHeight = metrics.lineHeight();
        int lh = metrics.lineHeight();
        this.paragraphSpacing = Math.max(2, lh / 3);
        this.headingSpacing = Math.max(2, lh / 3);
        this.imageTopMargin = Math.max(2, lh / 2);
        // Bottom margin is lh/4: when an image caption follows, the caption adds its own
        // small top gap, so image→caption stays tight (lh/4 + lh/8 ≈ 0.38lh) while a bare
        // image→body gap stays readable. Previously lh/2 (+ lh/2 caption gap = a full lh)
        // pushed captions far below their images.
        this.imageBottomMargin = Math.max(2, lh / 4);
        this.tablePadding = Math.max(2, lh / 3);
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
        if (node instanceof CaptionNode cn) {
            return List.of(layoutCaption(cn));
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

        // Extra breathing room above top-level headings so the hierarchy reads clearly
        int extraTop = level == 1 ? Math.max(2, metrics.lineHeight() / 2) : 0;
        currentY += extraTop;

        List<InlineFragment> fragments = collectInlineFragments(node.getChildren(), null);
        int usedHeight = layoutInlineFragmentsIntoParagraph(heading, fragments, contentX, currentY, contentWidth);
        heading.h = Math.max(usedHeight, scaledLineHeight) + headingSpacing;
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
        // mcmod.cn body paragraphs use CSS text-indent:2em; render as a first-line indent.
        int firstLineIndent = node.isFirstLineIndent() ? metrics.lineHeight() * 2 : 0;

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
        // Text alignment: mcmod rarely uses text-align, but when it does the line is a
        // short centered/right line (e.g. captions). Center/right shift the line start.
        ParagraphNode.Align align = node.getAlign();
        int alignShift = 0;
        if (align == ParagraphNode.Align.CENTER || align == ParagraphNode.Align.RIGHT) {
            int textW = 0;
            for (InlineFragment f : fragments) {
                if (f.type == LayoutType.INLINE_IMAGE) {
                    textW += Math.max(1, metrics.lineHeight() - 1) + 1;
                } else if (f.text != null) {
                    textW += fragmentTextWidth(f);
                }
            }
            if (textW > 0 && textW < paragraphWidth) {
                alignShift = align == ParagraphNode.Align.CENTER
                    ? (paragraphWidth - textW) / 2
                    : (paragraphWidth - textW);
            }
        }
        int usedHeight = layoutInlineFragmentsIntoParagraph(para, fragments, para.x + alignShift, currentY, paragraphWidth - alignShift, firstLineIndent);
        para.h = usedHeight + paragraphSpacing;
        para.w = paragraphWidth;

        // mcmod.cn has no <h1-h6>: section headings are short standalone paragraphs like
        // "魔弹", "召唤方式". Detect them conservatively so the renderer can give them
        // heading prominence without changing the document model.
        if (isHeadingLikeParagraph(node)) {
            para.withHeadingLevel(3);
        }

        currentY += para.h;
        return para;
    }

    private LayoutNode layoutCaption(CaptionNode node) {
        // Image caption: centered gray line at 80% scale. The image above already carried
        // its bottom margin (imageBottomMargin), so the caption only adds a tiny top gap
        // here (lh/8) — image→caption stays tight (≈0.38lh total). The caption then adds
        // a bottom gap (lh/3) so the following body text doesn't crowd the small note.
        // Previously the top gap was lh/2, which stacked with the image's bottom margin
        // into a huge lh gap (the "caption floats far below the image" bug).
        String text = node.getText();
        float scale = 0.8f;
        int textW = (int) (metrics.textWidth(text) * scale);
        int lh = metrics.lineHeight();
        int capH = Math.max(1, (int) (lh * scale));
        int capX = contentX + Math.max(0, (contentWidth - textW) / 2);
        currentY += Math.max(1, lh / 8);   // tiny gap from the image above
        LayoutNode cap = new LayoutNode(LayoutType.TEXT, text)
            .at(capX, currentY, textW, capH);
        cap.withColor(0xFF777777).withTextScale(scale);
        currentY += capH;
        currentY += Math.max(2, lh / 3);   // clear gap to the following body text
        return cap;
    }

    /**
     * Conservative "short heading paragraph" detection. Requires: pure text (no inline
     * markup), ≤ 10 chars, no punctuation, not a "field:" label, not a 【…】 annotation
     * prefix, and containing at least one CJK or letter character.
     */
    private static boolean isHeadingLikeParagraph(ParagraphNode node) {
        StringBuilder sb = new StringBuilder();
        for (DocNode child : node.getChildren()) {
            if (child instanceof TextNode tn) {
                sb.append(tn.getText());
            } else {
                // any inline element (strong/em/link/image) means it's body text
                return false;
            }
        }
        String text = sb.toString().trim();
        if (text.isEmpty() || text.length() > 10) return false;
        if (!text.matches(".*[\\p{IsHan}A-Za-z].*")) return false;
        if (text.endsWith(":") || text.endsWith("：") || text.endsWith("。") || text.endsWith("；")) return false;
        if (text.startsWith("【") || text.startsWith("（") || text.startsWith("(")) return false;
        return true;
    }

    private LayoutNode layoutInline(String text, boolean styled) {
        return layoutInline(text, styled, false, -1);
    }

    private LayoutNode layoutInline(String text, boolean styled, boolean bold, int color) {
        return layoutInline(text, styled, bold, false, false, false, color);
    }

    private LayoutNode layoutInline(String text, boolean styled, boolean bold, boolean italic,
                                    boolean underline, boolean strikethrough, int color) {
        // Wrap bare inline text using the paragraph layout infrastructure so that
        // overlong text wraps instead of being truncated.
        LayoutType fragType = styled ? LayoutType.STYLED_TEXT : LayoutType.TEXT;
        InlineFragment fragment = new InlineFragment(fragType, text, null, null, bold, italic, underline, strikethrough, color, null);

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
    /**
     * Block image sizing: prefers the ACTUAL decoded size (via {@link ImageSizeProvider})
     * so layout matches what will be drawn — no more placeholder/overflow mismatch. Falls
     * back to HTML width/height attributes, then to a sensible default box.
     * <p>
     * Rules: fit within contentWidth; cap height at 8 lines of text; never shrink below a
     * small readable size. Small images (e.g. mcmod item icons) get a minimum display size
     * so they stay legible instead of rendering as a 2px dot.
     */
    private LayoutNode layoutImage(ImageNode node) {
        int[] box = computeImageBox(node, contentWidth, metrics.lineHeight());
        int imgX = contentX + (contentWidth - box[0]) / 2; // center horizontally
        int imgY = currentY + imageTopMargin;
        LayoutNode ln = new LayoutNode(LayoutType.IMAGE, null, node.getUrl(), null, node.getAlt());
        ln.at(imgX, imgY, box[0], box[1]);
        currentY += imageTopMargin + box[1] + imageBottomMargin;
        return ln;
    }

    /**
     * Computes a display box {w, h} for an image, using real size &gt; HTML attrs &gt; default.
     * Returns a 2-element array. Pure helper — shared by block images and table cells.
     */
    private int[] computeImageBox(ImageNode node, int maxWidth, int lineHeight) {
        int maxH = lineHeight * 8;
        int minH = lineHeight * 2;
        int minW = lineHeight * 3;
        int realW = 0, realH = 0;
        if (imageSizeProvider != null) {
            ImageDimensions dims = imageSizeProvider.getImageSize(node.getUrl());
            if (dims != null && dims.width() > 0 && dims.height() > 0) {
                realW = dims.width();
                realH = dims.height();
            }
        }
        int oW = node.getOrigWidth();
        int oH = node.getOrigHeight();
        int w = realW > 0 && realH > 0 ? realW : (oW > 0 && oH > 0 ? oW : 0);
        int h = realW > 0 && realH > 0 ? realH : (oW > 0 && oH > 0 ? oH : 0);

        int imgW, imgH;
        if (w > 0 && h > 0) {
            imgW = Math.min(w, maxWidth);
            imgH = (int) ((float) imgW / w * h);
            if (imgH > maxH) {
                imgH = maxH;
                imgW = Math.min((int) ((float) imgH / h * w), maxWidth);
            }
            // Enforce a minimum display size so small icons stay legible
            if (imgH < minH && imgW < minW) {
                float scale = Math.max((float) minH / imgH, (float) minW / imgW);
                imgH = Math.min((int) (imgH * scale), maxH);
                imgW = Math.min((int) (imgW * scale), maxWidth);
            }
        } else {
            // No known size: assume a 3:2 box, 4 lines tall
            imgH = lineHeight * 4;
            imgW = Math.min(imgH * 3 / 2, maxWidth);
        }
        return new int[]{Math.max(1, imgW), Math.max(1, imgH)};
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
                            : fragmentTextWidth(f);
                    }
                    maxContentWidths[i] = Math.max(maxContentWidths[i], cellW);
                }
            }
        }
        int[] colWidths = new int[colCount];
        int[] colX = new int[colCount];
        // Column widths from content, but a colspan cell's content is split across the
        // columns it covers so merged cells don't starve their columns. Header text is
        // weighted (×1.6) so header-driven tables (like difficulty tables) keep readable
        // columns instead of collapsing to the narrowest label column.
        int[] contentWidths = new int[colCount];
        for (int i = 0; i < colCount; i++) {
            int w = maxContentWidths[i];
            if (headers != null && i < headers.size()) {
                w = Math.max(w, (int) (metrics.textWidth(headers.get(i)) * 1.6f));
            }
            contentWidths[i] = Math.max(w, 24);
        }
        if (rows != null) {
            for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
                List<DocNode> row = rows.get(rowIdx);
                int col = 0;
                for (int i = 0; i < row.size(); i++) {
                    int colspan = Math.max(1, Math.min(node.getColspan(rowIdx, i), colCount - col));
                    if (colspan > 1) {
                        List<DocNode> cellChildren = extractCellChildren(row.get(i));
                        List<InlineFragment> frags = collectInlineFragments(cellChildren, null);
                        int w = 0;
                        for (InlineFragment f : frags) {
                            w += f.type == LayoutType.INLINE_IMAGE
                                ? Math.max(1, metrics.lineHeight() - 1) + 1
                                : fragmentTextWidth(f);
                        }
                        int share = w / colspan;
                        for (int c = col; c < col + colspan && c < colCount; c++) {
                            contentWidths[c] = Math.max(contentWidths[c], share);
                        }
                    }
                    col += colspan;
                }
            }
        }
        int totalContent = 0;
        for (int i = 0; i < colCount; i++) totalContent += contentWidths[i];
        // Tables span the full content width (aligned with body text); column widths
        // stay proportional to content.
        int tableWidth = contentWidth;
        colX[0] = 0;
        for (int i = 0; i < colCount; i++) {
            colWidths[i] = Math.max(20, (int) ((float) contentWidths[i] / totalContent * tableWidth));
            if (i > 0) colX[i] = colX[i - 1] + colWidths[i - 1] + tablePadding;
        }
        // Guarantee the last column ends exactly at the table width (integer rounding
        // otherwise lets the table stick out past the content area / under the scrollbar).
        colWidths[colCount - 1] = Math.max(20, tableWidth - (colX[colCount - 1] - 0));

        // Center the table horizontally within the content area so it doesn't hug one
        // side; column x positions are relative to the table's left edge.
        int tableLeft = contentX + Math.max(0, (contentWidth - tableWidth) / 2);
        int tableW = tableWidth;
        for (int i = 0; i < colCount; i++) {
            colX[i] += tableLeft;
        }

        int rowH = metrics.lineHeight() + tablePadding * 2;        LayoutNode table = new LayoutNode(LayoutType.TABLE);
        table.at(tableLeft, currentY, tableW, 0);

        if (headers != null) {
            LayoutNode headerRow = new LayoutNode(LayoutType.PARAGRAPH);
            headerRow.at(tableLeft, currentY, tableW, 0);
            if (headers.size() == 1 && colCount > 1) {
                LayoutNode cell = new LayoutNode(LayoutType.TEXT, headers.get(0)).withIsHeader(true);
                cell.at(tableLeft, currentY, tableW, rowH);
                headerRow.add(cell);
            } else {
                for (int i = 0; i < headers.size() && i < colCount; i++) {
                    LayoutNode cell = new LayoutNode(LayoutType.TEXT, headers.get(i)).withIsHeader(true);
                    cell.at(colX[i], currentY, colWidths[i], rowH);
                    headerRow.add(cell);
                }
            }
            headerRow.h = rowH;
            table.add(headerRow);
            currentY += rowH;
        }

        if (rows != null) {
            for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
                List<DocNode> row = rows.get(rowIdx);
                int maxRowH = rowH;
                // Group this row's cells under a row container so the renderer can paint
                // the row background once and draw borders at column boundaries only.
                LayoutNode rowBox = new LayoutNode(LayoutType.PARAGRAPH);
                rowBox.at(tableLeft, currentY, tableW, 0);
                // Track the starting column of each cell so colspan cells span the right
                // columns (mcmod.cn merges cells, e.g. "6 点（难度相同）" spanning 3 cols).
                int col = 0;
                for (int i = 0; i < row.size(); i++) {
                    int colspan = Math.max(1, Math.min(node.getColspan(rowIdx, i), colCount - col));
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
                    int cellX = colX[col];
                    int cellW = colX[Math.min(col + colspan, colCount) - 1] + colWidths[Math.min(col + colspan, colCount) - 1] - colX[col];
                    LayoutNode cell = new LayoutNode(LayoutType.PARAGRAPH);
                    cell.at(cellX, currentY, cellW, rowH);
                    // Layout block images: lineHeight-based constraints, preserve aspect ratio
                    int imgY = currentY;
                    int cellMaxH = metrics.lineHeight() * 5;
                    for (DocNode imgNode : imageNodes) {
                        if (imgNode instanceof ImageNode im) {
                            LayoutNode imgLayout = new LayoutNode(LayoutType.IMAGE, null, im.getUrl(), null, im.getAlt());
                            int[] ibox = computeImageBox(im, cellW, metrics.lineHeight());
                            int iW = ibox[0], iH = ibox[1];
                            if (iH > cellMaxH) {
                                iH = cellMaxH;
                                iW = Math.max(1, (int) ((float) iH / ibox[1] * ibox[0]));
                            }
                            imgLayout.at(cellX, imgY, iW, iH);
                            cell.add(imgLayout);
                            imgY += iH + 2;
                        }
                    }
                    // Vertical center the text within the row (rowH is the base row height;
                    // multi-line content expands the row via maxRowH).
                    int textStartY = Math.max(currentY, currentY + Math.max(0, (rowH - metrics.lineHeight()) / 2));
                    if (imgY > currentY) textStartY = imgY;
                    int cellHeight = layoutInlineFragmentsIntoParagraph(cell, fragments, cellX, textStartY, cellW);
                    cellHeight = Math.max(cellHeight, imgY - currentY + tablePadding * 2);
                    cell.h = cellHeight + tablePadding * 2;
                    maxRowH = Math.max(maxRowH, cell.h);
                    rowBox.add(cell);
                    col += colspan;
                }
                for (LayoutNode cell : rowBox.children) {
                    cell.h = maxRowH;
                }
                rowBox.h = maxRowH;
                table.add(rowBox);
                currentY += maxRowH;
            }
        }

        // Table height covers exactly the rows (no trailing paragraph spacing), so the
        // outer border and separators end flush with the last row.
        table.h = currentY - table.y;
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
        boolean italic = style != null && style.italic();
        boolean underline = style != null && style.underline();
        boolean strikethrough = style != null && style.strikethrough();
        int color = -1;
        if (style != null && style.color() != null && !style.color().isEmpty()) {
            try {
                String hex = style.color().startsWith("#") ? style.color().substring(1) : style.color();
                color = (int) Long.parseLong(hex, 16);
                if (color <= 0xFFFFFF) color |= 0xFF000000; // add alpha if missing
            } catch (NumberFormatException ignored) {}
        }
        return layoutInline(node.getText(), true, bold, italic, underline, strikethrough, color);
    }

    private int layoutInlineFragmentsIntoParagraph(
        LayoutNode paragraph,
        List<InlineFragment> fragments,
        int startX,
        int startY,
        int maxWidth
    ) {
        return layoutInlineFragmentsIntoParagraph(paragraph, fragments, startX, startY, maxWidth, 0);
    }

    /**
     * Lays out inline fragments into a wrapping paragraph. When {@code firstLineIndent} &gt;
     * 0, the first line starts indented from {@code startX}; wrapped lines return to
     * {@code startX}. Used to render mcmod.cn's CSS text-indent:2em body paragraphs.
     */
    private int layoutInlineFragmentsIntoParagraph(
        LayoutNode paragraph,
        List<InlineFragment> fragments,
        int startX,
        int startY,
        int maxWidth,
        int firstLineIndent
    ) {
        if (fragments.isEmpty()) {
            return metrics.lineHeight();
        }

        List<PendingInline> line = new ArrayList<>();
        int lineOriginX = startX + firstLineIndent; // first line may be indented
        int lineX = lineOriginX;
        int lineY = startY;
        int lineHeight = Math.max(1, metrics.lineHeight());

        for (InlineFragment fragment : fragments) {
            if (fragment.type == LayoutType.INLINE_IMAGE) {
                int iconSize = Math.max(1, metrics.lineHeight() - 1);
                int advance = iconSize + 1;
                if (lineX + advance > startX + maxWidth && !line.isEmpty()) {
                    lineY = flushLine(paragraph, line, lineOriginX, lineY, lineHeight);
                    lineOriginX = startX;
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
                        int segWidth = fragmentTextWidth(fragmentWithText(fragment, before));
                        line.add(new PendingInline(fragmentWithText(fragment, before), segWidth, Math.max(1, metrics.lineHeight()), segWidth));
                        lineX += segWidth;
                        lineHeight = Math.max(lineHeight, Math.max(1, metrics.lineHeight()));
                    }
                    // Force line break
                    lineY = flushLine(paragraph, line, lineOriginX, lineY, lineHeight);
                    lineOriginX = startX;
                    lineX = startX;
                    lineHeight = Math.max(1, metrics.lineHeight());
                    remaining = remaining.substring(nlIdx + 1);
                    continue;
                }

                int available = (startX + maxWidth) - lineX;
                if (available <= 0 && !line.isEmpty()) {
                    lineY = flushLine(paragraph, line, lineOriginX, lineY, lineHeight);
                    lineOriginX = startX;
                    lineX = startX;
                    lineHeight = Math.max(1, metrics.lineHeight());
                    continue;
                }

                int fitLength = maxFittingPrefixLength(remaining, Math.max(1, available), fragment.bold);
                if (fitLength == 0) {
                    if (!line.isEmpty()) {
                        lineY = flushLine(paragraph, line, lineOriginX, lineY, lineHeight);
                        lineOriginX = startX;
                        lineX = startX;
                        lineHeight = Math.max(1, metrics.lineHeight());
                        continue;
                    }
                    fitLength = 1;
                }

                String segment = remaining.substring(0, fitLength);
                int segmentWidth = fragmentTextWidth(fragmentWithText(fragment, segment));
                line.add(new PendingInline(fragmentWithText(fragment, segment), segmentWidth, Math.max(1, metrics.lineHeight()), segmentWidth));
                lineX += segmentWidth;
                lineHeight = Math.max(lineHeight, Math.max(1, metrics.lineHeight()));
                remaining = remaining.substring(fitLength);

                if (!remaining.isEmpty()) {
                    lineY = flushLine(paragraph, line, lineOriginX, lineY, lineHeight);
                    lineOriginX = startX;
                    lineX = startX;
                    lineHeight = Math.max(1, metrics.lineHeight());
                }
            }
        }

        if (!line.isEmpty()) {
            lineY = flushLine(paragraph, line, lineOriginX, lineY, lineHeight);
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
            if (fragment.italic) node.withItalic(true);
            if (fragment.underline) node.withUnderline(true);
            if (fragment.strikethrough) node.withStrikethrough(true);
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
                    result.add(new InlineFragment(LayoutType.TEXT, tn.getText(), null, null, false, false, false, false, -1, inheritedLinkUrl));
                }
            } else if (node instanceof StyledTextNode stn) {
                TextStyle style = stn.getStyle();
                boolean bold = style != null && style.bold();
                boolean italic = style != null && style.italic();
                boolean underline = style != null && style.underline();
                boolean strikethrough = style != null && style.strikethrough();
                int color = resolveTextColor(style);
                if (!stn.getText().isEmpty()) {
                    result.add(new InlineFragment(LayoutType.STYLED_TEXT, stn.getText(), null, null, bold, italic, underline, strikethrough, color, inheritedLinkUrl));
                }
            } else if (node instanceof ImageInlineNode iin) {
                result.add(new InlineFragment(LayoutType.INLINE_IMAGE, null, iin.getUrl(), iin.getAlt(), false, false, false, false, -1, inheritedLinkUrl));
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
            fragment.italic,
            fragment.underline,
            fragment.strikethrough,
            fragment.color,
            fragment.linkUrl
        );
    }

    /**
     * Measures the rendered width of a text fragment, honoring its bold flag.
     * Bold text is wider than regular text (MC widens bold glyphs), so it must be
     * measured with {@link FontMetrics#boldWidth} or it overflows its wrap boundary.
     */
    private int fragmentTextWidth(InlineFragment fragment) {
        if (fragment.text == null || fragment.text.isEmpty()) {
            return 0;
        }
        return fragment.bold ? metrics.boldWidth(fragment.text) : metrics.textWidth(fragment.text);
    }

    private int maxFittingPrefixLength(String text, int maxWidth, boolean bold) {
        if (text.isEmpty()) return 0;
        // Measure with the fragment's actual weight so bold text wraps at the same pixel
        // boundary it will occupy when rendered (bold glyphs are wider).
        if (bold) {
            if (metrics.boldWidth(text) <= maxWidth) return text.length();
        } else if (metrics.textWidth(text) <= maxWidth) {
            return text.length();
        }
        int low = 1;
        int high = text.length();
        int best = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int width = bold
                ? metrics.boldWidth(text.substring(0, mid))
                : metrics.textWidth(text.substring(0, mid));
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
