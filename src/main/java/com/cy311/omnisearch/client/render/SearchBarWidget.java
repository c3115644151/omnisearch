package com.cy311.omnisearch.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

// verified: GuiGraphics fill/hLine/vLine signatures from lexxie.dev NeoForge 1.21.1 javadoc 2026-06-14
// verified: EditBox constructor (Font, int, int, int, int, Component) from lexxie.dev NeoForge 1.21.1 2026-06-14
// verified: Font width(String) from lexxie.dev NeoForge 1.21.1 javadoc 2026-06-14

/**
 * Classic vanilla-style search bar.
 * <p>
 * Renders a stone-gray (#C6C6C6) panel with double border (white highlight + dark shadow),
 * containing a black EditBox input area.
 */
public class SearchBarWidget {

    private static final int STONE_GRAY = 0xFFC6C6C6;
    private static final int BORDER_DARK = 0xFF373737;
    private static final int BORDER_WHITE = 0xFFFFFFFF;
    private static final int BLACK_BG = 0xFF000000;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int PLACEHOLDER_GRAY = 0xFF808080;

    private static final int PANEL_PADDING = 4;
    private static final int BORDER_WIDTH = 1;

    private final EditBox editBox;

    public SearchBarWidget(Font font, int x, int y, int width) {
        int innerX = x + PANEL_PADDING + BORDER_WIDTH;
        int innerY = y + PANEL_PADDING + BORDER_WIDTH;
        int innerWidth = width - (PANEL_PADDING + BORDER_WIDTH) * 2;

        // EditBox renders internally but its styling is overwritten by our panel.
        // The edit box needs height; we use font.lineHeight + 4 for single-line input.
        this.editBox = new EditBox(font, innerX, innerY, innerWidth, font.lineHeight + 4, Component.empty());
        this.editBox.setTextColor(TEXT_WHITE);
        this.editBox.setBordered(false);
        this.editBox.setHint(Component.literal("搜索MC百科...").withColor(PLACEHOLDER_GRAY));
    }

    /**
     * Renders the search bar.
     *
     * @param gui   the GuiGraphics instance
     * @param x     left edge of the search bar panel
     * @param y     top edge of the search bar panel
     * @param width total width of the search bar panel
     * @param query the current query string
     */
    public void render(GuiGraphics gui, int x, int y, int width, @Nullable String query) {
        int height = editBox.getHeight() + (PANEL_PADDING + BORDER_WIDTH) * 2;

        // ---- Outer background (stone gray) ----
        gui.fill(x, y, x + width, y + height, STONE_GRAY); // verified: fill(int,int,int,int,int) from lexxie.dev javadoc 2026-06-14

        // ---- Double border (classic vanilla style) ----
        // Outer layer: white highlight on top/left, dark shadow on bottom/right
        gui.hLine(x, x + width - 1, y, BORDER_WHITE);
        gui.vLine(x, y, y + height - 1, BORDER_WHITE);
        gui.hLine(x, x + width - 1, y + height - 1, BORDER_DARK);
        gui.vLine(x + width - 1, y, y + height - 1, BORDER_DARK);

        // Inner layer: dark on top/left, white on bottom/right (reversed shading)
        gui.hLine(x + 1, x + width - 2, y + 1, BORDER_DARK);
        gui.vLine(x + 1, y + 1, y + height - 2, BORDER_DARK);
        gui.hLine(x + 1, x + width - 2, y + height - 2, BORDER_WHITE);
        gui.vLine(x + width - 2, y + 1, y + height - 2, BORDER_WHITE);

        // ---- Inner edit box background (black) ----
        int innerX = x + PANEL_PADDING + BORDER_WIDTH;
        int innerY = y + PANEL_PADDING + BORDER_WIDTH;
        int innerW = width - (PANEL_PADDING + BORDER_WIDTH) * 2;
        int innerH = editBox.getHeight();
        gui.fill(innerX, innerY, innerX + innerW, innerY + innerH, BLACK_BG);

        // ---- Update and render EditBox ----
        // Sync position in case x/y changed
        editBox.setX(innerX);
        editBox.setY(innerY);
        if (query != null && !query.equals(editBox.getValue())) {
            editBox.setValue(query);
        }
        editBox.render(gui, 0, 0, 0); // verified: EditBox.render(GuiGraphics,int,int,float) from lexxie.dev 1.21.1 2026-06-14
    }

    public EditBox getEditBox() {
        return editBox;
    }
}
