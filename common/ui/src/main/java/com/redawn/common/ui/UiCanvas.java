package com.redawn.common.ui;

/**
 * Minimal drawing surface for GUI/HUD code. Coordinates are GUI pixels; origin is the
 * top-left of the current surface. Implementations own platform/GPU state and must only be
 * used on their renderer thread.
 */
public interface UiCanvas {
    int width();
    int height();
    int fontHeight();

    int textWidth(UiText text);

    void fill(UiRect rect, UiColor color);

    default void frame(UiRect rect, int thickness, UiColor color) {
        if (thickness <= 0 || rect.width() == 0 || rect.height() == 0) return;
        int edge = Math.min(thickness, Math.min(rect.width(), rect.height()));
        fill(new UiRect(rect.x(), rect.y(), rect.width(), edge), color);
        fill(new UiRect(rect.x(), rect.bottom() - edge, rect.width(), edge), color);
        fill(new UiRect(rect.x(), rect.y() + edge, edge, Math.max(0, rect.height() - edge * 2)), color);
        fill(new UiRect(rect.right() - edge, rect.y() + edge, edge,
                Math.max(0, rect.height() - edge * 2)), color);
    }

    void text(UiText text, int x, int y, UiColor fallbackColor, boolean shadow);

    /**
     * Draws text inside a fixed-width slot. Overflowing text scrolls instead of spilling
     * into adjacent controls. The caller supplies a monotonic elapsed time in milliseconds.
     */
    default void textScrolling(UiText text, int x, int y, int visibleWidth,
                               UiColor fallbackColor, boolean shadow, long elapsedMillis) {
        if (visibleWidth <= 0) return;
        int textWidth = textWidth(text);
        if (!UiTextScroll.overflows(textWidth, visibleWidth)) {
            text(text, x, y, fallbackColor, shadow);
            return;
        }
        int offset = UiTextScroll.offset(textWidth, visibleWidth, elapsedMillis);
        pushClip(new UiRect(x, y, visibleWidth, fontHeight()));
        try {
            text(text, x - offset, y, fallbackColor, shadow);
        } finally {
            popClip();
        }
    }

    void texture(UiTexture texture, UiRect destination, UiRect source);

    /** Clips to one rectangle. Nested clips are implementation-defined and should be avoided. */
    void pushClip(UiRect rect);
    void popClip();

    /** Small transform stack for icon scaling and future animated UI. */
    void push();
    void pop();
    void translate(float x, float y);
    void scale(float x, float y);
}
