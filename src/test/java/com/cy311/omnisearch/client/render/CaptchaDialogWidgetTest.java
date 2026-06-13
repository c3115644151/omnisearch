package com.cy311.omnisearch.client.render;

import com.cy311.omnisearch.data.model.CaptchaContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.Test;

import static com.cy311.omnisearch.client.render.RenderTestUtil.*;
import static org.mockito.Mockito.verify;

class CaptchaDialogWidgetTest {

    private static final int OVERLAY_ALPHA = 0xAA000000;
    private static final int PANEL_BG = 0xFFC6C6C6;
    private static final int BORDER_WHITE = 0xFFFFFFFF;
    private static final int BORDER_DARK = 0xFF373737;
    private static final int SUBTITLE_GRAY = 0xFF555555;
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

    private final Font font = createMockFont();
    private final GuiGraphics gui = createMockGuiGraphics();
    private final CaptchaDialogWidget widget = new CaptchaDialogWidget(font);

    private final CaptchaContext captcha = new CaptchaContext(
        "https://example.com/captcha.png",
        "captcha_abc123"
    );

    @Test
    void render_paintsOverlay() {
        int dialogHeight = widget.computeDialogHeight();
        widget.render(gui, 100, 100, captcha);

        verify(gui).fill(80, 80, 360, 100 + dialogHeight + 20, OVERLAY_ALPHA);
    }

    @Test
    void render_paintsPanelBackground() {
        int dialogHeight = widget.computeDialogHeight();
        widget.render(gui, 100, 100, captcha);

        verify(gui).fill(100, 100, 340, 100 + dialogHeight, PANEL_BG);
    }

    @Test
    void render_rendersDoubleBorder() {
        int dialogHeight = widget.computeDialogHeight();
        widget.render(gui, 100, 100, captcha);

        // Outer: white top/left, dark bottom/right
        verify(gui).hLine(100, 339, 100, BORDER_WHITE);
        verify(gui).vLine(100, 100, 100 + dialogHeight - 1, BORDER_WHITE);
        verify(gui).hLine(100, 339, 100 + dialogHeight - 1, BORDER_DARK);
        verify(gui).vLine(339, 100, 100 + dialogHeight - 1, BORDER_DARK);
        // Inner: dark top/left, white bottom/right
        verify(gui).hLine(101, 338, 101, BORDER_DARK);
        verify(gui).vLine(101, 101, 100 + dialogHeight - 2, BORDER_DARK);
        verify(gui).hLine(101, 338, 100 + dialogHeight - 2, BORDER_WHITE);
        verify(gui).vLine(338, 101, 100 + dialogHeight - 2, BORDER_WHITE);
    }

    @Test
    void render_rendersTitleAndSubtitle() {
        widget.render(gui, 100, 100, captcha);

        int titleY = 100 + DIALOG_PADDING + BORDER;
        verify(gui).drawCenteredString(font, "验证码 (CAPTCHA)", 100 + DIALOG_WIDTH / 2, titleY, SUBTITLE_GRAY);

        int subtitleY = titleY + TITLE_HEIGHT;
        verify(gui).drawCenteredString(font, "请输入验证码以继续搜索", 100 + DIALOG_WIDTH / 2, subtitleY, SUBTITLE_GRAY);
    }

    @Test
    void render_rendersCaptchaPlaceholder() {
        widget.render(gui, 100, 100, captcha);

        int titleY = 100 + DIALOG_PADDING + BORDER;
        int subtitleY = titleY + TITLE_HEIGHT;
        int placeholderX = 100 + (DIALOG_WIDTH - CAPTCHA_WIDTH) / 2;
        int placeholderY = subtitleY + ELEMENT_GAP + 4;

        // Placeholder borders
        verify(gui).hLine(placeholderX, placeholderX + CAPTCHA_WIDTH - 1, placeholderY, BORDER_DARK);
        verify(gui).vLine(placeholderX, placeholderY, placeholderY + CAPTCHA_HEIGHT - 1, BORDER_DARK);
        verify(gui).hLine(placeholderX, placeholderX + CAPTCHA_WIDTH - 1, placeholderY + CAPTCHA_HEIGHT - 1, BORDER_WHITE);
        verify(gui).vLine(placeholderX + CAPTCHA_WIDTH - 1, placeholderY, placeholderY + CAPTCHA_HEIGHT - 1, BORDER_WHITE);

        // Placeholder backgrounds
        verify(gui).fill(placeholderX + 1, placeholderY + 1, placeholderX + CAPTCHA_WIDTH - 1, placeholderY + CAPTCHA_HEIGHT - 1, PLACEHOLDER_BG);
        verify(gui).fill(placeholderX + 2, placeholderY + 2, placeholderX + CAPTCHA_WIDTH - 2, placeholderY + CAPTCHA_HEIGHT - 2, PLACEHOLDER_INNER);

        // "CAPTCHA" text centered
        int textY = placeholderY + (CAPTCHA_HEIGHT - font.lineHeight) / 2;
        verify(gui).drawCenteredString(font, "CAPTCHA", placeholderX + CAPTCHA_WIDTH / 2, textY, PLACEHOLDER_TEXT);
    }

