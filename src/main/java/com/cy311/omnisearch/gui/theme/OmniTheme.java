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
    public static final int BG_ROW_ALT = 0xFF222222;       // alternating row
    public static final int BG_PLACEHOLDER = 0xFF444444;   // image placeholder
    public static final int BG_SCROLLBAR_TRACK = 0xFF333333;
    public static final int BG_SCROLLBAR_THUMB = 0xFF6C6C6C;
    public static final int BG_HOVER = 0x40FFFFFF;         // hover overlay

    // ── Text colors ──
    public static final int TEXT_WHITE = 0xFFFFFFFF;
    public static final int TEXT_LIGHT = 0xFFFFFFAA;
    public static final int TEXT_GRAY = 0xFFAAAAAA;
    public static final int TEXT_DIM = 0xFF666666;
    public static final int TEXT_HEADING_1 = 0xFFFFAA00;
    public static final int TEXT_HEADING_2 = 0xFFFFD700;
    public static final int TEXT_LINK = 0xFF5555FF;
    public static final int TEXT_ERROR = 0xFFFF5555;
    public static final int TEXT_BACK_BUTTON = 0xFFFFAA00;
    public static final int TEXT_CAPTCHA_SUBTITLE = 0xFF888888;

    // ── Border colors ──
    public static final int BORDER = 0xFF555555;
    public static final int BORDER_LIGHT = 0xFF888888;
    public static final int BORDER_PLACEHOLDER = 0xFF888888;

    // ── Dimensions ──
    public static final int PADDING = 6;
    public static final int PADDING_SMALL = 4;
    public static final int HEADER_HEIGHT = 30;
    public static final int METADATA_HEIGHT = 50;
    public static final int BACK_BUTTON_SIZE = 20;
    public static final int SCROLLBAR_WIDTH = 6;
    public static final int LIST_ITEM_HEIGHT = 20;

    // ── Animation ──
    /** Duration of page slide animation in milliseconds */
    public static final long ANIM_DURATION_MS = 200;
}
