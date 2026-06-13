package com.cy311.omnisearch.client.render.document;

import com.cy311.omnisearch.data.model.document.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Renders a Document (DocNode tree) onto the screen using GuiGraphics.
 * Each visitXxx method returns the Y position after rendering the node.
 * <p>
 * Layout: text flows left-to-right within a fixed-width area, wrapping at word boundaries.
 * Block-level nodes (headings, paragraphs, tables, lists, images, dividers, sections)
 * each start on a new line. Inline content flows horizontally.
 */
public class DocumentRenderer implements DocNodeVisitor<Integer> {

    private static final int PARAGRAPH_SPACING = 4;
    private static final int LIST_INDENT = 10;
    private static final int TABLE_PADDING = 2;
    private static final int IMAGE_PLACEHOLDER_WIDTH = 64;
    private static final int IMAGE_PLACEHOLDER_HEIGHT = 48;

    // Heading colors
    private static final int COLOR_HEADING_1 = 0xFFFFAA00;
    private static final int COLOR_HEADING_2 = 0xFFFFD700;
    private static final int COLOR_HEADING_3 = 0xFFFFFFFF;
    // Link color
    private static final int COLOR_LINK = 0xFF5555FF;
    // Table colors
    private static final int COLOR_TABLE_HEADER_BG = 0xFF333333;
    private static final int COLOR_TABLE_BORDER = 0xFF555555;

    private final GuiGraphics gui;
    private final Font font;
    private int x;
    private int y;
    private final int width;

    // Inline rendering state
    private int cursorX;
    private int inlineY;
    private boolean inlineMode;

    public DocumentRenderer(GuiGraphics gui, Font font, int x, int y, int width) {
        this.gui = gui;
        this.font = font;
        this.x = x;
        this.y = y;
        this.width = width;
        this.inlineMode = false;
    }

    /**
     * Entry point: renders an entire Document starting from the current position.
     */
    public void render(Document doc) {
        for (DocNode node : doc.content()) {
            y = node.accept(this);
        }
    }

    // -----------------------------------------------------------
    // Node visitors
    // -----------------------------------------------------------

    @Override
    public Integer visitHeading(HeadingNode node) {
        int level = node.getLevel();
        float scale;
        int color;

        if (level == 1) {
            scale = 1.5f;
            color = COLOR_HEADING_1;
        } else if (level == 2) {
            scale = 1.2f;
            color = COLOR_HEADING_2;
        } else {
            scale = 1.0f;
            color = COLOR_HEADING_3;
        }

        String text = collectText(node.getChildren());

        gui.pose().pushPose();
        // verified: GuiGraphics.pose() returns PoseStack (NeoForge 1.21.1 lexxie.dev 2026-06-14)
        gui.pose().translate(x, y, 0);
        gui.pose().scale(scale, scale, 1.0f);
        // verified: GuiGraphics.drawString(Font, String, int, int, int, boolean) NeoForge 1.21.1 lexxie.dev 2026-06-14
        gui.drawString(font, text, 0, 0, color, false);
        gui.pose().popPose();

        int consumedHeight = (int) (font.lineHeight * scale) + PARAGRAPH_SPACING;
        // verified: Font.lineHeight is final int field (NeoForge 1.21.1 Javadoc 2026-06-14)
        return y + consumedHeight;
    }

    @Override
    public Integer visitParagraph(ParagraphNode node) {
        y += 2;
        int newY = renderInlineChildren(node.getChildren(), x, y);
        return newY + PARAGRAPH_SPACING;
    }

    @Override
    public Integer visitText(TextNode node) {
        if (inlineMode) {
            cursorX = renderInlineText(node.getText(), x, cursorX, inlineY, 0xFFFFFFFF, false);
            return inlineY;
        }
        return renderTextLine(node.getText(), x, y, 0xFFFFFFFF, false);
    }

    @Override
    public Integer visitStyledText(StyledTextNode node) {
        TextStyle style = node.getStyle();
        int color = parseColor(style.color(), 0xFFFFFFFF);
        boolean bold = style.bold();

        if (inlineMode) {
            int startX = cursorX;
            cursorX = renderInlineText(node.getText(), x, cursorX, inlineY, color, bold);
            if (style.underline()) {
                gui.hLine(startX, cursorX - 1, inlineY + font.lineHeight - 1, color);
                // verified: GuiGraphics.hLine(int,int,int,int) NeoForge 1.21.1 mappings.dev 2026-06-14
            }
            return inlineY;
        }

        return renderStyledTextBlock(node, color, bold);
    }