    @Test
    void render_rendersCaptchaId() {
        widget.render(gui, 100, 100, captcha);

        int inputWidth = DIALOG_WIDTH - (DIALOG_PADDING + BORDER) * 2;
        int contentX = 100 + DIALOG_PADDING + BORDER;
        int titleY = 100 + DIALOG_PADDING + BORDER;
        int subtitleY = titleY + TITLE_HEIGHT;
        int placeholderY = subtitleY + ELEMENT_GAP + 4;
        int inputY = placeholderY + CAPTCHA_HEIGHT + ELEMENT_GAP;
        int buttonY = inputY + INPUT_HEIGHT + ELEMENT_GAP;

        String idText = "ID: captcha_abc123";
        int idTextWidth = font.width(idText);
        int idX = 100 + DIALOG_WIDTH - DIALOG_PADDING - idTextWidth;
        int idY = buttonY + BUTTON_HEIGHT + 4;

        verify(gui).drawString(font, idText, idX, idY, 0xFF888888, false);
    }

    @Test
    void render_rendersSubmitButton() {
        widget.render(gui, 100, 100, captcha);

        int titleY = 100 + DIALOG_PADDING + BORDER;
        int subtitleY = titleY + TITLE_HEIGHT;
        int placeholderY = subtitleY + ELEMENT_GAP + 4;
        int inputY = placeholderY + CAPTCHA_HEIGHT + ELEMENT_GAP;
        int buttonX = 100 + (DIALOG_WIDTH - BUTTON_WIDTH) / 2;
        int buttonY = inputY + INPUT_HEIGHT + ELEMENT_GAP;

        // Button border
        verify(gui).hLine(buttonX, buttonX + BUTTON_WIDTH - 1, buttonY, BORDER_WHITE);
        verify(gui).vLine(buttonX, buttonY, buttonY + BUTTON_HEIGHT - 1, BORDER_WHITE);
        verify(gui).hLine(buttonX, buttonX + BUTTON_WIDTH - 1, buttonY + BUTTON_HEIGHT - 1, BORDER_DARK);
        verify(gui).vLine(buttonX + BUTTON_WIDTH - 1, buttonY, buttonY + BUTTON_HEIGHT - 1, BORDER_DARK);
        // Button background
        verify(gui).fill(buttonX + 1, buttonY + 1, buttonX + BUTTON_WIDTH - 1, buttonY + BUTTON_HEIGHT - 1, BUTTON_BG);
        // Button text
        verify(gui).drawCenteredString(font, "提交", buttonX + BUTTON_WIDTH / 2, buttonY + (BUTTON_HEIGHT - font.lineHeight) / 2, BUTTON_TEXT);
    }

    @Test
    void render_rendersInputField() {
        widget.render(gui, 100, 100, captcha);

        int contentX = 100 + DIALOG_PADDING + BORDER;
        int inputWidth = DIALOG_WIDTH - (DIALOG_PADDING + BORDER) * 2;
        int titleY = 100 + DIALOG_PADDING + BORDER;
        int subtitleY = titleY + TITLE_HEIGHT;
        int placeholderY = subtitleY + ELEMENT_GAP + 4;
        int inputY = placeholderY + CAPTCHA_HEIGHT + ELEMENT_GAP;

        // Input field black background
        verify(gui).fill(contentX, inputY, contentX + inputWidth, inputY + INPUT_HEIGHT, 0xFF000000);
        // Input field inner border
        verify(gui).hLine(contentX, contentX + inputWidth - 1, inputY, BORDER_DARK);
        verify(gui).vLine(contentX, inputY, inputY + INPUT_HEIGHT - 1, BORDER_DARK);
        verify(gui).hLine(contentX, contentX + inputWidth - 1, inputY + INPUT_HEIGHT - 1, BORDER_WHITE);
        verify(gui).vLine(contentX + inputWidth - 1, inputY, inputY + INPUT_HEIGHT - 1, BORDER_WHITE);
    }

    @Test
    void computeDialogHeight_returnsPositiveValue() {
        int height = widget.computeDialogHeight();
        org.junit.jupiter.api.Assertions.assertTrue(height > 0);
    }
}
