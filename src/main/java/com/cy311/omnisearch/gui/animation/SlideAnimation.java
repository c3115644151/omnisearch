package com.cy311.omnisearch.gui.animation;

import com.cy311.omnisearch.gui.theme.OmniTheme;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Slide transition animation for page changes.
 * Tracks an offset that transitions from -100% to 0% (slide-in)
 * or 0% to +100% (slide-out) over the animation duration.
 */
public class SlideAnimation {

    private long startTime = -1;
    private int fromOffset;
    private int toOffset;
    private boolean active;

    /**
     * Start a slide-in animation (content slides from right to in).
     */
    public void startSlideIn() {
        startTime = System.currentTimeMillis();
        fromOffset = 1;  // start from right
        toOffset = 0;    // end at center
        active = true;
    }

    /**
     * Start a slide-out animation (content slides from in to left).
     */
    public void startSlideOut() {
        startTime = System.currentTimeMillis();
        fromOffset = 0;   // start at center
        toOffset = -1;    // end at left
        active = true;
    }

    /**
     * Returns the current horizontal offset as a fraction of screen width.
     * 0 = normal position, 1 = one screen to the right, -1 = one screen to the left.
     */
    public float getProgress() {
        if (!active || startTime < 0) return 0;
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= OmniTheme.ANIM_DURATION_MS) {
            active = false;
            return toOffset;
        }
        float t = (float) elapsed / OmniTheme.ANIM_DURATION_MS;
        // Ease out quad
        float eased = t * (2 - t);
        return fromOffset + (toOffset - fromOffset) * eased;
    }

    public boolean isActive() { return active; }

    /**
     * Apply the slide translation to the pose stack.
     * Caller must push/pop around this.
     */
    public void applyTransform(GuiGraphics g, int screenWidth) {
        float progress = getProgress();
        if (progress != 0) {
            g.pose().translate(progress * screenWidth, 0, 0);
        }
    }
}
