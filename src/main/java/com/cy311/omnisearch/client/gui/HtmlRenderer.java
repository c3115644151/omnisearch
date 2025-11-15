package com.cy311.omnisearch.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import com.cy311.omnisearch.util.ImageManager;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.fml.ModList;


import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Arrays;
import com.cy311.omnisearch.util.McmodFetcher;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlRenderer {
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+\\.\\d+\\.\\d+)");
    private static final Set<String> BLOCK_TAGS = new java.util.HashSet<>(Arrays.asList(
            "p","h1","h2","h3","h4","h5","div","ul","li","br","table"
    ));
    private static final int PADDING = 2;
    private static final int LIST_INDENT_SPACES = 2;
    private final Runnable onUpdate;
    private static final Map<String, ResourceLocation> textureCache = new HashMap<>();

    private static final Map<String, ImagePart> ICONS = new HashMap<>();
    static {
        ICONS.put("icon-health-full", new ImagePart(ResourceLocation.fromNamespaceAndPath("minecraft", "hud/heart/full"), 9, 9, true));
        ICONS.put("icon-health-half", new ImagePart(ResourceLocation.fromNamespaceAndPath("minecraft", "hud/heart/half"), 9, 9, true));
        ICONS.put("icon-health-empty", new ImagePart(ResourceLocation.fromNamespaceAndPath("minecraft", "hud/heart/container"), 9, 9, true));
    }

    // =================================================================================
    // 新的行内渲染模型
    // =================================================================================

    /**
     * 存储样式信息，如颜色、是否为链接等。
     */
    private static class Style {
        public static final int DEFAULT_COLOR = 0xFFD0D0D0;
        public static final int LINK_COLOR = 0xFF9090FF; // A lighter blue for links
        public static final int FIGCAPTION_COLOR = 0xFF9E9E9E; // Darker grey for captions

        public int color;
        public String linkUrl;
        public boolean isBold = false;
        public boolean isItalic = false;

        public Style() {
            this.color = DEFAULT_COLOR;
            this.linkUrl = null;
        }

        public Style copy() {
            Style newStyle = new Style();
            newStyle.color = this.color;
            newStyle.linkUrl = this.linkUrl;
            newStyle.isBold = this.isBold;
            newStyle.isItalic = this.isItalic;
            return newStyle;
        }

        public Style withBold(boolean bold) {
            Style s = this.copy();
            s.isBold = bold;
            return s;
        }

        public Style withItalic(boolean italic) {
            Style s = this.copy();
            s.isItalic = italic;
            return s;
        }

        public Style withColor(int newColor) {
            Style s = this.copy();
            s.color = newColor;
            return s;
        }

        public Style withLink(String url) {
            Style s = this.copy();
            s.linkUrl = url;
            return s;
        }
    }

    private abstract static class RenderablePart {
        public final int width;

        protected RenderablePart(int width) {
            this.width = width;
        }
    }

    private static class CaptionMetrics {
        final float offsetX;
        final float finalScale;
        CaptionMetrics(float offsetX, float finalScale) {
            this.offsetX = offsetX;
            this.finalScale = finalScale;
        }
    }

    private static class Painter {
        static void renderLine(RenderContext ctx, RenderableLine line, int x, int y) {
            if (line.isFigcaption) return;
            GuiGraphics g = ctx.g;
            Font font = ctx.font;
            float scale = ctx.scale;
            if (line.lineType == RenderableLine.LineType.TITLE_LINE) {
                scale *= 1.15f;
            }
            int renderWidth = ctx.renderWidth;
            RenderCompat.push(g);
            RenderCompat.scale(g, scale, scale);
            int currentX = (int)(x / scale);
            int currentY = (int)(y / scale);
            if (line.lineType == RenderableLine.LineType.IMAGE_LINE) {
                currentX += computeCenterOffset(line, renderWidth / scale);
            }
            if (line.lineType == RenderableLine.LineType.TITLE_LINE) {
                int padX = 3;
                int padY = 2;
                int bx0 = currentX - padX;
                int by0 = currentY - padY;
                int bx1 = currentX + line.totalWidth + padX;
                int by1 = currentY + line.height + padY - 1;
                g.fill(bx0, by0, bx1, by1, 0x22151518);
            }
            if (line.lineType == RenderableLine.LineType.TITLE_LINE) {
                int ux0 = currentX;
                int ux1 = currentX + (int)(line.totalWidth * 0.6f); // 仅占标题宽度的60%
                int uy0 = currentY + line.height + 1;
                int uy1 = uy0 + 1; // 1px baseline
                int gradientW = Math.min(10, Math.max(0, ux1 - ux0));
                int baseEnd = ux1 - gradientW;
                if (baseEnd > ux0) {
                    g.fill(ux0, uy0, baseEnd, uy1, 0x22454A53); // 更淡 alpha
                }
                for (int i = 0; i < gradientW; i++) {
                    float t = (float)i / (float)gradientW;
                    int alpha = (int)(0x33 * (1.0f - t)); // 渐隐更弱
                    int color = (alpha << 24) | 0x00454A53;
                    int px = baseEnd + i;
                    g.fill(px, uy0, px + 1, uy1, color);
                }
            }
            for (RenderablePart part : line.parts) {
                if (part instanceof ImagePart) {
                    currentX = renderImagePart(ctx, line, (ImagePart) part, currentX, currentY);
                } else if (part instanceof StyledPart) {
                    currentX = renderStyledPart(ctx, line, (StyledPart) part, currentX, currentY);
                }
            }
            RenderCompat.pop(g);
        }

        static void renderFigcaption(RenderContext ctx, RenderableLine line, int x, int y, CaptionMetrics m) {
            GuiGraphics g = ctx.g;
            Font font = ctx.font;
            RenderCompat.push(g);
            RenderCompat.translate(g, x + m.offsetX, y);
            RenderCompat.scale(g, m.finalScale, m.finalScale);
            int currentX = 0;
            int currentY = 0;
            for (RenderablePart part : line.parts) {
                if (part instanceof StyledPart) {
                    StyledPart styledPart = (StyledPart) part;
                    int textY = currentY + (int)((line.height - font.lineHeight) / 2f);
                    g.drawString(font, styledPart.text, currentX, textY, styledPart.style.color);
                    currentX += styledPart.width;
                }
            }
            RenderCompat.pop(g);
        }

        private static int computeCenterOffset(RenderableLine line, float unscaledRenderWidth) {
            return (int)((unscaledRenderWidth - line.totalWidth) / 2);
        }

        private static int renderImagePart(RenderContext ctx, RenderableLine line, ImagePart imagePart, int currentX, int currentY) {
            GuiGraphics g = ctx.g;
            int partY = currentY + (int)((line.height - imagePart.imageHeight) / 2f);
            RenderCompat.push(g);
            RenderCompat.translate(g, currentX, partY);
            if (imagePart.isSprite) {
                RenderCompat.blitSprite(g, imagePart.location, 0, 0, imagePart.imageWidth, imagePart.imageHeight);
            } else if (imagePart.location != null) {
                float sx = imagePart.texWidth > 0 ? (float) imagePart.imageWidth / (float) imagePart.texWidth : 1f;
                float sy = imagePart.texHeight > 0 ? (float) imagePart.imageHeight / (float) imagePart.texHeight : 1f;
                RenderCompat.scale(g, sx, sy);
                RenderCompat.blitTexture(g, imagePart.location, 0, 0, 0, 0, Math.max(1, imagePart.texWidth), Math.max(1, imagePart.texHeight), Math.max(1, imagePart.texWidth), Math.max(1, imagePart.texHeight));
            }
            RenderCompat.pop(g);
            return currentX + imagePart.width;
        }

        private static int renderStyledPart(RenderContext ctx, RenderableLine line, StyledPart styledPart, int currentX, int currentY) {
            Font font = ctx.font;
            GuiGraphics g = ctx.g;
            int textY = currentY + (int)((line.height - font.lineHeight) / 2f);
            g.drawString(font, styledPart.text, currentX, textY, styledPart.style.color);
            return currentX + styledPart.width;
        }
    }

    private static class RenderContext {
        final GuiGraphics g;
        final Font font;
        final float scale;
        final int renderWidth;
        RenderContext(GuiGraphics g, Font font, float scale, int renderWidth) {
            this.g = g;
            this.font = font;
            this.scale = scale;
            this.renderWidth = renderWidth;
        }
    }

    /**
     * 代表一行中带有特定样式的一部分文本。
     */
    private static class StyledPart extends RenderablePart {
        public final Component text;
        public final Style style;

        public StyledPart(String text, Style style, Font font) {
            super(font.width(text));
            this.style = style;

            MutableComponent component = Component.literal(text);
            if (style.isBold) {
                component.withStyle(s -> s.withBold(true));
            }
            if (style.isItalic) {
                component.withStyle(s -> s.withItalic(true));
            }
            this.text = component;
        }
    }

    /**
     * 代表一个可渲染的行，由多个 StyledPart 组成。
     */
    private static class RenderableLine {
        public final List<RenderablePart> parts = new ArrayList<>();
        public int height; // No longer final, stores max UNscaled height of parts
        public int totalWidth = 0;
        public boolean isFigcaption = false;
        public int marginBottom = 0;
        enum LineType { TEXT_LINE, IMAGE_LINE, CAPTION_LINE, TITLE_LINE }
        public LineType lineType = LineType.TEXT_LINE;

        public RenderableLine(Font font) {
            this.height = font.lineHeight;
        }

        public void addPart(RenderablePart part) {
            parts.add(part);
            totalWidth += part.width;
            if (part instanceof ImagePart) {
                int imageHeight = ((ImagePart) part).imageHeight;
                if (imageHeight > this.height) {
                    this.height = imageHeight;
                }
                // 当行内出现图片，标记为图片行
                this.lineType = LineType.IMAGE_LINE;
            }
        }

        public void render(GuiGraphics g, int x, int y, Font font, float scale, int renderWidth) {
            RenderContext ctx = new RenderContext(g, font, scale, renderWidth);
            if (isFigcaption) {
                CaptionMetrics m = computeCaptionOffsetAndScale(scale, renderWidth);
                Painter.renderFigcaption(ctx, this, x, y, m);
            } else {
                Painter.renderLine(ctx, this, x, y);
            }
        }

        

        private CaptionMetrics computeCaptionOffsetAndScale(float scale, int renderWidth) {
            float sMin = 0.65f * scale;
            float sMax = 0.85f * scale;
            float s = 0.7f * scale;
            float maxByWidth = this.totalWidth > 0 ? (float) renderWidth / this.totalWidth : s;
            if (s > maxByWidth) s = maxByWidth;
            if (s < sMin) s = sMin;
            if (s > sMax) s = sMax;
            float w = this.totalWidth * s;
            float offsetX = Math.round((renderWidth - w) / 2.0f);
            return new CaptionMetrics(offsetX, s);
        }
    }

    private final List<RenderableLine> renderableLines = new ArrayList<>();
    private int totalHeight = 0;
    private final float scale = 0.75f;
    private final float lineSpacingFactor = 1.2f;
    private final MetricsCalculator metrics = new MetricsCalculator();

    // 解析状态
    private RenderableLine currentLine;
    private int currentX;
    private Stack<Style> styleStack;
    private int renderWidth;
    private final Font font;
    private final Map<String, ElementHandler> elementHandlers = new HashMap<>();
    private final Map<String, Float> widthCache = new HashMap<>();
    private final LayoutEngine layout = new LayoutEngine();
    // 有序列表状态
    private boolean inOrderedList = false;
    private int orderedListIndex = 0;
    private String orderedListStyle = "decimal";
    // 无序列表状态
    private boolean inUnorderedList = false;
    private String unorderedListStyle = "disc";


    public HtmlRenderer(Runnable onUpdate) {
        this.font = Minecraft.getInstance().font;
        this.onUpdate = onUpdate;
        buildElementHandlers();
    }

    private class LayoutEngine {
        private final Map<String, List<String>> splitCache = new HashMap<>();
        void clear() { splitCache.clear(); }
        float correctedMaxWidth(int maxWidth) {
            return ModList.get().isLoaded("modernui") ? maxWidth * 1.07f : (float) maxWidth;
        }
        boolean shouldWrap(float currentX, float segmentWidth, float maxWidth) {
            return currentX > 0 && currentX + segmentWidth > maxWidth;
        }
        float measureScaledWidth(String s) {
            Float cached = widthCache.get(s);
            if (cached != null) return cached;
            float w = font.width(s) * scale;
            if (w == 0f && s != null && !s.isEmpty()) {
                w = font.width(" ") * scale;
            }
            widthCache.put(s, w);
            return w;
        }

        List<String> splitWordByWidth(String word, float maxWidth) {
            String key = word + "|" + (int) maxWidth;
            List<String> cachedSegments = splitCache.get(key);
            if (cachedSegments != null) return cachedSegments;
            List<String> segments = new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < word.length(); i++) {
                sb.append(word.charAt(i));
                float w = measureScaledWidth(sb.toString());
                if (w > maxWidth && sb.length() > 1) {
                    sb.setLength(sb.length() - 1);
                    segments.add(sb.toString());
                    sb.setLength(0);
                    sb.append(word.charAt(i));
                }
            }
            if (sb.length() > 0) {
                segments.add(sb.toString());
            }
            splitCache.put(key, segments);
            return segments;
        }

        List<String> splitTextToSegments(String text, int renderWidth) {
            List<String> out = new ArrayList<>();
            StringBuilder currentWord = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                boolean isIdeo = Character.isIdeographic(c);
                boolean isAlnum = Character.isLetterOrDigit(c);
                if (isIdeo || !isAlnum) {
                    if (currentWord.length() > 0) {
                        out.addAll(splitWordByWidth(currentWord.toString(), correctedMaxWidth(renderWidth)));
                        currentWord.setLength(0);
                    }
                    String s = String.valueOf(c);
                    if (s.trim().isEmpty()) s = " ";
                    out.addAll(splitWordByWidth(s, correctedMaxWidth(renderWidth)));
                } else {
                    currentWord.append(c);
                }
            }
            if (currentWord.length() > 0) {
                out.addAll(splitWordByWidth(currentWord.toString(), correctedMaxWidth(renderWidth)));
            }
            return out;
        }

        List<TextSegment> segmentByWidth(String word, float maxWidth) {
            List<TextSegment> segments = new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < word.length(); i++) {
                sb.append(word.charAt(i));
                float w = measureScaledWidth(sb.toString());
                if (w > maxWidth && sb.length() > 1) {
                    sb.setLength(sb.length() - 1);
                    String s = sb.toString();
                    segments.add(new TextSegment(s, measureScaledWidth(s)));
                    sb.setLength(0);
                    sb.append(word.charAt(i));
                }
            }
            if (sb.length() > 0) {
                String s = sb.toString();
                segments.add(new TextSegment(s, measureScaledWidth(s)));
            }
            return segments;
        }
    }

    private static class TextSegment {
        final String text;
        final float width;
        TextSegment(String text, float width) { this.text = text; this.width = width; }
    }

    private interface ElementHandler {
        StyleDelta handle(Element element, Style baseStyle, int width);
    }

    private void buildElementHandlers() {
        elementHandlers.put("a", (element, base, width) -> new StyleDelta(Style.LINK_COLOR, element.absUrl("href"), null, null));
        elementHandlers.put("strong", (element, base, width) -> new StyleDelta(null, null, Boolean.TRUE, null));
        elementHandlers.put("b", elementHandlers.get("strong"));
        elementHandlers.put("em", (element, base, width) -> new StyleDelta(null, null, null, Boolean.TRUE));
        elementHandlers.put("i", elementHandlers.get("em"));
        elementHandlers.put("span", (element, base, width) -> handleSpan(element));
        elementHandlers.put("img", (element, base, width) -> { handleImg(element); return null; });
        elementHandlers.put("svg", (element, base, width) -> { handleSvg(element, width); return null; });
        elementHandlers.put("ol", (element, base, width) -> { handleOl(element); return null; });
        elementHandlers.put("ul", (element, base, width) -> { handleUl(element); return null; });
        elementHandlers.put("li", (element, base, width) -> { handleLiStart(width); return null; });
    }

    public int prepare(String htmlContent, int width, String baseUrl) {
        this.renderableLines.clear();
        this.totalHeight = 0;
        this.styleStack = new Stack<>();
        this.styleStack.push(new Style());
        this.currentX = 0;
        this.currentLine = new RenderableLine(font);
        this.renderWidth = width;
        this.widthCache.clear();
        this.layout.clear();


        Document doc = Jsoup.parse(htmlContent, baseUrl);

        // 过滤掉不需要的元素（去重）
        doc.select(".common-text-menu, .uknowtoomuch").remove();

        processNode(doc.body(), width);

        // 添加最后一行
        if (!currentLine.parts.isEmpty()) {
            addLine(currentLine);
        }

        return this.totalHeight;
    }

   public void render(GuiGraphics guiGraphics, int x, int y, int viewportTop, int viewportBottom) {
        int currentY = y;
        for (int i = 0; i < renderableLines.size(); i++) {
            RenderableLine line = renderableLines.get(i);
            int scaledLineHeight = metrics.computeScaledLineHeight(line, scale, lineSpacingFactor);
            if (currentY + scaledLineHeight > viewportTop && currentY < viewportBottom) {
                line.render(guiGraphics, x, currentY, font, scale, this.renderWidth);
            }
            boolean skip = metrics.shouldSkipExtraSpacing(renderableLines, i, scale);
            currentY += skip ? (int)(line.height * scale) : scaledLineHeight + (int)(line.marginBottom * scale);
        }
    }

    public int getContentHeight() {
        return this.totalHeight;
    }

    public StyledPart getPartAt(int x, int y, int renderX, int renderY) {
        return findStyledPartAt(x, y, renderX, renderY);
    }

    public String getLinkUrlAt(int mouseX, int mouseY, int renderX, int renderY) {
        StyledPart part = findStyledPartAt(mouseX, mouseY, renderX, renderY);
        return (part != null && part.style != null) ? part.style.linkUrl : null;
    }

    private StyledPart findStyledPartAt(int x, int y, int renderX, int renderY) {
        int cy = renderY;
        float s = scale;
        for (RenderableLine line : renderableLines) {
            int h = metrics.computeScaledLineHeight(line, scale, lineSpacingFactor);
            if (y >= cy && y < cy + h) {
                StyledPart hit = scanPartsForStyledAt(line, x, renderX, s);
                if (hit != null) return hit;
            }
            cy += h + (int)(line.marginBottom * s);
        }
        return null;
    }

    private StyledPart scanPartsForStyledAt(RenderableLine line, int x, int startX, float s) {
        int scaledCx = startX;
        for (RenderablePart part : line.parts) {
            int partScaledWidth = (int) (part.width * s);
            if (part instanceof StyledPart) {
                if (x >= scaledCx && x < scaledCx + partScaledWidth) {
                    return (StyledPart) part;
                }
            }
            scaledCx += partScaledWidth;
        }
        return null;
    }

    private void addImage(String src, int width, int height) {
        float maxW = layout.correctedMaxWidth(this.renderWidth);
        if (layout.shouldWrap(currentX, width * scale, maxW)) {
            startNewLine(false);
        }

        ResourceLocation cachedTexture = textureCache.get(src);
        int[] size0 = com.cy311.omnisearch.util.ImageManager.getTextureSize(src);
        int texW = size0 != null ? Math.max(1, size0[0]) : Math.max(1, (int)(width / getImageScaleFactor()));
        int texH = size0 != null ? Math.max(1, size0[1]) : Math.max(1, (int)(height / getImageScaleFactor()));
        ImagePart imagePart = new ImagePart(src, width, height, texW, texH, cachedTexture);
        this.currentLine.addPart(imagePart);
        this.currentX += width * scale;

        // If texture was not in cache, load it
        if (cachedTexture == null) {
            ImageManager.getTexture(src, (newTexture) -> {
                imagePart.location = newTexture;
                textureCache.put(src, newTexture);
                int[] s = com.cy311.omnisearch.util.ImageManager.getTextureSize(src);
                if (s != null) { imagePart.texWidth = Math.max(1, s[0]); imagePart.texHeight = Math.max(1, s[1]); }
                if (onUpdate != null) {
                    onUpdate.run();
                }
            });
        }
    }

    private static class ImagePart extends RenderablePart {
        final int imageWidth;
        final int imageHeight;
        int texWidth;
        int texHeight;
        final boolean isSprite;
        ResourceLocation location;
        final String src; // Only for web images

        // Constructor for web images
        ImagePart(String src, int drawWidth, int drawHeight, int texWidth, int texHeight, ResourceLocation texture) {
            super(drawWidth);
            this.imageWidth = drawWidth;
            this.imageHeight = drawHeight;
            this.texWidth = texWidth;
            this.texHeight = texHeight;
            this.location = texture;
            this.isSprite = false;
            this.src = src;
        }

        // Constructor for icons/sprites
        ImagePart(ResourceLocation spriteLocation, int width, int height, boolean isSprite) {
            super(width);
            this.imageWidth = width;
            this.imageHeight = height;
            this.texWidth = width;
            this.texHeight = height;
            this.location = spriteLocation;
            this.isSprite = isSprite;
            this.src = null;
        }
    }

    private void processNode(Node node, int width) {
        if (node instanceof TextNode) {
            processTextNode((TextNode) node, styleStack.peek());
        } else if (node instanceof Element) {
            processElementNode((Element) node, width);
        }
    }

    private void processTextNode(TextNode node, Style style) {
        String text = node.getWholeText();
        processVersionNumbers(text, style);
    }

    private void processVersionNumbers(String text, Style style) {
        Pattern pattern = VERSION_PATTERN;
        Matcher matcher = pattern.matcher(text);
        int lastEnd = 0;
        while (matcher.find()) {
            String precedingText = text.substring(lastEnd, matcher.start());
            if (!precedingText.isEmpty()) {
                addText(precedingText, style);
            }
            String versionNumber = matcher.group(1);
            Style nonBoldStyle = style.withBold(false);
            addText(versionNumber, nonBoldStyle);
            lastEnd = matcher.end();
        }
        if (lastEnd < text.length()) {
            String remainingText = text.substring(lastEnd);
            addText(remainingText, style);
        }
    }

    private void addText(String text, Style style) {
        if (text.trim().isEmpty()) return;
        for (String seg : layout.splitTextToSegments(text, renderWidth)) {
            addWordToLine(seg, style, renderWidth);
        }
    }

    private void addWordToLine(String word, Style style, int maxWidth) {
        float m = layout.correctedMaxWidth(maxWidth);
        if (isWhitespace(word)) word = normalizeWhitespace(word);
        List<TextSegment> segs = layout.segmentByWidth(word, m);
        for (TextSegment seg : segs) {
            if (layout.shouldWrap(currentX, seg.width, m)) startNewLine(false);
            appendWord(seg.text, style, seg.width);
        }
    }


    private boolean isWhitespace(String word) {
        return word.trim().isEmpty();
    }


    private String normalizeWhitespace(String word) {
        return word.trim().isEmpty() ? " " : word;
    }

    private void appendWord(String word, Style style, float scaledWordWidth) {
        currentLine.addPart(new StyledPart(word, style, font));
        currentX += scaledWordWidth;
    }


    

    private void processElementNode(Element element, int width) {
        String tagName = element.tagName();
        if (isBlockTag(tagName)) {
            boolean inList = inOrderedList || inUnorderedList;
            boolean isLiBlock = "li".equals(tagName) && inList;
            boolean isPInsideLi = "p".equals(tagName) && inList && element.parent() != null && "li".equals(element.parent().tagName());
            if (!isLiBlock && !isPInsideLi) {
                startNewLine(true);
            }
        }
        StyleDelta delta = applyElementDelta(element, width);
        for (Node child : element.childNodes()) {
            processNode(child, width);
        }
        revertElementDelta(delta);
        postProcessElementEnd(element, tagName);
    }

    private StyleDelta applyElementDelta(Element element, int width) {
        ElementHandler handler = elementHandlers.get(element.tagName());
        Style base = styleStack.peek();
        StyleDelta delta = handler != null ? handler.handle(element, base, width) : null;
        if (delta != null) {
            styleStack.push(delta.apply(base));
        }
        return delta;
    }

    private void revertElementDelta(StyleDelta delta) {
        if (delta != null) {
            styleStack.pop();
        }
    }

    private boolean isTitleElement(Element element) {
        if (element.hasClass("common-text-title")) return true;
        for (String cls : element.classNames()) {
            if (cls.startsWith("common-text-title-")) return true;
        }
        return false;
    }

    private StyleDelta handleSpan(Element element) {
        boolean isFigcaption = element.hasClass("figcaption");
        if (isFigcaption) {
            startNewLine(false);
            if (currentLine != null) {
                currentLine.isFigcaption = true;
                currentLine.lineType = RenderableLine.LineType.CAPTION_LINE;
            }
            return new StyleDelta(Style.FIGCAPTION_COLOR, null, null, null);
        } else if ( isTitleElement(element) ) {
            startNewLine(false);
            if (currentLine != null) {
                currentLine.lineType = RenderableLine.LineType.TITLE_LINE;
            }
            return new StyleDelta(0xFFE0E0E0, null, Boolean.TRUE, null);
        } else if (element.hasAttr("style")) {
            Integer parsed = StyleParser.parseColorFromStyle(element.attr("style"));
            if (parsed != null) {
                return new StyleDelta(parsed, null, null, null);
            }
        }
        return null;
    }

    private void postProcessElementEnd(Element element, String tagName) {
        if (isBlockTag(tagName)) {
            boolean inList = inOrderedList || inUnorderedList;
            boolean isPInsideLi = "p".equals(tagName) && inList && element.parent() != null && "li".equals(element.parent().tagName());
            if (!isPInsideLi) {
                if (currentLine != null && !currentLine.parts.isEmpty()) {
                    boolean isImageContainer = element.children().size() == 1 && element.child(0).tagName().equals("img");
                    if (!isImageContainer) {
                        currentLine.marginBottom = (int) (font.lineHeight * 0.5f);
                    }
                }
                startNewLine(true);
            }
            return;
        }
        if (tagName.equals("span") && element.hasClass("figcaption")) {
            if (currentLine != null && !currentLine.parts.isEmpty()) {
                currentLine.marginBottom = (int) (font.lineHeight * 0.5f);
            }
            startNewLine(true);
        } else if (tagName.equals("span") && isTitleElement(element)) {
            if (currentLine != null && !currentLine.parts.isEmpty()) {
                currentLine.marginBottom = (int) (font.lineHeight * 0.6f);
            }
            startNewLine(true);
        } else if (tagName.equals("li") && (inOrderedList || inUnorderedList)) {
            
        } else if (tagName.equals("ol") && inOrderedList) {
            inOrderedList = false;
            orderedListIndex = 0;
            startNewLine(true);
        } else if (tagName.equals("ul") && inUnorderedList) {
            inUnorderedList = false;
            startNewLine(true);
        }
    }

    private void handleOl(Element element) {
        String style = element.attr("style");
        if (style != null && style.contains("list-style-type")) {
            if (style.contains("decimal")) {
                orderedListStyle = "decimal";
            } else {
                orderedListStyle = "other";
            }
        } else {
            orderedListStyle = "decimal";
        }
        inOrderedList = true;
        orderedListIndex = 1;
        startNewLine(true);
    }

    private void handleUl(Element element) {
        String style = element.attr("style");
        if (style != null && style.contains("list-style-type")) {
            if (style.contains("disc")) unorderedListStyle = "disc";
            else if (style.contains("circle")) unorderedListStyle = "circle";
            else if (style.contains("square")) unorderedListStyle = "square";
            else unorderedListStyle = "disc";
        } else {
            unorderedListStyle = "disc";
        }
        inUnorderedList = true;
        startNewLine(true);
    }

    private void handleLiStart(int width) {
        if (inOrderedList || inUnorderedList) {
            if (currentLine == null) {
                startNewLine(true);
            }
            String prefix;
            if (inOrderedList) {
                prefix = ("decimal".equals(orderedListStyle) ? (orderedListIndex + ". ") : (orderedListIndex + ") "));
                orderedListIndex++;
            } else {
                if ("circle".equals(unorderedListStyle)) prefix = "◦ ";
                else if ("square".equals(unorderedListStyle)) prefix = "■ ";
                else prefix = "• ";
            }
            Style numStyle = styleStack.peek();
            if (inOrderedList) {
                numStyle = numStyle.withBold(true);
            }
            addText(prefix, numStyle);
            for (int i = 0; i < LIST_INDENT_SPACES; i++) {
                addText(" ", styleStack.peek());
            }
        }
    }

    private static class StyleParser {
        private static final Pattern COLOR_DECL = Pattern.compile("color\\s*:\\s*([^;]+)");
        private static final Pattern HEX6 = Pattern.compile("#([0-9a-fA-F]{6})");
        private static final Pattern HEX3 = Pattern.compile("#([0-9a-fA-F]{3})");
        private static final Pattern RGB = Pattern.compile("rgb\\s*\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*\\)");
        static Integer parseColorFromStyle(String styleAttr) {
            Matcher m = COLOR_DECL.matcher(styleAttr);
            if (!m.find()) return null;
            String colorStr = m.group(1).trim();
            Matcher hex6 = HEX6.matcher(colorStr);
            if (hex6.matches()) {
                int rgb = Integer.parseInt(hex6.group(1), 16);
                return 0xFF000000 | rgb;
            }
            Matcher hex3 = HEX3.matcher(colorStr);
            if (hex3.matches()) {
                String h = hex3.group(1);
                char r = h.charAt(0), g = h.charAt(1), b = h.charAt(2);
                String expanded = "" + r + r + g + g + b + b;
                int rgb = Integer.parseInt(expanded, 16);
                return 0xFF000000 | rgb;
            }
            Matcher rgbm = RGB.matcher(colorStr);
            if (rgbm.matches()) {
                int r = Integer.parseInt(rgbm.group(1));
                int g = Integer.parseInt(rgbm.group(2));
                int b = Integer.parseInt(rgbm.group(3));
                return (0xFF << 24) | (r << 16) | (g << 8) | b;
            }
            return null;
        }
    }

    private static class StyleDelta {
        final Integer color;
        final String linkUrl;
        final Boolean bold;
        final Boolean italic;
        StyleDelta(Integer color, String linkUrl, Boolean bold, Boolean italic) {
            this.color = color;
            this.linkUrl = linkUrl;
            this.bold = bold;
            this.italic = italic;
        }
        Style apply(Style base) {
            Style s = base.copy();
            if (color != null) s.color = color;
            if (linkUrl != null) s.linkUrl = linkUrl;
            if (bold != null) s.isBold = bold;
            if (italic != null) s.isItalic = italic;
            return s;
        }
    }

    private void handleImg(Element element) {
        if (currentLine == null) {
            startNewLine(true);
        } else if (!currentLine.parts.isEmpty()) {
            startNewLine(false);
        }
        String srcAttr = element.hasAttr("data-src") && !element.attr("data-src").isEmpty() ? "data-src" : "src";
        String src = element.attr(srcAttr);

        if (src != null && !src.isEmpty()) {
            String absoluteSrc = element.absUrl(srcAttr);
            int imgWidth = 50; // Default width
            int imgHeight = 50; // Default height
            try {
                if (element.hasAttr("data-width")) {
                    imgWidth = Integer.parseInt(element.attr("data-width"));
                } else if (element.hasAttr("width")) {
                    imgWidth = Integer.parseInt(element.attr("width"));
                }

                if (element.hasAttr("data-height")) {
                    imgHeight = Integer.parseInt(element.attr("data-height"));
                } else if (element.hasAttr("height")) {
                    imgHeight = Integer.parseInt(element.attr("height"));
                }
            } catch (NumberFormatException e) {
                // Ignore
            }
            float baseScale = getImageScaleFactor();
            float maxW = layout.correctedMaxWidth(this.renderWidth);
            int targetW = (int) (imgWidth * baseScale);
            if (targetW > maxW) {
                float clampScale = maxW / (float) imgWidth;
                targetW = (int) (imgWidth * clampScale);
            }
            if (targetW < 1) targetW = 1;
            int targetH = (int) (imgHeight * (targetW / (float) imgWidth));
            if (targetH < 1) targetH = 1;

            addImage(absoluteSrc, targetW, targetH);
        }
    }

    private float getImageScaleFactor() {
        return 0.2f;
    }

    private void handleSvg(Element element, int width) {
        if (element.hasClass("common-mcicon")) {
            Element useElement = element.selectFirst("use");
            if (useElement != null && useElement.hasAttr("xlink:href")) {
                String iconId = useElement.attr("xlink:href").substring(1);
                ImagePart icon = ICONS.get(iconId);
                if (icon != null) {
                    float maxW = layout.correctedMaxWidth(width);
                    if (layout.shouldWrap(currentX, icon.width * scale, maxW)) {
                        startNewLine(false);
                    }
                    currentLine.addPart(icon);
                    currentX += icon.width * scale;
                }
            }
        }
    }

    private void startNewLine(boolean force) {
        if (force || currentX > 0) {
            if (currentLine != null && !currentLine.parts.isEmpty()) {
                addLine(currentLine);
            }
            currentLine = new RenderableLine(font);
            currentX = 0;
            currentLine.isFigcaption = false;
            currentLine.lineType = RenderableLine.LineType.TEXT_LINE;
        }
    }


    private void addLine(RenderableLine line) {
        // 避免添加完全空白的行
        boolean hasContent = false;
        for (RenderablePart part : line.parts) {
            if (part instanceof ImagePart) {
                hasContent = true;
                break;
            }
            if (part instanceof StyledPart && !((StyledPart) part).text.getString().trim().isEmpty()) {
                hasContent = true;
                break;
            }
        }

        if (!hasContent) {
            return;
        }
        this.renderableLines.add(line);
        recalculateTotalHeight();
    }

    private void recalculateTotalHeight() {
        this.totalHeight = metrics.recalculateTotalHeight(this.renderableLines, scale, lineSpacingFactor);
    }

    private static class MetricsCalculator {
        int computeScaledLineHeight(RenderableLine line, float scale, float spacingFactor) {
            float s = scale;
            if (line.lineType == RenderableLine.LineType.CAPTION_LINE) {
                s = scale * 0.7f;
            } else if (line.lineType == RenderableLine.LineType.TITLE_LINE) {
                s = scale * 1.15f;
            }
            return (int)(line.height * s * spacingFactor);
        }
        boolean shouldSkipExtraSpacing(List<RenderableLine> lines, int index, float scale) {
            if (index + 1 >= lines.size()) return false;
            RenderableLine line = lines.get(index);
            RenderableLine next = lines.get(index + 1);
            boolean currentIsImageLine = line.lineType == RenderableLine.LineType.IMAGE_LINE;
            return currentIsImageLine && (next.lineType == RenderableLine.LineType.CAPTION_LINE || next.isFigcaption);
        }
        int recalculateTotalHeight(List<RenderableLine> lines, float scale, float spacingFactor) {
            int h = 0;
            for (RenderableLine line : lines) {
                float s = scale;
                if (line.lineType == RenderableLine.LineType.CAPTION_LINE || line.isFigcaption) {
                    s = scale * 0.7f;
                } else if (line.lineType == RenderableLine.LineType.TITLE_LINE) {
                    s = scale * 1.15f;
                }
                h += (int)(line.height * s * spacingFactor) + (int)(line.marginBottom * s);
            }
            return h;
        }
    }

    private boolean isBlockTag(String tag) {
        return BLOCK_TAGS.contains(tag);
    }
}
