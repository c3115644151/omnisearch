package com.cy311.omnisearch.gui.component;

import com.cy311.omnisearch.client.render.ResultListWidget;
import com.cy311.omnisearch.data.model.SearchHit;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import java.util.List;

public class ResultList implements UIComponent {
    private final ResultListWidget widget;
    private final int x, y, w, h;
    private List<SearchHit> items = List.of();
    private int scrollOffset;
    private int selectedIndex = -1;

    public ResultList(Font font, int x, int y, int w, int h) {
        this.widget = new ResultListWidget(font);
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void setItems(List<SearchHit> items) { this.items = items; }
    public void setScrollOffset(int offset) { this.scrollOffset = offset; }
    public int getScrollOffset() { return scrollOffset; }
    public int getSelectedIndex() { return selectedIndex; }
    public void setSelectedIndex(int idx) { this.selectedIndex = idx; }
    public List<SearchHit> getItems() { return items; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        widget.render(g, x, y, w, h, items, selectedIndex, scrollOffset, mx, my);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            selectedIndex = widget.getRowAt((int)my, y, scrollOffset);
            return selectedIndex >= 0 && selectedIndex < items.size();
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        scrollOffset -= (int) Math.round(scrollY) * 3;
        scrollOffset = Math.max(0, scrollOffset);
        return true;
    }
}
