package com.cy311.omnisearch.gui.theme;

/**
 * Unified design system for Omnisearch UI.
 * All colors, fonts, and dimensions are defined here.
 */
public final class OmniTheme {

    private OmniTheme() {}

    // ── Colors ──
    public static final int BG_DARK = 0xCC000000;          // semi-transparent black
    public static final int BG_PANEL = 0xAA1A1A1A;         // header/panel
    public static final int BG_CONTENT = 0xFF0A0A0A;       // content area
    public static final int BG_TABLE_HEADER = 0xFF333333;  // table header
    public static final int BG_PLACEHOLDER = 0xFF444444;   // image placeholder
    public static final int BG_SCROLLBAR_TRACK = 0xFF333333;
    public static final int BG_SCROLLBAR_THUMB = 0xFF6C6C6C;
    public static final int BG_HOVER = 0x40FFFFFF;         // hover overlay

    // ── Text colors ──
    public static final int TEXT_WHITE = 0xFFFFFFFF;
    public static final int TEXT_GRAY = 0xFFAAAAAA;
    public static final int TEXT_DIM = 0xFF666666;
    public static final int TEXT_HEADING_1 = 0xFFFFAA00;
    public static final int TEXT_HEADING_2 = 0xFFFFD700;
    public static final int TEXT_LINK = 0xFF5555FF;
    public static final int TEXT_ERROR = 0xFFFF5555;
    public static final int TEXT_CAPTCHA_SUBTITLE = 0xFF888888;
    public static final int TEXT_SUCCESS = 0xFF55FF55;        // success/success green
    public static final int TEXT_PLACEHOLDER = 0xFF808080;    // placeholder text
    public static final int TEXT_SELECTED = 0xFFFFFFFF;       // selected item text

    // ── Border colors (top/left = light, bottom/right = dark, simulating top-left light source) ──
    public static final int BORDER = 0xFF555555;              // dark border (bottom/right)
    public static final int BORDER_LIGHT = 0xFF888888;        // light border (top/left)

    // ── Dimensions ──
    public static final int PADDING = 4;
    public static final int PADDING_SMALL = 2;
    public static final int HEADER_HEIGHT = 18;
    public static final int BACK_BUTTON_SIZE = 14;
    public static final int SCROLLBAR_WIDTH = 6;
    public static final int LIST_ITEM_HEIGHT = 16;
    public static final int STATUS_HEIGHT = 16;
    public static final int SECTION_GAP = 4;
    public static final int SIDE_MARGIN = 12;
    public static final int ROW_PADDING_X = 3;
    public static final int SCROLL_STEP = 20;

    // ── Chip/Tag colors ──
    public static final int CHIP_MOD_TEXT = 0xFF55FF55;       // mod filter tag text
    public static final int CHIP_MOD_BG = 0xFF1A3A1A;         // mod filter tag background
    public static final int CHIP_MOD_BORDER = 0xFF2A5A2A;     // mod filter tag border
    public static final int CHIP_DETAIL_TEXT = 0xFF5555FF;    // detail tag text (same as TEXT_LINK)
    public static final int CHIP_DETAIL_BG = 0xFF2A2A4A;      // detail tag background

    // ── Animation ──
    /** Duration of page slide animation in milliseconds */
    public static final long ANIM_DURATION_MS = 200;
}