    private int renderStyledTextBlock(StyledTextNode node, int color, boolean bold) {
        String text = node.getText();
        int textWidth = font.width(text);

        if (textWidth <= width) {
            gui.drawString(font, text, x, y, color, false);
            if (bold) {
                gui.drawString(font, text, x + 1, y, color, false);
            }
            if (node.getStyle().underline()) {
                gui.hLine(x, x + textWidth - 1, y + font.lineHeight - 1, color);
            }
            return y + font.lineHeight;
        }

        // Word wrap
        int currentY = y;
        String remaining = text;
        int availWidth = width;

        while (!remaining.isEmpty() && font.width(remaining) > availWidth) {
            String line = font.plainSubstrByWidth(remaining, availWidth);
            // verified: Font.plainSubstrByWidth(String, int) NeoForge 1.21.1 lexxie.dev 2026-06-14
            int lastSpace = line.lastIndexOf(' ');
            if (lastSpace > 0) {
                line = remaining.substring(0, lastSpace);
            }

            gui.drawString(font, line, x, currentY, color, false);
            if (bold) {
                gui.drawString(font, line, x + 1, currentY, color, false);
            }
            currentY += font.lineHeight;
            remaining = remaining.substring(line.length()).trim();
        }

        if (!remaining.isEmpty()) {
            gui.drawString(font, remaining, x, currentY, color, false);
            if (bold) {
                gui.drawString(font, remaining, x + 1, currentY, color, false);
            }
            currentY += font.lineHeight;
        }

        return currentY;
    }

    @Override
    public Integer visitLink(LinkNode node) {
        if (inlineMode) {
            for (DocNode child : node.getChildren()) {
                if (child instanceof TextNode tn) {
                    int startX = cursorX;
                    cursorX = renderInlineText(tn.getText(), x, cursorX, inlineY, COLOR_LINK, false);
                    gui.hLine(startX, cursorX - 1, inlineY + font.lineHeight - 1, COLOR_LINK);
                } else if (child instanceof StyledTextNode stn) {
                    int startX = cursorX;
                    cursorX = renderInlineText(stn.getText(), x, cursorX, inlineY, COLOR_LINK, false);
                    gui.hLine(startX, cursorX - 1, inlineY + font.lineHeight - 1, COLOR_LINK);
                } else {
                    child.accept(this);
                }
            }
            return inlineY;
        }

        // Block-level link: render children as inline blue text
        int currentY = y;
        int cx = x;
        for (DocNode child : node.getChildren()) {
            if (child instanceof TextNode tn) {
                gui.drawString(font, tn.getText(), cx, currentY, COLOR_LINK, false);
                int w = font.width(tn.getText());
                gui.hLine(cx, cx + w - 1, currentY + font.lineHeight - 1, COLOR_LINK);
                cx += w;
            } else if (child instanceof StyledTextNode stn) {
                gui.drawString(font, stn.getText(), cx, currentY, COLOR_LINK, false);
                int w = font.width(stn.getText());
                gui.hLine(cx, cx + w - 1, currentY + font.lineHeight - 1, COLOR_LINK);
                cx += w;
            } else {
                currentY = child.accept(this);
                cx = x;
            }
        }
        return currentY + font.lineHeight;
    }

