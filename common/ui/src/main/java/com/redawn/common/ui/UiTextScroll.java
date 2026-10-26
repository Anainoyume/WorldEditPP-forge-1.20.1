package com.redawn.common.ui;

/**
 * Deterministic marquee timing for text that is wider than its fixed visible slot.
 * The text pauses at the start, scrolls left until the suffix is visible, then snaps back
 * to the start after another pause. Callers own clipping and the actual draw call.
 */
public final class UiTextScroll {
    public static final int PIXELS_PER_SECOND = 18;
    public static final long END_PAUSE_MILLIS = 900L;

    public static int offset(int textWidth, int visibleWidth, long elapsedMillis) {
        if (textWidth <= 0 || visibleWidth <= 0) return 0;
        int distance = textWidth - visibleWidth;
        if (distance <= 0) return 0;
        long travelMillis = Math.max(1L, (long) distance * 1000L / PIXELS_PER_SECOND);
        long cycle = END_PAUSE_MILLIS * 2L + travelMillis;
        long time = Math.floorMod(elapsedMillis, cycle);
        if (time < END_PAUSE_MILLIS) return 0;
        time -= END_PAUSE_MILLIS;
        if (time < travelMillis) return (int) (time * PIXELS_PER_SECOND / 1000L);
        return distance;
    }

    public static boolean overflows(int textWidth, int visibleWidth) {
        return textWidth > visibleWidth;
    }

    private UiTextScroll() { }
}
