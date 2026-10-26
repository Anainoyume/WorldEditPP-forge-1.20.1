package com.redawn.common.ui;

/** Screen-space rectangle in GUI pixels. */
public record UiRect(int x, int y, int width, int height) {
    public UiRect {
        if (width < 0 || height < 0) throw new IllegalArgumentException("Negative UI rectangle");
    }

    public int right() { return x + width; }
    public int bottom() { return y + height; }

    public UiRect inset(int left, int top, int right, int bottom) {
        return new UiRect(x + left, y + top,
                Math.max(0, width - left - right),
                Math.max(0, height - top - bottom));
    }

    public UiRect inset(int amount) {
        return inset(amount, amount, amount, amount);
    }

    public UiRect offset(int dx, int dy) {
        return new UiRect(x + dx, y + dy, width, height);
    }

    public boolean contains(double px, double py) {
        return px >= x && py >= y && px < right() && py < bottom();
    }
}
