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

    private static final int IMAGE_PLACEHOLDER_WIDTH = 64;
    private static final int IMAGE_PLACEHOLDER_HEIGHT = 48;

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

    // Transient paint state — set at the start of paint()
    private GuiGraphics gui;
    private int paintOffsetX;
    private int paintOffsetY;

    public record LinkHit(int x, int y, int w, int h, String url) {}

    public DocumentRenderer(Font font, @Nullable ImageManager imageManager) {
        this.font = font;
        this.imageManager = imageManager;
        this.metrics = new FontMetrics(Math.max(1, font.lineHeight), font::width);
    }

    // ── Layout preparation (called when document or width changes) ──

    /**
     * Computes layout for the given document and returns a snapshot.
     * <p>
     * All coordinates in the snapshot are relative to (0, 0).
     * Call {@link #paint} to render with a content-area offset.
     */
    public PreparedDocumentLayout prepare(Document doc, int width) {
        LayoutEngine engine = new LayoutEngine(metrics, 0, 0, width);
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
        this.gui = g;
        this.paintOffsetX = offsetX;
        this.paintOffsetY = offsetY;
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
        String text = node.text;
        if (text == null || text.isEmpty()) return;
        int color;
        switch (node.headingLevel) {
            case 1 -> color = OmniTheme.TEXT_HEADING_1;
            case 2 -> color = OmniTheme.TEXT_HEADING_2;
            default -> color = OmniTheme.TEXT_WHITE;
        }
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
        int color = node.linkUrl != null
            ? OmniTheme.TEXT_LINK
            : node.textColor != -1
                ? node.textColor
                : (node.type == LayoutType.STYLED_TEXT ? OmniTheme.TEXT_LIGHT : OmniTheme.TEXT_WHITE);
        gui.drawString(font, node.text, rx, ry, color, false);
        if (node.isBold) {
            gui.drawString(font, node.text, rx + 1, ry, color, false);
        }
        if (node.linkUrl != null) {
            gui.hLine(rx, rx + Math.max(0, node.w - 1), ry + font.lineHeight - 1, OmniTheme.TEXT_LINK);
        }
    }

    private void renderImageNode(LayoutNode node, int rx, int ry) {
        String url = node.imageUrl;
        int imgX = rx;
        int imgY = ry;

        if (imageManager != null && url != null && !url.isBlank()) {
            ResourceLocation loc = imageManager.getImage(url).getNow(null);
            if (loc != null) {
                ImageDimensions dims = imageManager.getCachedSize(url);
                int renderW = dims != null ? Math.min(dims.width(), node.w) : IMAGE_PLACEHOLDER_WIDTH;
                int renderH = dims != null
                    ? (int) ((float) renderW / dims.width() * dims.height())
                    : IMAGE_PLACEHOLDER_HEIGHT;
                gui.blit(loc, imgX, imgY, 0, 0, renderW, renderH, renderW, renderH);
                return;
            }
        }

        // Placeholder fallback
        gui.fill(imgX, imgY, imgX + IMAGE_PLACEHOLDER_WIDTH, imgY + IMAGE_PLACEHOLDER_HEIGHT, OmniTheme.BG_PLACEHOLDER);
        gui.hLine(imgX, imgX + IMAGE_PLACEHOLDER_WIDTH - 1, imgY, OmniTheme.BORDER_PLACEHOLDER);
        gui.hLine(imgX, imgX + IMAGE_PLACEHOLDER_WIDTH - 1, imgY + IMAGE_PLACEHOLDER_HEIGHT - 1, OmniTheme.BORDER_PLACEHOLDER);
        gui.vLine(imgX, imgY, imgY + IMAGE_PLACEHOLDER_HEIGHT - 1, OmniTheme.BORDER_PLACEHOLDER);
        gui.vLine(imgX + IMAGE_PLACEHOLDER_WIDTH - 1, imgY, imgY + IMAGE_PLACEHOLDER_HEIGHT - 1, OmniTheme.BORDER_PLACEHOLDER);

        String alt = node.alt;
        if (alt != null && !alt.isEmpty()) {
            gui.drawString(font, alt, imgX, imgY + IMAGE_PLACEHOLDER_HEIGHT + 1, OmniTheme.TEXT_GRAY, false);
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

    private void renderLinkNode(LayoutNode node, int rx, int ry) {
        for (LayoutNode child : node.inlineChildren) {
            renderLayoutNode(child);
        }
    }

    private void renderTableNode(LayoutNode node, int rx, int ry) {
        int rowH = font.lineHeight + 4;

        for (int i = 0; i < node.children.size(); i++) {
            LayoutNode cell = node.children.get(i);
            int cx = cell.x + paintOffsetX;
            int cy = cell.y + paintOffsetY;
            int color = cell.isHeader ? OmniTheme.TEXT_LIGHT : OmniTheme.TEXT_WHITE;

            // Background
            if (cell.isHeader) {
                gui.fill(cx - 2, cy, cx + cell.w + 2, cy + rowH, OmniTheme.BG_TABLE_HEADER);
            } else if (i % 4 == 0) {
                gui.fill(cx - 2, cy, cx + cell.w + 2, cy + rowH, OmniTheme.BG_ROW_ALT);
            }

            // Cell content: inline children (icons + text) or plain text fallback
            if (!cell.inlineChildren.isEmpty()) {
                for (LayoutNode inline : cell.inlineChildren) {
                    renderLayoutNode(inline);
                }
            } else if (cell.text != null && !cell.text.isEmpty()) {
                gui.drawString(font, cell.text, cx, cy + 2, color, false);
                if (cell.isHeader) {
                    gui.drawString(font, cell.text, cx + 1, cy + 2, color, false);
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
                int color = isMarker ? OmniTheme.TEXT_LIGHT : OmniTheme.TEXT_WHITE;
                gui.drawString(font, child.text, cx, cy, color, false);
            }
        }
    }

    private void renderDivider(LayoutNode node, int rx, int ry) {
        int midY = ry + font.lineHeight / 2;
        gui.hLine(rx, rx + node.w, midY, OmniTheme.BORDER_PLACEHOLDER);
    }
}
