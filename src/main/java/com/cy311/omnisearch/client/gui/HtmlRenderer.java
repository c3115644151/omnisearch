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
import com.cy311.omnisearch.util.McmodFetcher;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlRenderer {
    private static final int PADDING = 2;
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
    }

    private abstract static class RenderablePart {
        public final int width;

        protected RenderablePart(int width) {
            this.width = width;
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
            }
        }

        public void render(GuiGraphics g, int x, int y, Font font, float scale, int renderWidth) {
            if (isFigcaption) {
                renderAsFigcaption(g, x, y, font, scale, renderWidth);
            } else {
                renderAsNormal(g, x, y, font, scale, renderWidth);
            }
        }

        private void renderAsNormal(GuiGraphics g, int x, int y, Font font, float scale, int renderWidth) {
            g.pose().pushPose();
            g.pose().scale(scale, scale, 1.0f);

            int currentX = (int)(x / scale);
            int currentY = (int)(y / scale);

            // Center line if it's a single image
            if (parts.size() == 1 && parts.get(0) instanceof ImagePart) {
                float unscaledRenderWidth = renderWidth / scale;
                currentX += (unscaledRenderWidth - this.totalWidth) / 2;
            }

            for (RenderablePart part : parts) {
                if (part instanceof ImagePart) {
                    ImagePart imagePart = (ImagePart) part;
                    int partY = currentY + (int)((height - imagePart.imageHeight) / 2f);
                    if (imagePart.isSprite) {
                        g.blitSprite(imagePart.location, currentX, partY, imagePart.imageWidth, imagePart.imageHeight);
                    } else if (imagePart.location != null) {
                        g.blit(imagePart.location, currentX, partY, 0, 0, imagePart.imageWidth, imagePart.imageHeight, imagePart.imageWidth, imagePart.imageHeight);
                    }
                    currentX += imagePart.width;
                } else if (part instanceof StyledPart) {
                    StyledPart styledPart = (StyledPart) part;
                    int textY = currentY + (int)((height - font.lineHeight) / 2f);
                    g.drawString(font, styledPart.text, currentX, textY, styledPart.style.color);
                    currentX += styledPart.width;
                }
            }
            g.pose().popPose();
        }

        private void renderAsFigcaption(GuiGraphics g, int x, int y, Font font, float scale, int renderWidth) {
            float captionTextScale = 0.7f;
            float finalScale = scale * captionTextScale;

            float totalCaptionScreenWidth = this.totalWidth * finalScale;
            float offsetX = (renderWidth - totalCaptionScreenWidth) / 2.0f;

            g.pose().pushPose();
            g.pose().translate(x + offsetX, y, 0);
            g.pose().scale(finalScale, finalScale, 1.0f);

            int currentX = 0;
            int currentY = 0;

            for (RenderablePart part : parts) {
                if (part instanceof StyledPart) {
                    StyledPart styledPart = (StyledPart) part;
                    int textY = currentY + (int)((height - font.lineHeight) / 2f);
                    g.drawString(font, styledPart.text, currentX, textY, styledPart.style.color);
                    currentX += styledPart.width;
                }
            }
            g.pose().popPose();
        }
    }

    private final List<RenderableLine> renderableLines = new ArrayList<>();
    private int totalHeight = 0;
    private final float scale = 0.85f;
    private final float lineSpacingFactor = 1.2f;

    // 解析状态
    private RenderableLine currentLine;
    private int currentX;
    private Stack<Style> styleStack;
    private int renderWidth;
    private final Font font;


    public HtmlRenderer(Runnable onUpdate) {
        this.font = Minecraft.getInstance().font;
        this.onUpdate = onUpdate;
    }

    public int prepare(String htmlContent, int width, String baseUrl) {
        this.renderableLines.clear();
        this.totalHeight = 0;
        this.styleStack = new Stack<>();
        this.styleStack.push(new Style());
        this.currentX = 0;
        this.currentLine = new RenderableLine(font);
        this.renderWidth = width;


        Document doc = Jsoup.parse(htmlContent, baseUrl);

        // 过滤掉不需要的元素
        doc.select(".common-text-menu").remove(); // 移除目录
        doc.select(".uknowtoomuch").remove();     // 移除吐槽

        // 过滤掉不需要的元素
        doc.select(".common-text-menu").remove(); // 移除目录
        doc.select(".uknowtoomuch").remove();     // 移除吐槽

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

            int scaledLineHeight = (int)(line.height * scale * lineSpacingFactor);
            if (currentY + scaledLineHeight > viewportTop && currentY < viewportBottom) {
                line.render(guiGraphics, x, currentY, font, scale, this.renderWidth);
            }

            // Check if we should skip the extra spacing for an image followed by a figcaption
            boolean skipExtraSpacing = false;
            if (i + 1 < renderableLines.size()) {
                RenderableLine nextLine = renderableLines.get(i + 1);
                boolean isCurrentImageLine = line.parts.size() == 1 && line.parts.get(0) instanceof ImagePart;
                if (isCurrentImageLine && nextLine.isFigcaption) {
                    skipExtraSpacing = true;
                }
            }

            if (skipExtraSpacing) {
                // For an image followed by a figcaption, only add the image's actual scaled height,
                // ignoring both lineSpacingFactor and marginBottom.
                currentY += (int)(line.height * scale);
            } else {
                currentY += scaledLineHeight + (int)(line.marginBottom * scale);
            }
        }
    }

    public int getContentHeight() {
        return this.totalHeight;
    }

    public StyledPart getPartAt(int x, int y, int renderX, int renderY) {
        int currentY = renderY;
        for (RenderableLine line : renderableLines) {
            int scaledLineHeight = (int)(line.height * scale * lineSpacingFactor);
            if (y >= currentY && y < currentY + scaledLineHeight) {
                int currentX = (int) (renderX / scale);
                for (RenderablePart part : line.parts) {
                    if (part instanceof StyledPart) {
                        StyledPart styledPart = (StyledPart) part;
                        int partWidth = styledPart.width;
                        if (x >= currentX * scale && x < (currentX + partWidth) * scale) {
                            return styledPart;
                        }
                    }
                    currentX += part.width;
                }
            }
            currentY += scaledLineHeight + (int)(line.marginBottom * scale);
        }
        return null;
    }

    public String getLinkUrlAt(int mouseX, int mouseY, int renderX, int renderY) {
        StyledPart part = getPartAt(mouseX, mouseY, renderX, renderY);
        if (part != null && part.style != null && part.style.linkUrl != null) {
            return part.style.linkUrl;
        }
        return null;
    }

    private void addImage(String src, int width, int height) {
        

        // Check cache first
        ResourceLocation cachedTexture = textureCache.get(src);
        ImagePart imagePart = new ImagePart(src, width, height, cachedTexture); // Create with cached texture if available
        this.currentLine.addPart(imagePart);
        this.currentX += width * scale;

        // If texture was not in cache, load it
        if (cachedTexture == null) {
            ImageManager.getTexture(src, (newTexture) -> {
                imagePart.location = newTexture;
                textureCache.put(src, newTexture); // Cache the newly loaded texture
                if (onUpdate != null) {
                    onUpdate.run();
                }
            });
        }
    }

    private static class ImagePart extends RenderablePart {
        final int imageWidth;
        final int imageHeight;
        final boolean isSprite;
        ResourceLocation location;
        final String src; // Only for web images

        // Constructor for web images
        ImagePart(String src, int width, int height, ResourceLocation texture) {
            super(width);
            this.imageWidth = width;
            this.imageHeight = height;
            this.location = texture;
            this.isSprite = false;
            this.src = src;
        }

        // Constructor for icons/sprites
        ImagePart(ResourceLocation spriteLocation, int width, int height, boolean isSprite) {
            super(width);
            this.imageWidth = width;
            this.imageHeight = height;
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
        Pattern pattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+)");
        Matcher matcher = pattern.matcher(text);

        int lastEnd = 0;
        while (matcher.find()) {
            String precedingText = text.substring(lastEnd, matcher.start());
            if (!precedingText.isEmpty()) {
                addText(precedingText, style);
            }

            String versionNumber = matcher.group(1);
            Style nonBoldStyle = style.copy();
            nonBoldStyle.isBold = false;
            addText(versionNumber, nonBoldStyle);

            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            String remainingText = text.substring(lastEnd);
            addText(remainingText, style);
        }
    }

    private void addText(String text, Style style) {
        if (text.trim().isEmpty()) {
            return;
        }

        // Re-implementing the text processing logic to handle CJK characters and mixed-language text better.
        StringBuilder currentWord = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // CJK characters, punctuation, and other symbols are treated as individual words.
            if (Character.isIdeographic(c) || !Character.isLetterOrDigit(c)) {
                // First, process the accumulated word.
                if (currentWord.length() > 0) {
                    addWordToLine(currentWord.toString(), style, renderWidth); // Use renderWidth
                    currentWord.setLength(0);
                }
                // Then, process the current character as a word.
                addWordToLine(String.valueOf(c), style, renderWidth); // Use renderWidth
            } else {
                // Accumulate letters and digits into a word.
                currentWord.append(c);
            }
        }
        // Add the last accumulated word if it exists.
        if (currentWord.length() > 0) {
            addWordToLine(currentWord.toString(), style, renderWidth); // Use renderWidth
        }
    }

    private void addWordToLine(String word, Style style, int maxWidth) {
        // ModernUI has different font metrics, which makes our width calculation based on vanilla fonts inaccurate.
        // This can cause lines to overflow. To compensate, we apply a correction factor to the max width
        // only when ModernUI is loaded. This makes the line wrap earlier, fixing the layout issue.
        float widthCorrectionFactor = ModList.get().isLoaded("modernui") ? 0.95f : 1.0f;
        float correctedMaxWidth = maxWidth * widthCorrectionFactor;

        if (word.trim().isEmpty()) { // Handle whitespace
            int spaceWidth = font.width(word);
            if (currentX + spaceWidth * scale <= correctedMaxWidth) {
                currentLine.addPart(new StyledPart(word, style, font));
                currentX += spaceWidth * scale;
            }
            return;
        }

        int wordWidth = font.width(word);
        float scaledWordWidth = wordWidth * scale;

        if (currentX > 0 && currentX + scaledWordWidth > correctedMaxWidth) {
            startNewLine(false);
        }

        if (scaledWordWidth > correctedMaxWidth) { // Word is longer than a line
            for (char c : word.toCharArray()) {
                String character = String.valueOf(c);
                int charWidth = font.width(character);
                float scaledCharWidth = charWidth * scale;

                if (currentX > 0 && currentX + scaledCharWidth > correctedMaxWidth) {
                    startNewLine(false);
                }
                currentLine.addPart(new StyledPart(character, style, font));
                currentX += scaledCharWidth;
            }
        } else { // Word fits on the current line (or a new line)
            currentLine.addPart(new StyledPart(word, style, font));
            currentX += scaledWordWidth;
        }
    }

    private void processElementNode(Element element, int width) {
        String tagName = element.tagName();
        

        Style newStyle = styleStack.peek().copy();
        boolean styleChanged = false;

        if (isBlockTag(tagName)) {
            startNewLine(true);
        }

        switch (tagName) {
            case "a":
                newStyle.color = Style.LINK_COLOR;
                newStyle.linkUrl = element.absUrl("href");
                styleChanged = true;
                break;
            case "strong":
            case "b":
                newStyle.isBold = true;
                styleChanged = true;
                break;
            case "em":
            case "i":
                newStyle.isItalic = true;
                styleChanged = true;
                break;
            case "span":
                styleChanged = handleSpan(element, newStyle);
                break;
            case "img":
                handleImg(element);
                break;
            case "svg":
                handleSvg(element, width);
                break;
        }

        if (styleChanged) {
            styleStack.push(newStyle);
        }

        for (Node child : element.childNodes()) {
            processNode(child, width);
        }

        if (styleChanged) {
            styleStack.pop();
        }

        if (isBlockTag(tagName)) {
            if (currentLine != null && !currentLine.parts.isEmpty()) {
                // If the block element only contains a single image, don't add a bottom margin.
                boolean isImageContainer = element.children().size() == 1 && element.child(0).tagName().equals("img");
                if (!isImageContainer) {
                    currentLine.marginBottom = (int) (font.lineHeight * 0.5f);
                }
            }
            startNewLine(true);
        } else if (tagName.equals("span") && element.hasClass("figcaption")) {
            if (currentLine != null && !currentLine.parts.isEmpty()) {
                // Keep the original logic for figcaptions (margin after)
                currentLine.marginBottom = (int) (font.lineHeight * 0.5f);
            }
            startNewLine(true);
        }
    }

    private boolean handleSpan(Element element, Style newStyle) {
        boolean isFigcaption = element.hasClass("figcaption");
        if (isFigcaption) {
            startNewLine(false);
            if (currentLine != null) { 
                currentLine.isFigcaption = true;
            }
            newStyle.color = Style.FIGCAPTION_COLOR;
            return true;
        } else if (element.hasAttr("style")) {
            String styleAttr = element.attr("style");
            if (styleAttr.contains("color")) {
                try {
                    String colorStr = styleAttr.split("color:")[1].split(";")[0].trim();
                    if (colorStr.startsWith("#")) {
                        newStyle.color = Integer.parseInt(colorStr.substring(1), 16) | 0xFF000000;
                        return true;
                    } else if (colorStr.startsWith("rgb")) {
                        String[] rgb = colorStr.substring(colorStr.indexOf('(') + 1, colorStr.indexOf(')')).split(",");
                        int r = Integer.parseInt(rgb[0].trim());
                        int g = Integer.parseInt(rgb[1].trim());
                        int b = Integer.parseInt(rgb[2].trim());
                        newStyle.color = (0xFF << 24) | (r << 16) | (g << 8) | b;
                        return true;
                    }
                } catch (Exception e) { /* ignore parse errors */ }
            }
        }
        return false;
    }

    private void handleImg(Element element) {
        if (currentLine != null && !currentLine.parts.isEmpty()) {
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
            float imageScaleFactor = 0.2f;
            int scaledWidth = (int) (imgWidth * imageScaleFactor);
            int scaledHeight = (int) (imgHeight * imageScaleFactor);

            addImage(absoluteSrc, scaledWidth, scaledHeight);
        }
    }

    private void handleSvg(Element element, int width) {
        if (element.hasClass("common-mcicon")) {
            Element useElement = element.selectFirst("use");
            if (useElement != null && useElement.hasAttr("xlink:href")) {
                String iconId = useElement.attr("xlink:href").substring(1);
                ImagePart icon = ICONS.get(iconId);
                if (icon != null) {
                    if (currentX + icon.width * scale > width) {
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
        int newTotalHeight = 0;
        for (RenderableLine line : this.renderableLines) {
            float finalScale = this.scale;
            if (line.isFigcaption) {
                finalScale *= 0.7f; // Use the same scale as in renderAsFigcaption
            }
            newTotalHeight += (int)(line.height * finalScale * lineSpacingFactor) + (int)(line.marginBottom * finalScale);
        }
        this.totalHeight = newTotalHeight;
    }

    private boolean isBlockTag(String tag) {
        return tag.equals("p") || tag.equals("h1") || tag.equals("h2") || tag.equals("h3") || tag.equals("h4") || tag.equals("h5") || tag.equals("div") || tag.equals("ul") || tag.equals("li") || tag.equals("br") || tag.equals("table");
    }
}