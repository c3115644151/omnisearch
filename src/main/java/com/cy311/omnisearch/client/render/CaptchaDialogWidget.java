package com.cy311.omnisearch.client.render;

import com.cy311.omnisearch.data.model.CaptchaContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

// verified: GuiGraphics fill/hLine/vLine/drawCenteredString signatures from lexxie.dev NeoForge 1.21.1 javadoc 2026-06-14
// verified: Font.width(String) from lexxie.dev NeoForge 1.21.1 2026-06-14

/**
 * Centered CAPTCHA verification dialog, styled after MC login/alert dialogs.
 * <p>
 * Renders a stone-gray classic vanilla panel with:
 * <ul>
 *   <li>CAPTCHA image placeholder (gray rectangle)</li>
 *   <li>Text input field area</li>
 *   <li>Submit button</li>
 * </ul>
 * <p>
 * This widget renders the dialog container only. The Screen layer is responsible
 * for managing the interactive EditBox and handling submit click events.
 */
public class CaptchaDialogWidget {

    private static final int OVERLAY_ALPHA = 0xAA000000;
    private static final int PANEL_BG = 0xFFC6C6C6;
    private static final int BORDER_WHITE = 0xFFFFFFFF;
    private static final int BORDER_DARK = 0xFF373737;
    private static final int SUBTITLE_GRAY = 0xFF555555;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int PLACEHOLDER_BG = 0xFF666666;
    private static final int PLACEHOLDER_INNER = 0xFF444444;
    private static final int PLACEHOLDER_TEXT = 0xFFAAAAAA;
    private static final int BUTTON_BG = 0xFF6C6C6C;
    private static final int BUTTON_TEXT = 0xFFFFFFFF;

    private static final int DIALOG_WIDTH = 240;
    private static final int DIALOG_PADDING = 8;
    private static final int CAPTCHA_WIDTH = 200;
    private static final int CAPTCHA_HEIGHT = 60;
    private static final int TITLE_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 60;
    private static final int BUTTON_HEIGHT = 20;
    private static final int INPUT_HEIGHT = 20;
    private static final int ELEMENT_GAP = 8;
    private static final int BORDER = 1;

    private final Font font;

    public CaptchaDialogWidget(Font font) {
        this.font = font;
    }

    /**
     * Renders the CAPTCHA dialog centered at the given position.
     *
     * @param gui    the GuiGraphics instance
     * @param x      left edge of the dialog
     * @param y      top edge of the dialog
     * @param captcha the CAPTCHA context (holds image URL and captcha ID)
     */
    public void render(GuiGraphics gui, int x, int y, CaptchaContext captcha) {
        int dialogHeight = computeDialogHeight();
        int dialogWidth = DIALOG_WIDTH;

        // ---- Dark overlay behind dialog ----
        // Overlay covers a large area centered around the dialog.
        // The Screen may already render a full overlay; this provides a safe fallback.
        gui.fill(x - 20, y - 20, x + dialogWidth + 20, y + dialogHeight + 20, OVERLAY_ALPHA);

        // ---- Panel background ----
        gui.fill(x, y, x + dialogWidth, y + dialogHeight, PANEL_BG);

        // ---- Double border (classic vanilla) ----
        // Outer: white top/left, dark bottom/right
        gui.hLine(x, x + dialogWidth - 1, y, BORDER_WHITE);
        gui.vLine(x, y, y + dialogHeight - 1, BORDER_WHITE);
        gui.hLine(x, x + dialogWidth - 1, y + dialogHeight - 1, BORDER_DARK);
        gui.vLine(x + dialogWidth - 1, y, y + dialogHeight - 1, BORDER_DARK);
        // Inner: dark top/left, white bottom/right
        gui.hLine(x + 1, x + dialogWidth - 2, y + 1, BORDER_DARK);
        gui.vLine(x + 1, y + 1, y + dialogHeight - 2, BORDER_DARK);
        gui.hLine(x + 1, x + dialogWidth - 2, y + dialogHeight - 2, BORDER_WHITE);
        gui.vLine(x + dialogWidth - 2, y + 1, y + dialogHeight - 2, BORDER_WHITE);

        // ---- Title ----
        int contentX = x + DIALOG_PADDING + BORDER;
        int titleY = y + DIALOG_PADDING + BORDER;
        gui.drawCenteredString(font, "验证码 (CAPTCHA)", x + dialogWidth / 2, titleY, SUBTITLE_GRAY); // verified: drawCenteredString(Font,String,int,int,int) from lexxie.dev 2026-06-14

        // ---- Subtitle text ----
        int subtitleY = titleY + TITLE_HEIGHT;
        gui.drawCenteredString(font, "请输入验证码以继续搜索", x + dialogWidth / 2, subtitleY, SUBTITLE_GRAY);

        // ---- CAPTCHA image placeholder (200x60 gray rect) ----
        int placeholderX = x + (dialogWidth - CAPTCHA_WIDTH) / 2;
        int placeholderY = subtitleY + ELEMENT_GAP + 4;
        drawCaptchaPlaceholder(gui, placeholderX, placeholderY, CAPTCHA_WIDTH, CAPTCHA_HEIGHT);

        // ---- Input field area (black background rectangle) ----
        int inputY = placeholderY + CAPTCHA_HEIGHT + ELEMENT_GAP;
        int inputWidth = dialogWidth - (DIALOG_PADDING + BORDER) * 2;
        gui.fill(contentX, inputY, contentX + inputWidth, inputY + INPUT_HEIGHT, 0xFF000000);
        // Input field inner border
        gui.hLine(contentX, contentX + inputWidth - 1, inputY, BORDER_DARK);
        gui.vLine(contentX, inputY, inputY + INPUT_HEIGHT - 1, BORDER_DARK);
        gui.hLine(contentX, contentX + inputWidth - 1, inputY + INPUT_HEIGHT - 1, BORDER_WHITE);
        gui.vLine(contentX + inputWidth - 1, inputY, inputY + INPUT_HEIGHT - 1, BORDER_WHITE);

        // ---- Submit button ----
        int buttonX = x + (dialogWidth - BUTTON_WIDTH) / 2;
        int buttonY = inputY + INPUT_HEIGHT + ELEMENT_GAP;
        drawSubmitButton(gui, buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT);

        // ---- Captcha ID (hidden info, displayed for debugging) ----
        String idText = "ID: " + captcha.captchaId();
        int idTextWidth = font.width(idText);
        gui.drawString(font, idText, x + dialogWidth - DIALOG_PADDING - idTextWidth,
                buttonY + BUTTON_HEIGHT + 4, 0xFF888888, false);
    }