    @Override
    public Integer visitTable(TableNode node) {
        List<String> headers = node.getHeaders();
        int colCount = headers.size();
        if (colCount == 0) return y;

        // Calculate column widths
        int[] colWidths = new int[colCount];
        int totalWidth = 0;
        for (int i = 0; i < colCount; i++) {
            colWidths[i] = font.width(headers.get(i)) + TABLE_PADDING * 4;
            for (List<DocNode> row : node.getRows()) {
                if (i < row.size()) {
                    String cellText = collectText(List.of(row.get(i)));
                    int cellW = font.width(cellText) + TABLE_PADDING * 4;
                    if (cellW > colWidths[i]) colWidths[i] = cellW;
                }
            }
            totalWidth += colWidths[i];
        }

        // Clamp to available width
        if (totalWidth > width) {
            float ratio = (float) width / totalWidth;
            int newTotal = 0;
            for (int i = 0; i < colCount; i++) {
                colWidths[i] = Math.max(10, (int) (colWidths[i] * ratio));
                newTotal += colWidths[i];
            }
            totalWidth = newTotal;
        }

        int tableX = x;
        int currentY = y;

        // Draw header background
        gui.fill(tableX, currentY, tableX + totalWidth, currentY + font.lineHeight + TABLE_PADDING * 2, COLOR_TABLE_HEADER_BG);
        // verified: GuiGraphics.fill(int,int,int,int,int) NeoForge 1.21.1 lexxie.dev 2026-06-14

        // Draw header text
        int cx = tableX + TABLE_PADDING;
        for (int i = 0; i < colCount; i++) {
            gui.drawString(font, headers.get(i), cx, currentY + TABLE_PADDING, 0xFFFFFFFF, false);
            gui.drawString(font, headers.get(i), cx + 1, currentY + TABLE_PADDING, 0xFFFFFFFF, false);
            cx += colWidths[i];
        }

        currentY += font.lineHeight + TABLE_PADDING * 2;

        // Draw rows
        for (int rowIdx = 0; rowIdx < node.getRows().size(); rowIdx++) {
            List<DocNode> row = node.getRows().get(rowIdx);
            int rowY = currentY;

            // Row border
            gui.hLine(tableX, tableX + totalWidth - 1, rowY, COLOR_TABLE_BORDER);

            // Determine row height
            int maxLineHeight = font.lineHeight;
            for (int i = 0; i < Math.min(colCount, row.size()); i++) {
                String text = collectText(List.of(row.get(i)));
                int lines = 1;
                int cellAvail = colWidths[i] - TABLE_PADDING * 2;
                if (font.width(text) > cellAvail && cellAvail > 0) {
                    lines = Math.max(1, (int) Math.ceil((float) font.width(text) / cellAvail));
                }
                int rowH = lines * font.lineHeight + TABLE_PADDING * 2;
                if (rowH > maxLineHeight) maxLineHeight = rowH;
            }

            // Alternating row background
            if (rowIdx % 2 == 1) {
                gui.fill(tableX, rowY, tableX + totalWidth, rowY + maxLineHeight, 0xFF222222);
            }

            // Cell content
            cx = tableX + TABLE_PADDING;
            for (int i = 0; i < Math.min(colCount, row.size()); i++) {
                String text = collectText(List.of(row.get(i)));
                int cellWidth = colWidths[i] - TABLE_PADDING * 2;
                if (font.width(text) > cellWidth && cellWidth > 0) {
                    String truncated = font.plainSubstrByWidth(text, cellWidth);
                    gui.drawString(font, truncated, cx, rowY + TABLE_PADDING, 0xFFFFFFFF, false);
                } else {
                    gui.drawString(font, text, cx, rowY + TABLE_PADDING, 0xFFFFFFFF, false);
                }
                cx += colWidths[i];
            }

            currentY += maxLineHeight;
        }

        // Bottom border
        gui.hLine(tableX, tableX + totalWidth - 1, currentY, COLOR_TABLE_BORDER);
        // Side borders
        gui.vLine(tableX, y, currentY, COLOR_TABLE_BORDER);
        gui.vLine(tableX + totalWidth - 1, y, currentY, COLOR_TABLE_BORDER);
        // verified: GuiGraphics.vLine(int,int,int,int) NeoForge 1.21.1 mappings.dev 2026-06-14

        return currentY + PARAGRAPH_SPACING;
    }

    @Override
    public Integer visitList(ListNode node) {
        List<DocNode> items = node.getItems();
        int currentY = y;

        for (int i = 0; i < items.size(); i++) {
            // Render marker
            String marker = node.isOrdered() ? (i + 1) + "." : "\u2022";
            gui.drawString(font, marker, x, currentY, 0xFFFFFFFF, false);

            // Render item content indented
            int itemX = x + LIST_INDENT;
            DocNode item = items.get(i);
            if (item instanceof ParagraphNode pn) {
                currentY = renderInlineChildren(pn.getChildren(), itemX, currentY);
            } else {
                currentY = renderTextLine(collectText(List.of(item)), itemX, currentY, 0xFFFFFFFF, false);
            }
        }

        return currentY + PARAGRAPH_SPACING;
    }

