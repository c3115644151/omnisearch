package com.cy311.omnisearch.gui.component;

import com.cy311.omnisearch.client.render.SearchBarWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class SearchBar implements UIComponent {
    private final SearchBarWidget widget;
    private int x, y, width;

    public SearchBar(Font font, int x, int y, int width) {
        this.widget = new SearchBarWidget(font, x, y, width);
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public SearchBarWidget getWidget() { return widget; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        widget.render(g, x, y, width, null);
    }

    @Override
    public boolean charTyped(char cp, int mod) {
        return widget.getEditBox().charTyped(cp, mod);
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        return widget.getEditBox().keyPressed(kc, sc, mod);
    }
}
