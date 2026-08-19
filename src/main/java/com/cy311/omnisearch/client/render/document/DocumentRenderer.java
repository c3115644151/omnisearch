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

    // MC GUI sprite locations for inline SVG icon rendering (1.21.1 sprite system).
    // Verified against client-extra.jar: hunger is hud/food_*, NOT hud/hunger/*.
    private static final ResourceLocation MC_HEART_CONTAINER = ResourceLocation.withDefaultNamespace("hud/heart/container");
    private static final ResourceLocation MC_HEART_HALF = ResourceLocation.withDefaultNamespace("hud/heart/half");
    private static final ResourceLocation MC_HEART_FULL = ResourceLocation.withDefaultNamespace("hud/heart/full");
    private static final ResourceLocation MC_HUNGER_CONTAINER = ResourceLocation.withDefaultNamespace("hud/food_empty");
    private static final ResourceLocation MC_HUNGER_HALF = ResourceLocation.withDefaultNamespace("hud/food_half");
    private static final ResourceLocation MC_HUNGER_FULL = ResourceLocation.withDefaultNamespace("hud/food_full");
    private static final ResourceLocation MC_ARMOR_FULL = ResourceLocation.withDefaultNamespace("hud/armor_full");
    private static final ResourceLocation MC_ARMOR_HALF = ResourceLocation.withDefaultNamespace("hud/armor_half");
    private static final ResourceLocation MC_AIR = ResourceLocation.withDefaultNamespace("hud/air");
    private static final ResourceLocation MC_EXP = ResourceLocation.withDefaultNamespace("hud/experience_bar_progress");

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
        paint(g, layout, offsetX, offsetY, mouseX, mouseY, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    /**
     * Paints the layout, culling nodes outside the vertical viewport [clipTop, clipBottom]
     * (screen coordinates). This keeps long pages fast — off-screen nodes are skipped
     * instead of being drawn every frame.
     */
    public void paint(GuiGraphics g, PreparedDocumentLayout layout, int offsetX, int offsetY,
                      int mouseX, int mouseY, int clipTop, int clipBottom) {
        this.gui = g;
        this.paintOffsetX = offsetX;
        this.paintOffsetY = offsetY;
        this.mouseAbsX = mouseX;
        this.mouseAbsY = mouseY;
        for (LayoutNode node : layout.nodes()) {
            if (node.type == LayoutType.PARAGRAPH || node.type == LayoutType.HEADING
                    || node.type == LayoutType.TABLE || node.type == LayoutType.LIST
                    || node.type == LayoutType.IMAGE || node.type == LayoutType.DIVIDER) {
                int screenY = node.y + offsetY;
                if (screenY + node.h < clipTop || screenY > clipBottom) {
                    continue; // fully outside the visible viewport
                }
            }
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
            // to each child that hasn't had an explicit color set. Level-1 headings are
            // also bolded so the hierarchy reads clearly.
            for (LayoutNode inline : node.inlineChildren) {
                if (inline.textColor == -1) {
                    inline = inline.withColor(color);
                }
                if (node.headingLevel == 1 && !inline.isBold) {
                    inline = inline.withBold(true);
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
        if (node.headingLevel >= 3) {
            // Short heading paragraph (mcmod section titles like "魔弹", "召唤方式"):
            // emphasize with the section-heading color and bold.
            int headingColor = OmniTheme.TEXT_HEADING_2;
            for (LayoutNode inline : node.inlineChildren) {
                if (inline.textColor == -1) {
                    inline = inline.withColor(headingColor);
                }
                if (!inline.isBold) {
                    inline = inline.withBold(true);
                }
                renderLayoutNode(inline);
            }
            return;
        }
        for (LayoutNode inline : node.inlineChildren) {
            renderLayoutNode(inline);
        }
        for (LayoutNode child : node.children) {
            renderLayoutNode(child);
        }
    }

    /**
     * mcmod.cn styles its default body text with a dark gray (e.g. #333333 / rgb(34,34,34))
     * that is meant for a WHITE page background. On our dark panel that text is nearly
     * invisible. Colors that are BOTH dark AND nearly achromatic (grayish) are replaced
     * with white; genuinely colored emphasis (red/orange/blue) is preserved even when dark.
     */
    static int readableColor(int argb) {
        if (argb == -1 || argb == OmniTheme.TEXT_WHITE) return argb;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        if (lum >= 60) return argb; // bright enough either way
        // Dark but saturated (a real accent color) → keep it
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double sat = max == 0 ? 0 : (max - min) / max;
        if (sat > 0.5) return argb;
        // Dark, desaturated → mcmod's default gray, unreadable on dark → white
        return OmniTheme.TEXT_WHITE;
    }

    private void renderInlineTextNode(LayoutNode node, int rx, int ry) {
        if (node.text == null || node.text.isEmpty()) return;
        boolean linkHovered = node.linkUrl != null
            && mouseAbsX >= rx && mouseAbsX <= rx + node.w
            && mouseAbsY >= ry && mouseAbsY <= ry + font.lineHeight;
        int color = node.linkUrl != null
            ? (linkHovered ? OmniTheme.TEXT_WHITE : OmniTheme.TEXT_LINK)
            : node.textColor != -1
                ? readableColor(node.textColor)
                : OmniTheme.TEXT_WHITE;

        // Scaled text (image captions): shrink via pose transform so it renders smaller.
        if (node.textScale != 1.0f && node.textScale > 0f) {
            renderScaledText(node, rx, ry, color);
            return;
        }

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

    /**
     * Draws text at a reduced scale (image captions) via pose transform.
     * <p>
     * The bitmap font is fixed-size, so a smaller font is a smaller-scaled draw. In
     * 1.21.1 {@code drawString(..., shadow=false)} renders at full brightness with no
     * batch-shadow offset (verified in {@code Font.StringRenderOutput}: dropShadow=false
     * sets dimFactor=1.0), so drawing at (0,0) inside the scaled frame is crisp and
     * correctly positioned.
     */
    private void renderScaledText(LayoutNode node, int rx, int ry, int color) {
        float s = node.textScale;
        var pose = gui.pose();
        pose.pushPose();
        pose.translate(rx, ry, 0);
        pose.scale(s, s, 1);
        gui.drawString(font, node.text, 0, 0, color, false);
        pose.popPose();
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
            // Known text-based icons: don't log as unmapped, just render abbreviation.
            // The abbreviation is tinted by semantic color (experience green, hunger
            // orange, poison green…) so even without a sprite the icon reads at a glance.
            if (isKnownTextIcon(iconName)) {
                String abbr = abbreviateIconName(iconName);
                gui.drawString(font, abbr, rx, ry, mcIconColor(iconName), false);
                return;
            }
            logUnknownIconOnce(iconName);
            // Fallback: render readable abbreviation
            String abbr = abbreviateIconName(iconName);
            gui.drawString(font, abbr, rx, ry, mcIconColor(iconName), false);
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
     * Maps mcmod.cn SVG icon names to vanilla Minecraft resources (1.21.1):
     * HUD sprites for health/hunger/air, and the mob_effect textures for status effects.
     * Returns true if the icon was rendered.
     */
    private boolean tryRenderMcIcon(String iconName, int x, int y, int size) {
        boolean isHalf = iconName.contains("-half");
        boolean isEmpty = iconName.contains("-empty") || iconName.contains("-container");

        // Composite icons: mcmod encodes the state in the name, e.g.
        //   icon-food-full-hunger-level        → full hunger bar
        //   icon-food-empty-saturation-level-100 → empty saturation
        //   icon-toughness-half                → armor toughness
        String lower = iconName.toLowerCase();
        if (lower.startsWith("icon-food-")) {
            boolean saturating = lower.contains("saturation");
            boolean empty = lower.contains("-empty");
            ResourceLocation sprite;
            if (saturating) {
                // Saturation uses the mob_effect saturation texture (orange sparkle)
                gui.blit(ResourceLocation.withDefaultNamespace("textures/mob_effect/saturation.png"),
                    x, y, 0, 0, size, size, size, size);
                return true;
            }
            sprite = empty ? MC_HUNGER_CONTAINER : isHalf ? MC_HUNGER_HALF : MC_HUNGER_FULL;
            gui.blitSprite(sprite, x, y, size, size);
            return true;
        }
        if (lower.startsWith("icon-toughness")) {
            // Armor toughness: half bar for a half point, full otherwise
            gui.blitSprite(isHalf ? MC_ARMOR_HALF : MC_ARMOR_FULL, x, y, size, size);
            return true;
        }
        if (lower.startsWith("icon-food")) {
            gui.blitSprite(isHalf ? MC_HUNGER_HALF : MC_HUNGER_FULL, x, y, size, size);
            return true;
        }

        String key = iconName
            .replace("icon-", "")
            .replace("-full", "")
            .replace("-half", "")
            .replace("-empty", "")
            .replace("-container", "")
            .replace("_", "")
            .toLowerCase();
        // HUD sprites (hearts, hunger, air, armor)
        switch (key) {
            case "health" -> {
                gui.blitSprite(isEmpty ? MC_HEART_CONTAINER : isHalf ? MC_HEART_HALF : MC_HEART_FULL, x, y, size, size);
                return true;
            }
            case "hunger" -> {
                gui.blitSprite(isEmpty ? MC_HUNGER_CONTAINER : isHalf ? MC_HUNGER_HALF : MC_HUNGER_FULL, x, y, size, size);
                return true;
            }
            case "air" -> {
                gui.blitSprite(MC_AIR,
                    x, y, size, size);
                return true;
            }
            case "armor" -> {
                gui.blitSprite(ResourceLocation.withDefaultNamespace("hud/armor_full"),
                    x, y, size, size);
                return true;
            }
            case "exp", "experience" -> {
                gui.blitSprite(MC_EXP,
                    x, y, size, size);
                return true;
            }
            default -> { /* fall through to mob_effect textures */ }
        }
        // Status effects: mcmod icon name → vanilla mob_effect/{name}.png texture.
        // The PNGs are 18x18; the name normalizations below match vanilla texture names.
        String effect = normalizeEffectName(key);
        if (effect != null) {
            ResourceLocation tex = ResourceLocation.withDefaultNamespace("textures/mob_effect/" + effect + ".png");
            gui.blit(tex, x, y, 0, 0, size, size, size, size);
            return true;
        }
        return false;
    }

    /** Maps a normalized (no-underscore, lowercase) mcmod icon key to a vanilla
     *  mob_effect texture file name (with underscores), or null if no vanilla effect
     *  matches. mcmod icon names are the vanilla effect names, so most map 1:1. */
    private static String normalizeEffectName(String key) {
        return switch (key) {
            case "absorption" -> "absorption";
            case "badomen" -> "bad_omen";
            case "blindness" -> "blindness";
            case "conduitpower" -> "conduit_power";
            case "darkness" -> "darkness";
            case "dolphinsgrace" -> "dolphins_grace";
            case "fireresistance" -> "fire_resistance";
            case "glowing" -> "glowing";
            case "haste" -> "haste";
            case "healthboost" -> "health_boost";
            case "heroofthevillage" -> "hero_of_the_village";
            case "hunger" -> "hunger";
            case "infested" -> "infested";
            case "instantdamage" -> "instant_damage";
            case "instanthealth" -> "instant_health";
            case "invisibility" -> "invisibility";
            case "jumpboost" -> "jump_boost";
            case "levitation" -> "levitation";
            case "luck" -> "luck";
            case "miningfatigue" -> "mining_fatigue";
            case "nausea" -> "nausea";
            case "nightvision" -> "night_vision";
            case "oozing" -> "oozing";
            case "poison" -> "poison";
            case "raidomen" -> "raid_omen";
            case "regeneration" -> "regeneration";
            case "resistance" -> "resistance";
            case "saturation" -> "saturation";
            case "slowfalling" -> "slow_falling";
            case "slowness" -> "slowness";
            case "speed" -> "speed";
            case "strength" -> "strength";
            case "trialomen" -> "trial_omen";
            case "unluck" -> "unluck";
            case "waterbreathing" -> "water_breathing";
            case "weakness" -> "weakness";
            case "weaving" -> "weaving";
            case "windcharged" -> "wind_charged";
            case "wither" -> "wither";
            default -> null;
        };
    }

    /**
     * Semantic color for a mc-icon abbreviation, so icons read at a glance even without a
     * sprite: experience green, hunger orange, status-effect colors, etc.
     */
    static int mcIconColor(String iconName) {
        String raw = iconName
            .replace("icon-", "")
            .replace("-full", "")
            .replace("-half", "")
            .replace("-empty", "")
            .replace("-container", "");
        return switch (raw) {
            case "experience", "exp" -> 0xFF55FF55;      // XP green
            case "saturation", "sat" -> 0xFFFFAA00;     // hunger orange
            case "armor" -> 0xFFCCCCCC;                 // silver armor
            case "air" -> 0xFF66CCFF;                   // bubble blue
            case "speed" -> 0xFFFF5555;                 // speed blue-grey in MC is 7CAFC6; use light blue
            case "slowness" -> 0xFF8B8B8B;
            case "haste" -> 0xFFDCDCDC;
            case "mining-fatigue" -> 0xFF4A4A4A;
            case "strength" -> 0xFFFF5555;              // red
            case "weakness" -> 0xFF8B8B8B;
            case "jump-boost" -> 0xFF22FF4C;
            case "resistance" -> 0xFF994C00;
            case "fire-resistance" -> 0xFFE49A3A;
            case "water-breathing" -> 0xFF2E87AB;
            case "invisibility" -> 0xFF7F8392;
            case "night-vision" -> 0xFF1F1FA1;
            case "regeneration" -> 0xFFCD5CAB;
            case "poison" -> 0xFF4E9331;                // poison green
            case "wither" -> 0xFF352A27;
            case "absorption" -> 0xFF2552A5;
            case "health-boost" -> 0xFFF87E23;
            case "glowing" -> 0xFF94A061;
            case "levitation" -> 0xFFCEFFFF;
            case "luck" -> 0xFF339900;
            case "bad-luck", "unluck" -> 0xFFC0A700;
            case "slow-falling" -> 0xFFF8F9FF;
            case "conduit-power" -> 0xFF1C437A;
            case "dolphins-grace" -> 0xFF88CBEB;
            case "darkness" -> 0xFF202020;
            default -> 0xFF888888;                      // neutral gray
        };
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
        if (node.children.isEmpty()) return;
        OmnisearchMod.LOGGER.info("[DocRenderer] renderTableNode: rows={} firstRowCells={}",
            node.children.size(),
            node.children.getFirst().children.size());

        int left = node.x + paintOffsetX;
        int top = node.y + paintOffsetY;
        int right = left + node.w;
        int bottom = top + node.h;
        int pad = 3;

        // Sheet background — one continuous surface
        gui.fill(left, top, right, bottom, 0xFF101010);

        for (int r = 0; r < node.children.size(); r++) {
            LayoutNode row = node.children.get(r);
            int rowY = row.y + paintOffsetY;
            int rowH = row.h;
            boolean isHeader = !row.children.isEmpty() && row.children.getFirst().isHeader;

            // Row background: header band or alternating data rows
            if (isHeader) {
                gui.fill(left, rowY, right, rowY + rowH, 0xFF2A2A3E);
            } else if (r % 2 == 1) {
                gui.fill(left, rowY, right, rowY + rowH, 0xFF1A1A1A);
            }

            // Row separator
            if (rowY + rowH < bottom) {
                gui.hLine(left + 1, right - 1, rowY + rowH, 0xFF3A3A3A);
            }

            // Cell content: vertical center, small left padding
            for (LayoutNode cell : row.children) {
                int cx = cell.x + paintOffsetX;
                int cy = cell.y + paintOffsetY;
                // Data cells use the same white as body text so the table blends with the
                // page; only headers are tinted gold.
                int color = cell.isHeader ? OmniTheme.TEXT_HEADING_2 : OmniTheme.TEXT_WHITE;

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
                    int ty = cy + Math.max(1, (rowH - font.lineHeight) / 2);
                    gui.drawString(font, cell.text, cx + pad, ty, color, false);
                }
            }
        }

        // Vertical column separators. Column boundaries come from the first row's cell
        // right edges (header defines the grid). Lines are drawn PER ROW: a row that has
        // a colspan cell spanning across a boundary skips that segment (so the merged cell
        // isn't cut), but other rows still get their separator there.
        java.util.List<Integer> colEdges = new java.util.ArrayList<>();
        LayoutNode gridRow = node.children.getFirst();
        for (LayoutNode cell : gridRow.children) {
            int e = cell.x + cell.w + paintOffsetX;
            if (e > left + 1 && e < right - 1 && !colEdges.contains(e)) {
                colEdges.add(e);
            }
        }
        for (int edge : colEdges) {
            for (LayoutNode row : node.children) {
                int rowTop = row.y + paintOffsetY;
                int rowBottom = rowTop + row.h;
                boolean coveredInRow = false;
                for (LayoutNode cell : row.children) {
                    int cs = cell.x + paintOffsetX;
                    int ce = cell.x + cell.w + paintOffsetX;
                    if (cs < edge && ce > edge) { // this cell spans across the boundary
                        coveredInRow = true;
                        break;
                    }
                }
                if (!coveredInRow) {
                    gui.vLine(edge, rowTop, rowBottom, 0xFF3A3A3A);
                }
            }
        }

        // Outer border — clean rectangle
        gui.hLine(left, right, top, 0xFF555555);
        gui.hLine(left, right, bottom - 1, 0xFF555555);
        gui.vLine(left, top, bottom, 0xFF555555);
        gui.vLine(right - 1, top, bottom, 0xFF555555);
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