    @Override
    public Integer visitImage(ImageNode node) {
        int imgX = x;
        int imgY = y;

        // Placeholder rectangle
        gui.fill(imgX, imgY, imgX + IMAGE_PLACEHOLDER_WIDTH, imgY + IMAGE_PLACEHOLDER_HEIGHT, 0xFF444444);
        // Border
        gui.hLine(imgX, imgX + IMAGE_PLACEHOLDER_WIDTH - 1, imgY, 0xFF888888);
        gui.hLine(imgX, imgX + IMAGE_PLACEHOLDER_WIDTH - 1, imgY + IMAGE_PLACEHOLDER_HEIGHT - 1, 0xFF888888);
        gui.vLine(imgX, imgY, imgY + IMAGE_PLACEHOLDER_HEIGHT - 1, 0xFF888888);
        gui.vLine(imgX + IMAGE_PLACEHOLDER_WIDTH - 1, imgY, imgY + IMAGE_PLACEHOLDER_HEIGHT - 1, 0xFF888888);

        // Alt text
        String alt = node.getAlt();
        if (!alt.isEmpty()) {
            gui.drawString(font, alt, imgX, imgY + IMAGE_PLACEHOLDER_HEIGHT + 1, 0xFFAAAAAA, false);
        }

        int altLines = alt.isEmpty() ? 0 : 1;
        int consumedHeight = IMAGE_PLACEHOLDER_HEIGHT + altLines * (font.lineHeight + 1) + PARAGRAPH_SPACING;
        return y + consumedHeight;
    }

    @Override
    public Integer visitImageInline(ImageInlineNode node) {
        int iconSize = font.lineHeight;

        if (inlineMode) {
            if (cursorX + iconSize > x + width && cursorX > x) {
                inlineY += font.lineHeight;
                cursorX = x;
            }
        }

        gui.fill(cursorX, inlineY, cursorX + iconSize, inlineY + iconSize, 0xFF444444);
        gui.vLine(cursorX, inlineY, inlineY + iconSize - 1, 0xFF888888);
        gui.vLine(cursorX + iconSize - 1, inlineY, inlineY + iconSize - 1, 0xFF888888);
        gui.hLine(cursorX, cursorX + iconSize - 1, inlineY, 0xFF888888);
        gui.hLine(cursorX, cursorX + iconSize - 1, inlineY + iconSize - 1, 0xFF888888);

        String alt = node.getAlt();
        if (!alt.isEmpty()) {
            gui.drawString(font, alt.substring(0, 1), cursorX + 1, inlineY, 0xFFAAAAAA, false);
        }

        cursorX += iconSize + 2;

        if (inlineMode) return inlineY;
        return y + font.lineHeight;
    }

    @Override
    public Integer visitDivider(DividerNode node) {
        int midY = y + font.lineHeight / 2;
        int margin = Math.min(10, width / 8);
        int dividerX = x + margin;
        int dividerWidth = width - 2 * margin;
        if (dividerWidth < 0) dividerWidth = width;

        gui.hLine(dividerX, dividerX + dividerWidth, midY, 0xFF888888);
        return y + font.lineHeight + PARAGRAPH_SPACING;
    }

    @Override
    public Integer visitSection(SectionNode node) {
        // Title as heading level 2
        HeadingNode heading = new HeadingNode(2, List.of(new TextNode(node.getTitle())));
        int newY = visitHeading(heading);

        int savedX = this.x;
        int savedY = this.y;
        this.x = savedX + 4;
        this.y = newY;

        for (DocNode child : node.getChildren()) {
            this.y = child.accept(this);
        }

        this.x = savedX;
        int resultY = this.y;
        this.y = savedY;
        return resultY;
    }

    // -----------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------

    /**
     * Renders a list of child nodes as inline content (flowing horizontally with word wrap).
     */
    private int renderInlineChildren(List<DocNode> children, int startX, int startY) {
        boolean prevInline = inlineMode;
        int prevCursorX = cursorX;
        int prevInlineY = inlineY;

        inlineMode = true;
        cursorX = startX;
        inlineY = startY;

        for (DocNode child : children) {
            if (isBlockNode(child)) {
                // Flush current line
                if (cursorX > x) {
                    inlineY += font.lineHeight;
                    cursorX = x;
                }
                int savedBlockX = this.x;
                this.x = x;
                this.y = inlineY;
                inlineY = child.accept(this);
                this.x = savedBlockX;
                cursorX = x;
            } else {
                child.accept(this);
            }
        }

        int resultY = inlineY + font.lineHeight;

        inlineMode = prevInline;
        cursorX = prevCursorX;
        inlineY = prevInlineY;

        return resultY;
    }

