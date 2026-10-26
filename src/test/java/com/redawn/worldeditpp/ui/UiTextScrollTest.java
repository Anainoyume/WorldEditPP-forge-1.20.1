package com.redawn.common.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiTextScrollTest {
    @Test
    void textThatFitsNeverMoves() {
        assertFalse(UiTextScroll.overflows(80, 100));
        assertEquals(0, UiTextScroll.offset(80, 100, 50_000L));
    }

    @Test
    void overflowingTextPausesThenSlidesToTheSuffix() {
        int width = 100;
        int textWidth = 200;
        assertTrue(UiTextScroll.overflows(textWidth, width));
        assertEquals(0, UiTextScroll.offset(textWidth, width, 0));
        assertEquals(0, UiTextScroll.offset(textWidth, width, UiTextScroll.END_PAUSE_MILLIS - 1));
        assertTrue(UiTextScroll.offset(textWidth, width, UiTextScroll.END_PAUSE_MILLIS + 2_000L) > 0);
        int distance = textWidth - width;
        long travel = (long) distance * 1000L / UiTextScroll.PIXELS_PER_SECOND;
        assertEquals(distance, UiTextScroll.offset(textWidth, width,
                UiTextScroll.END_PAUSE_MILLIS + travel));
    }

    @Test
    void elapsedTimeIsCyclic() {
        int width = 100;
        int textWidth = 200;
        int distance = textWidth - width;
        long travel = (long) distance * 1000L / UiTextScroll.PIXELS_PER_SECOND;
        long cycle = UiTextScroll.END_PAUSE_MILLIS * 2L + travel;
        assertEquals(UiTextScroll.offset(textWidth, width, 500L),
                UiTextScroll.offset(textWidth, width, 500L + cycle));
    }
}