    private void drawCaptchaPlaceholder(GuiGraphics gui, int x, int y, int width, int height) {
        // Outer border
        gui.hLine(x, x + width - 1, y, BORDER_DARK);
        gui.vLine(x, y, y + height - 1, BORDER_DARK);
        gui.hLine(x, x + width - 1, y + height - 1, BORDER_WHITE);
        gui.vLine(x + width - 1, y, y + height - 1, BORDER_WHITE);

        // Background
        gui.fill(x + 1, y + 1, x + width - 1, y + height - 1, PLACEHOLDER_BG);
        // Inner area
        gui.fill(x + 2, y + 2, x + width - 2, y + height - 2, PLACEHOLDER_INNER);

        // "CAPTCHA" label centered
        gui.drawCenteredString(font, "CAPTCHA", x + width / 2, y + (height - font.lineHeight) / 2, PLACEHOLDER_TEXT);
    }

    private void drawSubmitButton(GuiGraphics gui, int x, int y, int width, int height) {
        // Button border (classic MC button style)
        gui.hLine(x, x + width - 1, y, BORDER_WHITE);
        gui.vLine(x, y, y + height - 1, BORDER_WHITE);
        gui.hLine(x, x + width - 1, y + height - 1, BORDER_DARK);
        gui.vLine(x + width - 1, y, y + height - 1, BORDER_DARK);

        // Button background
        gui.fill(x + 1, y + 1, x + width - 1, y + height - 1, BUTTON_BG);

        // Button text centered
        gui.drawCenteredString(font, "提交", x + width / 2, y + (height - font.lineHeight) / 2, BUTTON_TEXT);
    }

    /**
     * Returns the total height of the dialog based on its content layout.
     */
    public int computeDialogHeight() {
        int titleArea = DIALOG_PADDING + BORDER + TITLE_HEIGHT + (font.lineHeight + 2); // title + subtitle
        int placeholderArea = CAPTCHA_HEIGHT;
        int inputArea = INPUT_HEIGHT;
        int buttonArea = BUTTON_HEIGHT;
        int totalContent = titleArea + ELEMENT_GAP + 4 + placeholderArea + ELEMENT_GAP
                + inputArea + ELEMENT_GAP + buttonArea + 4 + font.lineHeight + DIALOG_PADDING;
        return totalContent;
    }

    /**
     * Returns the fixed dialog width.
     */
    public int getDialogWidth() {
        return DIALOG_WIDTH;
    }
}
