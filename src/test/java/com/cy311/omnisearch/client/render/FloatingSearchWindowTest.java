package com.cy311.omnisearch.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FloatingSearchWindowTest {

    private final FloatingSearchWindow window = new FloatingSearchWindow();

    @Test
    void computeBounds_isRightAnchored() {
        var bounds = window.computeBounds(1920, 1080);

        assertEquals(400, bounds.width());
        assertEquals(560, bounds.height());
        assertEquals(1920 - 400 - 12, bounds.x());
        assertEquals((1080 - 560) / 2, bounds.y());
    }

    @Test
    void computeBounds_scalesDownOnSmallerScreens() {
        var small = window.computeBounds(1280, 720);
        var large = window.computeBounds(2560, 1440);

        assertEquals(400, small.width());
        assertEquals(560, small.height());
        assertEquals(400, large.width());
        assertEquals(560, large.height());
    }

    @Test
    void computeBounds_fitsOnWorstCaseAutoScale() {
        // 1920x1080 Auto guiScale=4 -> scaled 480x270
        var bounds = window.computeBounds(480, 270);

        assertTrue(bounds.width() >= 200);
        assertTrue(bounds.width() <= 480 - 24);
        assertTrue(bounds.height() >= 200);
        assertTrue(bounds.height() <= 270 - 24);
        assertTrue(bounds.y() >= 12);
        assertTrue(bounds.y() + bounds.height() <= 270 - 12);
        assertTrue(bounds.x() + bounds.width() <= 480 - 12);
    }

    @Test
    void computeBounds_leavesVisibleMargins() {
        var bounds = window.computeBounds(1600, 900);

        assertTrue(bounds.y() >= 12);
        assertTrue(bounds.y() + bounds.height() <= 900 - 12);
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
        assertEquals(bounds.x() + 4, search[0]);
        assertEquals(bounds.x() + 4, body[0]);
        assertEquals(bounds.x() + 4, status[0]);
    }
}