    private static boolean isBlockNode(DocNode node) {
        return node instanceof ImageNode
            || node instanceof TableNode
            || node instanceof ListNode
            || node instanceof DividerNode
            || node instanceof SectionNode
            || node instanceof HeadingNode;
    }

    /**
     * Renders inline text with word wrapping. Returns the new cursorX.
     */
    private int renderInlineText(String text, int leftMargin, int currentX, int currentY, int color, boolean bold) {
        if (text.isEmpty()) return currentX;

        int textWidth = font.width(text);
        int maxLineWidth = width - (currentX - leftMargin);

        if (textWidth <= maxLineWidth) {
            // Fits on current line
            gui.drawString(font, text, currentX, currentY, color, false);
            if (bold) {
                gui.drawString(font, text, currentX + 1, currentY, color, false);
            }
            return currentX + textWidth;
        }

        // Wrap by words
        String[] words = text.split(" ");
        int cx = currentX;

        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            int wordW = font.width(word);

            if (cx + wordW > leftMargin + width && cx > leftMargin) {
                inlineY += font.lineHeight;
                cx = leftMargin;
            }

            // Add space before word (except first)
            if (i > 0) {
                gui.drawString(font, " ", cx, inlineY, color, false);
                cx += font.width(" ");
            }

            gui.drawString(font, word, cx, inlineY, color, false);
            if (bold) {
                gui.drawString(font, word, cx + 1, inlineY, color, false);
            }
            cx += wordW;
        }

        return cx;
    }

    /**
     * Renders a single text line with word wrapping. Returns the new Y.
     */
    private int renderTextLine(String text, int startX, int startY, int color, boolean bold) {
        if (text.isEmpty()) return startY + font.lineHeight;

        if (font.width(text) <= width) {
            gui.drawString(font, text, startX, startY, color, false);
            if (bold) {
                gui.drawString(font, text, startX + 1, startY, color, false);
            }
            return startY + font.lineHeight;
        }

        int currentY = startY;
        String remaining = text;

        while (!remaining.isEmpty() && font.width(remaining) > width) {
            String line = font.plainSubstrByWidth(remaining, width);
            int lastSpace = line.lastIndexOf(' ');
            if (lastSpace > 0) {
                line = remaining.substring(0, lastSpace);
            }

            gui.drawString(font, line, startX, currentY, color, false);
            if (bold) {
                gui.drawString(font, line, startX + 1, currentY, color, false);
            }
            currentY += font.lineHeight;
            remaining = remaining.substring(line.length()).trim();
        }

        if (!remaining.isEmpty()) {
            gui.drawString(font, remaining, startX, currentY, color, false);
            if (bold) {
                gui.drawString(font, remaining, startX + 1, currentY, color, false);
            }
            currentY += font.lineHeight;
        }

        return currentY;
    }

    /**
     * Collects text from a list of nodes recursively.
     */
    private static String collectText(List<DocNode> nodes) {
        StringBuilder sb = new StringBuilder();
        collectTextRecursive(nodes, sb);
        return sb.toString();
    }

    private static void collectTextRecursive(List<DocNode> nodes, StringBuilder sb) {
        for (DocNode node : nodes) {
            if (node instanceof TextNode tn) {
                sb.append(tn.getText());
            } else if (node instanceof StyledTextNode stn) {
                sb.append(stn.getText());
            } else if (node instanceof LinkNode ln) {
                collectTextRecursive(ln.getChildren(), sb);
            } else if (node instanceof HeadingNode hn) {
                collectTextRecursive(hn.getChildren(), sb);
            } else if (node instanceof ParagraphNode pn) {
                collectTextRecursive(pn.getChildren(), sb);
            }
        }
    }

    /**
     * Parses a hex color string (e.g. "#FFAA00") to ARGB int, or returns default on null.
     */
    private static int parseColor(@Nullable String colorStr, int defaultColor) {
        if (colorStr == null || colorStr.isEmpty()) return defaultColor;
        try {
            if (colorStr.startsWith("#")) {
                return 0xFF000000 | Integer.parseInt(colorStr.substring(1), 16);
            }
            return 0xFF000000 | Integer.parseInt(colorStr, 16);
        } catch (NumberFormatException e) {
            return defaultColor;
        }
    }
}
