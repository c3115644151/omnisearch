package com.cy311.omnisearch.gui.component;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Unified interface for all UI components in the Omnisearch GUI.
 * Each component handles its own rendering and input.
 */
public interface UIComponent {

    /**
     * Render the component. Called every frame.
     */
    default void render(GuiGraphics g, int mx, int my, float delta) {}

    /**
     * Handle mouse click. Return true if consumed.
     */
    default boolean mouseClicked(double mx, double my, int button) { return false; }

    /**
     * Handle key press. Return true if consumed.
     */
    default boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }

    /**
     * Handle key typed (for text input). Return true if consumed.
     */
    default boolean charTyped(char codePoint, int modifiers) { return false; }

    /**
     * Handle mouse scroll. Return true if consumed.
     */
    default boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) { return false; }

    /**
     * Handle tick (called every game tick while screen is open).
     */
    default void tick() {}
}
