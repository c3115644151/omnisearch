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
import net.minecraftforge.fml.ModList;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlRenderer {
    // Copied and adjusted ModList import for Forge
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+\\.\\d+\\.\\d+)");
    private static final Set<String> BLOCK_TAGS = new java.util.HashSet<>(Arrays.asList("p","h1","h2","h3","h4","h5","div","ul","li","br","table"));
    private static final int PADDING = 2;
    private static final int LIST_INDENT_SPACES = 2;
    private final Runnable onUpdate;
    private static final Map<String, ResourceLocation> textureCache = new HashMap<>();
    private static final Map<String, ImagePart> ICONS = new HashMap<>();
    static {
        ResourceLocation heartFull = ImageManager.getGeneratedIcon("icon-health-full");
        ResourceLocation heartHalf = ImageManager.getGeneratedIcon("icon-health-half");
        ResourceLocation heartEmpty = ImageManager.getGeneratedIcon("icon-health-empty");
        ResourceLocation armorFull = ImageManager.getGeneratedIcon("icon-armor-full");
        ResourceLocation armorHalf = ImageManager.getGeneratedIcon("icon-armor-half");
        ResourceLocation armorEmpty = ImageManager.getGeneratedIcon("icon-armor-empty");
        ResourceLocation toughFull = ImageManager.getGeneratedIcon("icon-toughness-full");
        ResourceLocation toughHalf = ImageManager.getGeneratedIcon("icon-toughness-half");
        ResourceLocation toughEmpty = ImageManager.getGeneratedIcon("icon-toughness-empty");
        ResourceLocation foodFull = ImageManager.getGeneratedIcon("icon-food-full");
        ResourceLocation foodHalf = ImageManager.getGeneratedIcon("icon-food-half");
        ResourceLocation foodEmpty = ImageManager.getGeneratedIcon("icon-food-empty");

        ICONS.put("icon-health-full", new ImagePart(heartFull, 9, 9, false));
        ICONS.put("icon-health-half", new ImagePart(heartHalf, 9, 9, false));
        ICONS.put("icon-health-empty", new ImagePart(heartEmpty, 9, 9, false));
        ICONS.put("icon-heart-full", new ImagePart(heartFull, 9, 9, false));
        ICONS.put("icon-heart-half", new ImagePart(heartHalf, 9, 9, false));
        ICONS.put("icon-heart-empty", new ImagePart(heartEmpty, 9, 9, false));
        ICONS.put("icon-armor-full", new ImagePart(armorFull, 9, 9, false));
        ICONS.put("icon-armor-half", new ImagePart(armorHalf, 9, 9, false));
        ICONS.put("icon-armor-empty", new ImagePart(armorEmpty, 9, 9, false));
        ICONS.put("icon-toughness-full", new ImagePart(toughFull, 9, 9, false));
        ICONS.put("icon-toughness-half", new ImagePart(toughHalf, 9, 9, false));
        ICONS.put("icon-toughness-empty", new ImagePart(toughEmpty, 9, 9, false));
        ICONS.put("icon-food-full", new ImagePart(foodFull, 9, 9, false));
        ICONS.put("icon-food-half", new ImagePart(foodHalf, 9, 9, false));
        ICONS.put("icon-food-empty", new ImagePart(foodEmpty, 9, 9, false));
    }
    private static class Style { public static final int DEFAULT_COLOR = 0xFFD0D0D0; public static final int LINK_COLOR = 0xFF9090FF; public static final int FIGCAPTION_COLOR = 0xFF9E9E9E; public int color; public String linkUrl; public boolean isBold = false; public boolean isItalic = false; public Style() { this.color = DEFAULT_COLOR; this.linkUrl = null; } public Style copy() { Style s = new Style(); s.color = this.color; s.linkUrl = this.linkUrl; s.isBold = this.isBold; s.isItalic = this.isItalic; return s; } public Style withBold(boolean b){Style s=copy();s.isBold=b;return s;} public Style withItalic(boolean i){Style s=copy();s.isItalic=i;return s;} public Style withColor(int c){Style s=copy();s.color=c;return s;} public Style withLink(String u){Style s=copy();s.linkUrl=u;return s;} }
    private abstract static class RenderablePart { public final int width; protected RenderablePart(int width){this.width=width;} }
    private static class CaptionMetrics { final float offsetX; final float finalScale; CaptionMetrics(float o,float f){offsetX=o;finalScale=f;} }
    private static class Painter {
        static void renderLine(RenderContext ctx, RenderableLine line, int x, int y) { if (line.isFigcaption) return; GuiGraphics g = ctx.g; Font font=ctx.font; float scale=ctx.scale; if (line.lineType==RenderableLine.LineType.TITLE_LINE) scale*=1.15f; int renderWidth=ctx.renderWidth; g.pose().pushPose(); g.pose().scale(scale, scale, 1.0f); int currentX=(int)(x/scale); int currentY=(int)(y/scale); if (line.lineType==RenderableLine.LineType.IMAGE_LINE) { currentX += computeCenterOffset(line, renderWidth/scale); } if (line.lineType==RenderableLine.LineType.TITLE_LINE){ int padX=3; int padY=2; int bx0=currentX-padX; int by0=currentY-padY; int bx1=currentX+line.totalWidth+padX; int by1=currentY+line.height+padY-1; g.fill(bx0,by0,bx1,by1,0x22151518);} if (line.lineType==RenderableLine.LineType.TITLE_LINE){ int ux0=currentX; int ux1=currentX+Math.max(line.totalWidth,(int)((renderWidth/scale)*0.60f)); int uy0=currentY+line.height+1; int uy1=uy0+1; int gradientW=Math.min(16, ux1-ux0); int baseEnd=ux1-gradientW; if (baseEnd>ux0){ g.fill(ux0,uy0,baseEnd,uy1,0x33454A53);} for(int i=0;i<gradientW;i++){ float t=(float)i/(float)gradientW; int alpha=(int)(0x44*(1.0f-t)); int color=(alpha<<24)|0x00454A53; int px=baseEnd+i; g.fill(px,uy0,px+1,uy1,color);} }
            for (RenderablePart part : line.parts) { if (part instanceof ImagePart) { currentX = renderImagePart(ctx, line, (ImagePart) part, currentX, currentY); } else if (part instanceof StyledPart) { currentX = renderStyledPart(ctx, line, (StyledPart) part, currentX, currentY); } }
            g.pose().popPose(); }
        static void renderFigcaption(RenderContext ctx, RenderableLine line, int x, int y, CaptionMetrics m){ GuiGraphics g=ctx.g; Font font=ctx.font; g.pose().pushPose(); g.pose().translate(x+m.offsetX, y, 0); g.pose().scale(m.finalScale, m.finalScale, 1.0f); int currentX=0; int currentY=0; for(RenderablePart part: line.parts){ if(part instanceof StyledPart){ StyledPart sp=(StyledPart)part; int textY=currentY+(int)((line.height-font.lineHeight)/2f); g.drawString(font, sp.text, currentX, textY, sp.style.color); currentX+=sp.width; } } g.pose().popPose(); }
        private static int computeCenterOffset(RenderableLine line, float unscaledRenderWidth){ return (int)((unscaledRenderWidth - line.totalWidth)/2); }
        private static int renderImagePart(RenderContext ctx, RenderableLine line, ImagePart imagePart, int currentX, int currentY){ GuiGraphics g=ctx.g; int partY=currentY+(int)((line.height-imagePart.imageHeight)/2f); if (imagePart.u != null) { RenderCompat.blitTextureUVLogged(g, imagePart.location, currentX, partY, imagePart.u, imagePart.v, imagePart.imageWidth, imagePart.imageHeight, imagePart.texW != null ? imagePart.texW : 256, imagePart.texH != null ? imagePart.texH : 256, imagePart.tag != null ? imagePart.tag : ""); } else if (imagePart.isSprite){ boolean ok = RenderCompat.blitHudSprite(g, imagePart.location, currentX, partY, imagePart.imageWidth, imagePart.imageHeight); if(!ok){ com.cy311.omnisearch.OmnisearchLogger.info("sprite draw failed: " + imagePart.location); } } else if (imagePart.location != null) { g.blit(imagePart.location, currentX, partY, 0, 0, imagePart.imageWidth, imagePart.imageHeight, imagePart.imageWidth, imagePart.imageHeight); } return currentX + imagePart.width; }
        private static int renderStyledPart(RenderContext ctx, RenderableLine line, StyledPart styledPart, int currentX, int currentY){ Font font=ctx.font; GuiGraphics g=ctx.g; int textY=currentY+(int)((line.height-font.lineHeight)/2f); g.drawString(font, styledPart.text, currentX, textY, styledPart.style.color); return currentX + styledPart.width; }
    }
    private static class RenderContext { final GuiGraphics g; final Font font; final float scale; final int renderWidth; RenderContext(GuiGraphics g, Font font, float scale, int renderWidth){ this.g=g; this.font=font; this.scale=scale; this.renderWidth=renderWidth; } }
    private static class StyledPart extends RenderablePart { public final Component text; public final Style style; public StyledPart(String text, Style style, Font font){ super(font.width(text)); this.style=style; MutableComponent c=Component.literal(text); if(style.isBold) c.withStyle(s->s.withBold(true)); if(style.isItalic) c.withStyle(s->s.withItalic(true)); this.text=c; } }
    private static class RenderableLine { public final List<RenderablePart> parts = new ArrayList<>(); public int height; public int totalWidth=0; public boolean isFigcaption=false; public int marginBottom=0; enum LineType{ TEXT_LINE, IMAGE_LINE, CAPTION_LINE, TITLE_LINE } public LineType lineType = LineType.TEXT_LINE; public RenderableLine(Font font){ this.height = font.lineHeight; } public void addPart(RenderablePart part){ parts.add(part); totalWidth += part.width; if (part instanceof ImagePart){ int ih = ((ImagePart)part).imageHeight; if (ih > this.height) this.height = ih; this.lineType = LineType.IMAGE_LINE; } }
        public void render(GuiGraphics g, int x, int y, Font font, float scale, int renderWidth){ RenderContext ctx = new RenderContext(g, font, scale, renderWidth); if (isFigcaption){ CaptionMetrics m = computeCaptionOffsetAndScale(scale, renderWidth); Painter.renderFigcaption(ctx, this, x, y, m); } else { Painter.renderLine(ctx, this, x, y); } }
        private CaptionMetrics computeCaptionOffsetAndScale(float scale, int renderWidth){ float sMin=0.65f*scale; float sMax=0.85f*scale; float s=0.7f*scale; float maxByWidth = this.totalWidth>0 ? (float)renderWidth/this.totalWidth : s; if (s>maxByWidth) s=maxByWidth; if (s<sMin) s=sMin; if (s>sMax) s=sMax; float w=this.totalWidth*s; float offsetX = Math.round((renderWidth - w)/2.0f); return new CaptionMetrics(offsetX, s);} }
    private final List<RenderableLine> renderableLines = new ArrayList<>(); private int totalHeight=0; private final float scale=0.75f; private final float lineSpacingFactor=1.2f; private final MetricsCalculator metrics = new MetricsCalculator(); private RenderableLine currentLine; private int currentX; private Stack<Style> styleStack; private int renderWidth; private final Font font; private final Map<String, ElementHandler> elementHandlers = new HashMap<>(); private final Map<String, Float> widthCache = new HashMap<>(); private final LayoutEngine layout = new LayoutEngine(); private boolean inOrderedList=false; private int orderedListIndex=0; private String orderedListStyle="decimal"; private boolean inUnorderedList=false; private String unorderedListStyle="disc";
    public HtmlRenderer(Runnable onUpdate){ this.font = Minecraft.getInstance().font; this.onUpdate = onUpdate; buildElementHandlers(); }
    private class LayoutEngine { private final Map<String, List<String>> splitCache = new HashMap<>(); void clear(){ splitCache.clear(); } float correctedMaxWidth(int maxWidth){ return ModList.get().isLoaded("modernui") ? maxWidth * 1.07f : (float) maxWidth; } boolean shouldWrap(float currentX, float segmentWidth, float maxWidth){ return currentX>0 && currentX + segmentWidth > maxWidth; } float measureScaledWidth(String s){ Float cached = widthCache.get(s); if (cached!=null) return cached; float w = font.width(s) * scale; if (w == 0f && s != null && !s.isEmpty()) { w = font.width(" ") * scale; } widthCache.put(s, w); return w; } List<String> splitWordByWidth(String word, float maxWidth){ String key=word+"|"+(int)maxWidth; List<String> cached=splitCache.get(key); if(cached!=null) return cached; List<String> segments=new ArrayList<>(); StringBuilder sb=new StringBuilder(); for(int i=0;i<word.length();i++){ sb.append(word.charAt(i)); float w=measureScaledWidth(sb.toString()); if (w > maxWidth && sb.length()>1){ sb.setLength(sb.length()-1); segments.add(sb.toString()); sb.setLength(0); sb.append(word.charAt(i)); } } if (sb.length()>0){ segments.add(sb.toString()); } splitCache.put(key, segments); return segments; } List<String> splitTextToSegments(String text, int renderWidth){ List<String> out=new ArrayList<>(); StringBuilder currentWord=new StringBuilder(); for(int i=0;i<text.length();i++){ char c=text.charAt(i); boolean isIdeo = Character.isIdeographic(c); boolean isAlnum = Character.isLetterOrDigit(c); if (isIdeo || !isAlnum){ if (currentWord.length()>0){ out.addAll(splitWordByWidth(currentWord.toString(), correctedMaxWidth(renderWidth))); currentWord.setLength(0); } String s=String.valueOf(c); if (s.trim().isEmpty()) s=" "; out.addAll(splitWordByWidth(s, correctedMaxWidth(renderWidth))); } else { currentWord.append(c); } } if (currentWord.length()>0){ out.addAll(splitWordByWidth(currentWord.toString(), correctedMaxWidth(renderWidth))); } return out; } List<TextSegment> segmentByWidth(String word, float maxWidth){ List<TextSegment> segs=new ArrayList<>(); StringBuilder sb=new StringBuilder(); for(int i=0;i<word.length();i++){ sb.append(word.charAt(i)); float w=measureScaledWidth(sb.toString()); if (w > maxWidth && sb.length()>1){ sb.setLength(sb.length()-1); String s=sb.toString(); segs.add(new TextSegment(s, measureScaledWidth(s))); sb.setLength(0); sb.append(word.charAt(i)); } } if (sb.length()>0){ String s=sb.toString(); segs.add(new TextSegment(s, measureScaledWidth(s))); } return segs; } }
    private static class TextSegment{ final String text; final float width; TextSegment(String t, float w){ text=t; width=w; }}
    private interface ElementHandler { StyleDelta handle(Element element, Style baseStyle, int width); }
    private void buildElementHandlers(){ elementHandlers.put("a", (element, base, width) -> new StyleDelta(Style.LINK_COLOR, element.absUrl("href"), null, null)); elementHandlers.put("strong", (element, base, width) -> new StyleDelta(null,null,Boolean.TRUE,null)); elementHandlers.put("b", elementHandlers.get("strong")); elementHandlers.put("em", (element, base, width) -> new StyleDelta(null,null,null,Boolean.TRUE)); elementHandlers.put("i", elementHandlers.get("em")); elementHandlers.put("span", (element, base, width) -> handleSpan(element)); elementHandlers.put("img", (element, base, width) -> { handleImg(element); return null; }); elementHandlers.put("svg", (element, base, width) -> { handleSvg(element, width); return null; }); elementHandlers.put("ol", (element, base, width) -> { handleOl(element); return null; }); elementHandlers.put("ul", (element, base, width) -> { handleUl(element); return null; }); elementHandlers.put("li", (element, base, width) -> { handleLiStart(width); return null; }); }
    public int prepare(String htmlContent, int width, String baseUrl){ this.renderableLines.clear(); this.totalHeight=0; this.styleStack=new Stack<>(); this.styleStack.push(new Style()); this.currentX=0; this.currentLine=new RenderableLine(font); this.renderWidth=width; this.widthCache.clear(); this.layout.clear(); Document doc = Jsoup.parse(htmlContent, baseUrl); doc.select(".common-text-menu, .uknowtoomuch").remove(); processNode(doc.body(), width); if (!currentLine.parts.isEmpty()) { addLine(currentLine); } return this.totalHeight; }
    public void render(GuiGraphics guiGraphics, int x, int y, int viewportTop, int viewportBottom){ int currentY=y; for(int i=0;i<renderableLines.size();i++){ RenderableLine line=renderableLines.get(i); int scaledLineHeight = metrics.computeScaledLineHeight(line, scale, lineSpacingFactor); if (currentY + scaledLineHeight > viewportTop && currentY < viewportBottom) { line.render(guiGraphics, x, currentY, font, scale, this.renderWidth); } boolean skip = metrics.shouldSkipExtraSpacing(renderableLines, i, scale); currentY += skip ? (int)(line.height * scale) : scaledLineHeight + (int)(line.marginBottom * scale); } }
    public int getContentHeight(){ return this.totalHeight; }
    public StyledPart getPartAt(int x, int y, int renderX, int renderY){ return findStyledPartAt(x,y,renderX,renderY); }
    public String getLinkUrlAt(int mouseX, int mouseY, int renderX, int renderY){ StyledPart part = findStyledPartAt(mouseX, mouseY, renderX, renderY); return (part != null && part.style != null) ? part.style.linkUrl : null; }
    private StyledPart findStyledPartAt(int x, int y, int renderX, int renderY){ int cy=renderY; float s=scale; for(RenderableLine line: renderableLines){ int h = metrics.computeScaledLineHeight(line, scale, lineSpacingFactor); if (y >= cy && y < cy + h){ StyledPart hit = scanPartsForStyledAt(line, x, renderX, s); if (hit != null) return hit; } cy += h + (int)(line.marginBottom * s); } return null; }
    private StyledPart scanPartsForStyledAt(RenderableLine line, int x, int startX, float s){ int scaledCx = startX; for (RenderablePart part : line.parts){ int partScaledWidth = (int)(part.width * s); if (part instanceof StyledPart){ if (x >= scaledCx && x < scaledCx + partScaledWidth){ return (StyledPart)part; } } scaledCx += partScaledWidth; } return null; }
    private void addImage(String src, int width, int height){
        float maxW = layout.correctedMaxWidth(this.renderWidth);
        if (layout.shouldWrap(currentX, width * scale, maxW)) { startNewLine(false); }
        ResourceLocation cachedTexture = textureCache.get(src);
        ImagePart imagePart = new ImagePart(src, width, height, cachedTexture);
        this.currentLine.addPart(imagePart);
        this.currentX += width * scale;
        if (cachedTexture == null){
            ResourceLocation pending = ImageManager.getTexture(src, (newTexture)->{
                imagePart.location = newTexture;
                textureCache.put(src, newTexture);
                if (onUpdate != null) { onUpdate.run(); }
            });
            if (imagePart.location == null) { imagePart.location = pending; }
            com.cy311.omnisearch.OmnisearchLogger.info("addImage src=" + src);
        }
    }
    private static class ImagePart extends RenderablePart { final int imageWidth; final int imageHeight; final boolean isSprite; ResourceLocation location; final String src; Integer u,v,texW,texH; String tag; ImagePart(String src, int width, int height, ResourceLocation texture){ super(width); this.imageWidth=width; this.imageHeight=height; this.location=texture; this.isSprite=false; this.src=src; } ImagePart(ResourceLocation spriteLocation, int width, int height, boolean isSprite){ super(width); this.imageWidth=width; this.imageHeight=height; this.location=spriteLocation; this.isSprite=isSprite; this.src=null; } ImagePart(ResourceLocation loc, int width, int height, int u, int v, int texW, int texH, String tag){ super(width); this.imageWidth=width; this.imageHeight=height; this.location=loc; this.isSprite=false; this.src=null; this.u=u; this.v=v; this.texW=texW; this.texH=texH; this.tag=tag; } }
    private void processNode(org.jsoup.nodes.Node node, int width){ if (node instanceof TextNode){ processTextNode((TextNode)node, styleStack.peek()); } else if (node instanceof Element){ processElementNode((Element)node, width); } }
    private void processTextNode(TextNode node, Style style){ String text = node.getWholeText(); processVersionNumbers(text, style); }
    private void processVersionNumbers(String text, Style style){ Pattern pattern = VERSION_PATTERN; Matcher matcher = pattern.matcher(text); int lastEnd=0; while(matcher.find()){ String precedingText=text.substring(lastEnd, matcher.start()); if (!precedingText.isEmpty()){ addText(precedingText, style); } String versionNumber = matcher.group(1); Style nonBoldStyle = style.withBold(false); addText(versionNumber, nonBoldStyle); lastEnd = matcher.end(); } if (lastEnd < text.length()){ String remainingText = text.substring(lastEnd); addText(remainingText, style); } }
    private void addText(String text, Style style){ if (text.trim().isEmpty()) return; for (String seg : layout.splitTextToSegments(text, renderWidth)){ addWordToLine(seg, style, renderWidth); } }
    private void addWordToLine(String word, Style style, int maxWidth){ float m = layout.correctedMaxWidth(maxWidth); if (isWhitespace(word)) word = normalizeWhitespace(word); List<TextSegment> segs = layout.segmentByWidth(word, m); for (TextSegment seg : segs){ if (layout.shouldWrap(currentX, seg.width, m)) startNewLine(false); appendWord(seg.text, style, seg.width); } }
    private boolean isWhitespace(String word){ return word.trim().isEmpty(); }
    private String normalizeWhitespace(String word){ return word.trim().isEmpty() ? " " : word; }
    private void appendWord(String word, Style style, float scaledWordWidth){ currentLine.addPart(new StyledPart(word, style, font)); currentX += scaledWordWidth; }
    private void handleElementEnd(Element element, String tagName){ boolean inList = inOrderedList || inUnorderedList; boolean isPInsideLi = "p".equals(tagName) && inList && element.parent()!=null && "li".equals(element.parent().tagName()); if (!isPInsideLi){ if (currentLine!=null && !currentLine.parts.isEmpty()){ boolean isImageContainer = element.children().size()==1 && element.child(0).tagName().equals("img"); if (!isImageContainer){ currentLine.marginBottom = (int)(font.lineHeight * 0.5f); } } startNewLine(true); } }
    private void postProcessElementEnd(Element element, String tagName){ if (isBlockTag(tagName)){ boolean inList = inOrderedList || inUnorderedList; boolean isPInsideLi = "p".equals(tagName) && inList && element.parent()!=null && "li".equals(element.parent().tagName()); if (!isPInsideLi){ if (currentLine!=null && !currentLine.parts.isEmpty()){ boolean isImageContainer = element.children().size()==1 && element.child(0).tagName().equals("img"); if (!isImageContainer){ currentLine.marginBottom = (int)(font.lineHeight * 0.5f); } } startNewLine(true); } return; } if (tagName.equals("span") && element.hasClass("figcaption")){ if (currentLine!=null && !currentLine.parts.isEmpty()){ currentLine.marginBottom=(int)(font.lineHeight*0.5f); } startNewLine(true); } else if (tagName.equals("span") && isTitleElement(element)){ if (currentLine!=null && !currentLine.parts.isEmpty()){ currentLine.marginBottom=(int)(font.lineHeight*0.6f); } startNewLine(true); } else if (tagName.equals("ol") && inOrderedList){ inOrderedList=false; orderedListIndex=0; startNewLine(true); } else if (tagName.equals("ul") && inUnorderedList){ inUnorderedList=false; startNewLine(true); } }
    private boolean isTitleElement(Element element){ if (element.hasClass("common-text-title")) return true; for (String cls : element.classNames()){ if (cls.startsWith("common-text-title-")) return true; } return false; }
    private StyleDelta handleSpan(Element element){ boolean isFigcaption = element.hasClass("figcaption"); if (isFigcaption){ startNewLine(false); if (currentLine!=null){ currentLine.isFigcaption=true; currentLine.lineType = RenderableLine.LineType.CAPTION_LINE; } return new StyleDelta(Style.FIGCAPTION_COLOR, null, null, null); } else if (isTitleElement(element)){ startNewLine(false); if (currentLine!=null){ currentLine.lineType = RenderableLine.LineType.TITLE_LINE; } return new StyleDelta(0xFFE0E0E0, null, Boolean.TRUE, null); } else if (element.hasAttr("style")){ Integer parsed = StyleParser.parseColorFromStyle(element.attr("style")); if (parsed != null){ return new StyleDelta(parsed, null, null, null); } } return null; }
    private void handleImg(Element element){
        if (currentLine == null){ startNewLine(true); } else if (!currentLine.parts.isEmpty()){ startNewLine(false); }
        String srcAttr = element.hasAttr("data-src") && !element.attr("data-src").isEmpty() ? "data-src" : (element.hasAttr("data-original") && !element.attr("data-original").isEmpty() ? "data-original" : "src");
        String src = element.attr(srcAttr);
        if (src != null && !src.isEmpty()){
            String absoluteSrc = element.absUrl(srcAttr);
            int imgWidth = 50; int imgHeight = 50;
            try {
                if (element.hasAttr("data-width")) imgWidth = Integer.parseInt(element.attr("data-width"));
                else if (element.hasAttr("width")) imgWidth = Integer.parseInt(element.attr("width"));
                if (element.hasAttr("data-height")) imgHeight = Integer.parseInt(element.attr("data-height"));
                else if (element.hasAttr("height")) imgHeight = Integer.parseInt(element.attr("height"));
            } catch (NumberFormatException ignored) {}
            float f = 0.2f; int scaledWidth = (int)(imgWidth * f); int scaledHeight = (int)(imgHeight * f);
            addImage(absoluteSrc, scaledWidth, scaledHeight);
        }
    }
    private void handleSvg(Element element, int width){
        if (element.hasClass("common-mcicon")){
            Element useElement = element.selectFirst("use");
            if (useElement != null){
                String href = useElement.hasAttr("xlink:href") ? useElement.attr("xlink:href") : (useElement.hasAttr("href") ? useElement.attr("href") : null);
                if (href != null && !href.isEmpty()){
                    String iconId = href.startsWith("#") ? href.substring(1) : href;
                    ImagePart icon = ICONS.get(iconId);
                    if (icon == null) { com.cy311.omnisearch.OmnisearchLogger.info("svg iconId=" + iconId + " not mapped"); }
                    else { com.cy311.omnisearch.OmnisearchLogger.info("svg icon mapped: " + iconId + " -> " + icon.location); }
                    if (icon != null){
                        float maxW = layout.correctedMaxWidth(width);
                        if (layout.shouldWrap(currentX, icon.width * scale, maxW)) startNewLine(false);
                        currentLine.addPart(icon);
                        currentX += icon.width * scale;
                    }
                }
            }
        }
    }
    private void startNewLine(boolean force){ if (force || currentX > 0){ if (currentLine != null && !currentLine.parts.isEmpty()){ addLine(currentLine); } currentLine = new RenderableLine(font); currentX = 0; currentLine.isFigcaption=false; currentLine.lineType = RenderableLine.LineType.TEXT_LINE; } }
    private void addLine(RenderableLine line){ boolean hasContent=false; for (RenderablePart part : line.parts){ if (part instanceof ImagePart){ hasContent = true; break; } if (part instanceof StyledPart && !((StyledPart)part).text.getString().trim().isEmpty()){ hasContent = true; break; } } if (!hasContent) { return; } this.renderableLines.add(line); recalculateTotalHeight(); }
    private void recalculateTotalHeight(){ this.totalHeight = metrics.recalculateTotalHeight(this.renderableLines, scale, lineSpacingFactor); }
    private static class MetricsCalculator { int computeScaledLineHeight(RenderableLine line, float scale, float spacing){ float s = scale; if (line.lineType == RenderableLine.LineType.CAPTION_LINE) s = scale * 0.7f; else if (line.lineType == RenderableLine.LineType.TITLE_LINE) s = scale * 1.15f; return (int)(line.height * s * spacing); } boolean shouldSkipExtraSpacing(List<RenderableLine> lines, int index, float scale){ if (index + 1 >= lines.size()) return false; RenderableLine line = lines.get(index); RenderableLine next = lines.get(index+1); boolean currentIsImageLine = line.lineType == RenderableLine.LineType.IMAGE_LINE; return currentIsImageLine && (next.lineType == RenderableLine.LineType.CAPTION_LINE || next.isFigcaption); } int recalculateTotalHeight(List<RenderableLine> lines, float scale, float spacing){ int h=0; for (RenderableLine line : lines){ float s = scale; if (line.lineType == RenderableLine.LineType.CAPTION_LINE || line.isFigcaption) s = scale * 0.7f; else if (line.lineType == RenderableLine.LineType.TITLE_LINE) s = scale * 1.15f; h += (int)(line.height * s * spacing) + (int)(line.marginBottom * s); } return h; } }
    private boolean isBlockTag(String tag){ return BLOCK_TAGS.contains(tag); }
    private void processElementNode(Element element, int width){ String tagName = element.tagName(); if (isBlockTag(tagName)){ boolean inList = inOrderedList || inUnorderedList; boolean isLiBlock = "li".equals(tagName) && inList; boolean isPInsideLi = "p".equals(tagName) && inList && element.parent()!=null && "li".equals(element.parent().tagName()); if (!isLiBlock && !isPInsideLi){ startNewLine(true); } } StyleDelta delta = applyElementDelta(element, width); for (Node child : element.childNodes()){ processNode(child, width); } revertElementDelta(delta); postProcessElementEnd(element, tagName); }
    private StyleDelta applyElementDelta(Element element, int width){ ElementHandler handler = elementHandlers.get(element.tagName()); Style base = styleStack.peek(); StyleDelta delta = handler != null ? handler.handle(element, base, width) : null; if (delta != null){ styleStack.push(delta.apply(base)); } return delta; }
    private void revertElementDelta(StyleDelta delta){ if (delta != null){ styleStack.pop(); } }
    private void handleOl(Element element){ String style = element.attr("style"); if (style!=null && style.contains("list-style-type")){ if (style.contains("decimal")) orderedListStyle = "decimal"; else orderedListStyle = "other"; } else { orderedListStyle = "decimal"; } inOrderedList = true; orderedListIndex = 1; startNewLine(true); }
    private void handleUl(Element element){ String style = element.attr("style"); if (style!=null && style.contains("list-style-type")){ if (style.contains("disc")) unorderedListStyle="disc"; else if (style.contains("circle")) unorderedListStyle="circle"; else if (style.contains("square")) unorderedListStyle="square"; else unorderedListStyle="disc"; } else { unorderedListStyle="disc"; } inUnorderedList = true; startNewLine(true); }
    private void handleLiStart(int width){ if (inOrderedList || inUnorderedList){ if (currentLine == null){ startNewLine(true); } String prefix; if (inOrderedList){ prefix = ("decimal".equals(orderedListStyle) ? (orderedListIndex + ". ") : (orderedListIndex + ") ")); orderedListIndex++; } else { if ("circle".equals(unorderedListStyle)) prefix = "◦ "; else if ("square".equals(unorderedListStyle)) prefix = "■ "; else prefix = "• "; } Style numStyle = styleStack.peek().withBold(true); addText(prefix, numStyle); for (int i=0;i<LIST_INDENT_SPACES;i++){ addText(" ", styleStack.peek()); } } }
    private static class StyleParser { private static final Pattern COLOR_DECL = Pattern.compile("color\\s*:\\s*([^;]+)"); private static final Pattern HEX6 = Pattern.compile("#([0-9a-fA-F]{6})"); private static final Pattern HEX3 = Pattern.compile("#([0-9a-fA-F]{3})"); private static final Pattern RGB = Pattern.compile("rgb\\s*\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*\\)"); static Integer parseColorFromStyle(String styleAttr){ Matcher m = COLOR_DECL.matcher(styleAttr); if (!m.find()) return null; String colorStr = m.group(1).trim(); Matcher hex6 = HEX6.matcher(colorStr); if (hex6.matches()){ int rgb = Integer.parseInt(hex6.group(1), 16); return 0xFF000000 | rgb; } Matcher hex3 = HEX3.matcher(colorStr); if (hex3.matches()){ String h = hex3.group(1); char r=h.charAt(0), g=h.charAt(1), b=h.charAt(2); String expanded = ""+r+r+g+g+b+b; int rgb = Integer.parseInt(expanded, 16); return 0xFF000000 | rgb; } Matcher rgbm = RGB.matcher(colorStr); if (rgbm.matches()){ int r=Integer.parseInt(rgbm.group(1)); int g=Integer.parseInt(rgbm.group(2)); int b=Integer.parseInt(rgbm.group(3)); return (0xFF<<24) | (r<<16) | (g<<8) | b; } return null; } }
    private static class StyleDelta{ final Integer color; final String linkUrl; final Boolean bold; final Boolean italic; StyleDelta(Integer c,String l,Boolean b,Boolean i){ color=c; linkUrl=l; bold=b; italic=i; } Style apply(Style base){ Style s = base.copy(); if (color!=null) s.color=color; if (linkUrl!=null) s.linkUrl=linkUrl; if (bold!=null) s.isBold=bold; if (italic!=null) s.isItalic=italic; return s; } }
}