package com.cy311.omnisearch.client.render.document;

import com.cy311.omnisearch.OmnisearchMod;
import com.cy311.omnisearch.client.render.image.ImageDimensions;
import com.cy311.omnisearch.client.render.image.ImageManager;
import com.cy311.omnisearch.client.render.layout.FontMetrics;
import com.cy311.omnisearch.client.render.layout.LayoutEngine;
import com.cy311.omnisearch.client.render.layout.LayoutNode;
import com.cy311.omnisearch.client.render.layout.LayoutType;
import com.cy311.omnisearch.data.model.document.Document;
import com.cy311.omnisearch.gui.theme.OmniTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders a Document onto the screen using GuiGraphics.
 * <p>
 * Layout computation ({@link #prepare}) is separated from rendering ({@link #paint}),
 * so that scrolling only changes the paint offset without re-running layout.
 */
public class DocumentRenderer {

    // MC GUI sprite locations for inline SVG icon rendering (1.21.1 sprite system)
    private static final ResourceLocation MC_HEART_CONTAINER = ResourceLocation.withDefaultNamespace("hud/heart/container");
    private static final ResourceLocation MC_HEART_HALF = ResourceLocation.withDefaultNamespace("hud/heart/half");
    private static final ResourceLocation MC_HEART_FULL = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation MC_HUNGER_CONTAINER = ResourceLocation.withDefaultNamespace("hud/hunger/container");
    private static final ResourceLocation MC_HUNGER_HALF = ResourceLocation.withDefaultNamespace("hud/hunger/half");
    private static final ResourceLocation MC_HUNGER_FULL = ResourceLocation.withDefaultNamespace("hud/hunger/full");

    /** Tracks unknown mc-icon names we've already logged to avoid spam. */
    private static final Set<String> loggedUnknownIcons = new HashSet<>();

    private final Font font;
    private final FontMetrics metrics;
    @Nullable
    private final ImageManager imageManager;

    // Transient paint state - set at the start of paint()
    private GuiGraphics gui;
    private int paintOffsetX;
    private int paintOffsetY;
    private int mouseAbsX = -1;
    private int mouseAbsY = -1;

    public record LinkHit(int x, int y, int w, int h, String url) {}

    public DocumentRenderer(Font font, @Nullable ImageManager imageManager) {
        this.font = font;
        this.imageManager = imageManager;
        this.metrics = new FontMetrics(Math.max(1, font.lineHeight), font::width);
    }

    /**
     * The image manager used by this renderer (may be null if images are disabled).
     */
    @Nullable
    public ImageManager imageManager() {
        return imageManager;
    }

    // ── Layout preparation (called when document or width changes) ──

    /**
     * Computes layout for the given document and returns a snapshot.
     * <p>
     * All coordinates in the snapshot are relative to (0, 0).
     * Call {@link #paint} to render with a content-area offset.
     */
    public PreparedDocumentLayout prepare(Document doc, int width) {
        LayoutEngine engine = new LayoutEngine(metrics, 0, 0, width,
            imageManager != null ? imageManager::getCachedSize : null);
        List<LayoutNode> nodes = engine.layout(doc);
        int height = engine.getHeight();
        return new PreparedDocumentLayout(nodes, height);
    }

    // ── Painting (called every frame, no layout recomputation) ──

    /**
     * Paints a previously-prepared layout snapshot onto the given graphics context.
     *
     * @param g        the graphics context to paint onto
     * @param layout   the prepared layout snapshot
     * @param offsetX  absolute X of the content area left edge
     * @param offsetY  absolute Y of the content area top edge, minus scroll offset
     */
    public void paint(GuiGraphics g, PreparedDocumentLayout layout, int offsetX, int offsetY) {
        paint(g, layout, offsetX, offsetY, -1, -1);
    }

    public void paint(GuiGraphics g, PreparedDocumentLayout layout, int offsetX, int offsetY, int mouseX, int mouseY) {
        this.gui = g;
        this.paintOffsetX = offsetX;
        this.paintOffsetY = offsetY;
        this.mouseAbsX = mouseX;
        this.mouseAbsY = mouseY;
        for (LayoutNode node : layout.nodes()) {
            renderLayoutNode(node);
        }
    }

    // ── Rendering dispatch ──

    private void renderLayoutNode(LayoutNode node) {
        int rx = node.x + paintOffsetX;
        int ry = node.y + paintOffsetY;
        switch (node.type) {
            case HEADING -> renderHeading(node, rx, ry);
            case PARAGRAPH -> renderParagraph(node, rx, ry);
            case TEXT, STYLED_TEXT -> renderInlineTextNode(node, rx, ry);
            case IMAGE -> renderImageNode(node, rx, ry);
            case TABLE -> renderTableNode(node, rx, ry);
            case LIST -> renderListNode(node);
            case LINK -> renderLinkNode(node, rx, ry);
            case DIVIDER -> renderDivider(node, rx, ry);
            case INLINE_TEXT -> renderInlineTextNode(node, rx, ry);
            case INLINE_IMAGE -> renderInlineImageNode(node, rx, ry);
            default -> {}
        }
    }

    // ── Block rendering ──

    private void renderHeading(LayoutNode node, int rx, int ry) {
        int color;
        switch (node.headingLevel) {
            case 1 -> color = OmniTheme.TEXT_HEADING_1;
            case 2 -> color = OmniTheme.TEXT_HEADING_2;
            default -> color = OmniTheme.TEXT_WHITE;
        }
        if (!node.inlineChildren.isEmpty()) {
            // Heading laid out as wrapped inline fragments - apply heading color
            // to each child that hasn't had an explicit color set.
            for (LayoutNode inline : node.inlineChildren) {
                if (inline.textColor == -1) {
                    inline = inline.withColor(color);
                }
                renderLayoutNode(inline);
            }
            return;
        }
        // Fallback: single-line heading with text (used by SectionNode)
        String text = node.text;
        if (text == null || text.isEmpty()) return;
        gui.drawString(font, text, rx, ry, color, false);
    }

    private void renderParagraph(LayoutNode node, int rx, int ry) {
        for (LayoutNode inline : node.inlineChildren) {
            renderLayoutNode(inline);
        }
        for (LayoutNode child : node.children) {
            renderLayoutNode(child);
        }
    }

    private void renderInlineTextNode(LayoutNode node, int rx, int ry) {
        if (node.text == null || node.text.isEmpty()) return;
        boolean linkHovered = node.linkUrl != null
            && mouseAbsX >= rx && mouseAbsX <= rx + node.w
            && mouseAbsY >= ry && mouseAbsY <= ry + font.lineHeight;
        int color = node.linkUrl != null
            ? (linkHovered ? OmniTheme.TEXT_WHITE : OmniTheme.TEXT_LINK)
            : node.textColor != -1
                ? node.textColor
                : OmniTheme.TEXT_WHITE;

        // Build a Component carrying all inline styles. Minecraft's Style renders bold
        // (via glyph widening, works for CJK), italic (oblique; CJK glyphs stay upright,
        // matching vanilla behavior), underline and strikethrough (drawn over any text).
        boolean anyStyle = node.isBold || node.isItalic || node.isUnderline || node.isStrikethrough;
        if (anyStyle) {
            Style style = Style.EMPTY
                .withBold(node.isBold)
                .withItalic(node.isItalic)
                .withUnderlined(node.isUnderline)
                .withStrikethrough(node.isStrikethrough);
            gui.drawString(font, Component.literal(node.text).withStyle(style), rx, ry, color, false);
        } else {
            gui.drawString(font, node.text, rx, ry, color, false);
        }
        if (node.linkUrl != null && linkHovered) {
            gui.hLine(rx, rx + Math.max(0, node.w - 1), ry + font.lineHeight, OmniTheme.TEXT_WHITE);
        }
    }

    private void renderImageNode(LayoutNode node, int rx, int ry) {
        String url = node.imageUrl;
        if (imageManager == null || url == null || url.isBlank()) return;

        ResourceLocation loc = imageManager.getImage(url).getNow(null);
        if (loc != null && node.w > 0 && node.h > 0) {
            // Layout already reserved the box from the real decoded size (ImageSizeProvider),
            // so draw exactly the laid-out box — no re-scaling that could overflow.
            gui.blit(loc, rx, ry, 0, 0, node.w, node.h, node.w, node.h);
            return;
        }

        // Not yet loaded — show placeholder sized to the reserved box
        gui.fill(rx, ry, rx + node.w, ry + node.h, OmniTheme.BG_PLACEHOLDER);
        String alt = node.alt;
        if (alt != null && !alt.isEmpty()) {
            gui.drawString(font, alt, rx + 2, ry + node.h + 1, OmniTheme.TEXT_GRAY, false);
        }
    }

    private void renderInlineImageNode(LayoutNode node, int rx, int ry) {
        String url = node.imageUrl;
        int iconSize = node.w;

        // SVG inline icon (mc-icon://) — render using MC's GUI sprite system
        if (url != null && url.startsWith("mc-icon://")) {
            String iconName = url.substring("mc-icon://".length());
            if (tryRenderMcIcon(iconName, rx, ry, iconSize)) {
                return;
            }
            // Known text-based icons: don't log as unmapped, just render abbreviation
            if (isKnownTextIcon(iconName)) {
                String abbr = abbreviateIconName(iconName);
                gui.drawString(font, abbr, rx, ry, 0xFF888888, false);
                return;
            }
            logUnknownIconOnce(iconName);
            // Fallback: render readable abbreviation
            String abbr = abbreviateIconName(iconName);
            gui.drawString(font, abbr, rx, ry, 0xFF888888, false);
            return;
        }

        if (imageManager != null && url != null && !url.isBlank()) {
            ResourceLocation loc = imageManager.getImage(url).getNow(null);
            if (loc != null) {
                gui.blit(loc, rx, ry, 0, 0, iconSize, iconSize, iconSize, iconSize);
                return;
            }
        }
        gui.fill(rx, ry, rx + iconSize, ry + iconSize, OmniTheme.BG_PLACEHOLDER);
        String alt = node.alt;
        if (alt != null && !alt.isEmpty()) {
            gui.drawString(font, alt.substring(0, 1), rx + 1, ry, OmniTheme.TEXT_GRAY, false);
        }
    }

    /**
     * Maps mcmod.cn SVG icon names to Minecraft's GUI sprite system (1.21.1).
     * Returns true if the icon was recognized and rendered via blitSprite.
     */
    private boolean tryRenderMcIcon(String iconName, int x, int y, int size) {
        // Determine icon state: full, half, or empty/container
        boolean isHalf = iconName.contains("-half");
        boolean isEmpty = iconName.contains("-empty") || iconName.contains("-container");
        String key = iconName
            .replace("icon-", "")
            .replace("-full", "")
            .replace("-half", "")
            .replace("-empty", "")
            .replace("-container", "");
        ResourceLocation sprite;
        switch (key) {
            case "health" -> sprite = isEmpty ? MC_HEART_CONTAINER : isHalf ? MC_HEART_HALF : MC_HEART_FULL;
            case "hunger" -> sprite = isEmpty ? MC_HUNGER_CONTAINER : isHalf ? MC_HUNGER_HALF : MC_HUNGER_FULL;
            default -> { return false; }
        }
        gui.blitSprite(sprite, x, y, size, size);
        return true;
    }

    /**
     * Produces a compact, readable fallback label for mc-icon names
     * that cannot be mapped to an MC sprite.
     */
    private static String abbreviateIconName(String iconName) {
        String raw = iconName
            .replace("icon-", "")
            .replace("-full", "")
            .replace("-half", "")
            .replace("-empty", "")
            .replace("-container", "");
        // Known abbreviations for common mcmod.cn icons without MC sprite equivalents
        return switch (raw) {
            case "experience", "exp" -> "[exp]";
            case "saturation", "sat" -> "[sat]";
            case "armor" -> "[armor]";
            case "air" -> "[air]";
            case "speed" -> "[spd]";
            case "slowness" -> "[slow]";
            case "haste" -> "[hast]";
            case "mining-fatigue" -> "[fatig]";
            case "strength" -> "[str]";
            case "weakness" -> "[weak]";
            case "jump-boost" -> "[jump]";
            case "resistance" -> "[res]";
            case "fire-resistance" -> "[fire]";
            case "water-breathing" -> "[water]";
            case "invisibility" -> "[invis]";
            case "night-vision" -> "[nv]";
            case "regeneration" -> "[regen]";
            case "poison" -> "[pois]";
            case "wither" -> "[with]";
            case "absorption" -> "[abso]";
            case "health-boost" -> "[hboost]";
            case "glowing" -> "[glow]";
            case "levitation" -> "[lev]";
            case "luck" -> "[luck]";
            case "bad-luck", "unluck" -> "[unlck]";
            case "slow-falling" -> "[slowf]";
            case "conduit-power" -> "[cond]";
            case "dolphins-grace" -> "[dolph]";
            case "darkness" -> "[dark]";
            default -> {
                // Fallback: take first 4 chars of the cleaned name
                String shortLabel = raw.length() > 4 ? raw.substring(0, 4) : raw;
                yield "[" + shortLabel + "]";
            }
        };
    }

    /** Logs unknown mc-icon names once per session to aid systematic discovery. */
    private static void logUnknownIconOnce(String iconName) {
        if (loggedUnknownIcons.add(iconName)) {
            OmnisearchMod.LOGGER.warn("[DocRenderer] Unmapped mc-icon: {} — add to tryRenderMcIcon or abbreviation table", iconName);
        }
    }

    /**
     * Checks if an mc-icon name is a known text-based fallback icon.
     * These should not trigger "unmapped" warnings since they intentionally use text rendering.
     */
    private static boolean isKnownTextIcon(String iconName) {
        String raw = iconName
            .replace("icon-", "")
            .replace("-full", "")
            .replace("-half", "")
            .replace("-empty", "")
            .replace("-container", "");
        return switch (raw) {
            case "experience", "exp", "saturation", "sat", "armor", "air",
                 "speed", "slowness", "haste", "mining-fatigue", "strength",
                 "weakness", "jump-boost", "resistance", "fire-resistance",
                 "water-breathing", "invisibility", "night-vision", "regeneration",
                 "poison", "wither", "absorption", "health-boost", "glowing",
                 "levitation", "luck", "bad-luck", "unluck", "slow-falling",
                 "conduit-power", "dolphins-grace", "darkness" -> true;
            default -> false;
        };
    }

    private void renderLinkNode(LayoutNode node, int rx, int ry) {
        for (LayoutNode child : node.inlineChildren) {
            renderLayoutNode(child);
        }
    }

    private void renderTableNode(LayoutNode node, int rx, int ry) {
        int headerY = -1;
        int lastCellY = -1;
        int rowIdx = 0;

        for (int i = 0; i < node.children.size(); i++) {
            LayoutNode cell = node.children.get(i);
            int cx = cell.x + paintOffsetX;
            int cy = cell.y + paintOffsetY;
            int ch = cell.h; // use actual cell height from layout
            int color = cell.isHeader ? OmniTheme.TEXT_HEADING_2 : OmniTheme.TEXT_WHITE;

            // Track row index: increment when Y changes (new row)
            if (headerY < 0) {
                headerY = cy;
                lastCellY = cy;
            } else if (cy != lastCellY) {
                rowIdx++;
                lastCellY = cy;
            }

            // Background: header gets header bg, data rows alternate
            if (cell.isHeader) {
                gui.fill(cx - 2, cy, cx + cell.w + 2, cy + ch, OmniTheme.BG_TABLE_HEADER);
            } else {
                if (rowIdx % 2 == 1) {
                    gui.fill(cx - 2, cy, cx + cell.w + 2, cy + ch, 0xFF222222);
                }
            }

            // Cell border
            gui.hLine(cx - 2, cx + cell.w + 2, cy, OmniTheme.BORDER_LIGHT);
            gui.hLine(cx - 2, cx + cell.w + 2, cy + ch - 1, OmniTheme.BORDER);

            // Cell content: block children (images), inline children (icons + text), or plain text fallback
            if (!cell.children.isEmpty()) {
                for (LayoutNode child : cell.children) {
                    renderLayoutNode(child);
                }
            }
            if (!cell.inlineChildren.isEmpty()) {
                for (LayoutNode inline : cell.inlineChildren) {
                    renderLayoutNode(inline);
                }
            } else if (cell.text != null && !cell.text.isEmpty()) {
                if (cell.isHeader) {
                    var component = net.minecraft.network.chat.Component.literal(cell.text)
                        .setStyle(net.minecraft.network.chat.Style.EMPTY.withBold(true));
                    gui.drawString(font, component, cx, cy + 2, color, false);
                } else {
                    gui.drawString(font, cell.text, cx, cy + 2, color, false);
                }
            }
        }
        // Borders
        if (!node.children.isEmpty()) {
            int tableY = node.y + paintOffsetY;
            int tableEndY = node.y + node.h + paintOffsetY;
            LayoutNode first = node.children.getFirst();
            LayoutNode last = node.children.getLast();
            int tableX = first.x + paintOffsetX - 2;
            int tableEndX = last.x + last.w + paintOffsetX + 2;
            gui.hLine(tableX, tableEndX, tableY, OmniTheme.BORDER);
            gui.hLine(tableX, tableEndX, tableEndY, OmniTheme.BORDER);
            gui.vLine(tableX, tableY, tableEndY, OmniTheme.BORDER);
            gui.vLine(tableEndX, tableY, tableEndY, OmniTheme.BORDER);
        }
    }

    private void renderListNode(LayoutNode node) {
        for (int i = 0; i < node.children.size(); i++) {
            LayoutNode child = node.children.get(i);
            if (child.type == LayoutType.TEXT && child.text != null) {
                int cx = child.x + paintOffsetX;
                int cy = child.y + paintOffsetY;
                boolean isMarker = child.text.length() <= 3 && (child.text.contains("•") || Character.isDigit(child.text.charAt(0)));
                int color = isMarker ? OmniTheme.TEXT_HEADING_2 : OmniTheme.TEXT_WHITE;
                gui.drawString(font, child.text, cx, cy, color, false);
            } else if (child.type == LayoutType.PARAGRAPH) {
                // Wrapped list item content
                for (LayoutNode inline : child.inlineChildren) {
                    renderLayoutNode(inline);
                }
            }
        }
    }

    private void renderDivider(LayoutNode node, int rx, int ry) {
        int midY = ry + font.lineHeight / 2;
        gui.hLine(rx, rx + node.w, midY, OmniTheme.BORDER_LIGHT);
    }
}
