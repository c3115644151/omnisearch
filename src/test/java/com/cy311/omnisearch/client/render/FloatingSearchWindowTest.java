package com.cy311.omnisearch.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FloatingSearchWindowTest {

    private final FloatingSearchWindow window = new FloatingSearchWindow();

    @Test
    void computeBounds_isRightAnchored() {
        var bounds = window.computeBounds(1920, 1080);

        assertEquals(520, bounds.width());
        assertEquals(1040, bounds.height());
        assertEquals(1920 - 520 - 16, bounds.x());
        assertEquals(20, bounds.y());
    }

    @Test
    void computeBounds_clampsWidthWithinRange() {
        var small = window.computeBounds(1280, 720);
        var large = window.computeBounds(2560, 1440);

        assertEquals(435, small.width());
        assertEquals(520, large.width());
    }

    @Test
    void layoutSections_doNotOverlap() {
        var bounds = window.computeBounds(1600, 900);
        int searchBarHeight = 20;

        int[] search = window.getSearchBarBounds(bounds, searchBarHeight);
        int[] body = window.getBodyBounds(bounds, searchBarHeight);
        int[] status = window.getStatusBounds(bounds);

        assertTrue(search[1] >= bounds.y());
        assertTrue(body[1] > search[1] + search[3]);
        assertTrue(status[1] > body[1] + body[3]);
        assertEquals(bounds.x() + 6, search[0]);
        assertEquals(bounds.x() + 6, body[0]);
        assertEquals(bounds.x() + 6, status[0]);
    }
}
