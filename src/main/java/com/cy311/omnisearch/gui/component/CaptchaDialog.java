package com.cy311.omnisearch.gui.component;

import com.cy311.omnisearch.client.render.CaptchaDialogWidget;
import com.cy311.omnisearch.data.model.CaptchaContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class CaptchaDialog implements UIComponent {
    private final CaptchaDialogWidget widget;
    private CaptchaContext captcha;

    public CaptchaDialog(Font font) {
        this.widget = new CaptchaDialogWidget(font);
    }

    public void setCaptcha(CaptchaContext captcha) { this.captcha = captcha; }
    public CaptchaDialogWidget getWidget() { return widget; }
    public int[] getImageBounds(int dx, int dy) { return widget.getImageBounds(dx, dy); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        if (captcha != null) {
            int dx = 0, dy = 0;
            widget.render(g, dx, dy, captcha);
        }
    }

    public void renderAt(GuiGraphics g, int dx, int dy, CaptchaContext captcha) {
        widget.render(g, dx, dy, captcha);
    }
}
