package com.redawn.common.ui;

/** An ARGB colour independent of any renderer or game class. */
public record UiColor(int argb) {
    public static UiColor rgb(int rgb) {
        return new UiColor(0xFF000000 | (rgb & 0x00FFFFFF));
    }

    public static final UiColor TRANSPARENT = new UiColor(0);

    public UiColor withAlpha(float alpha) {
        int value = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
        return new UiColor((value << 24) | (argb & 0x00FFFFFF));
    }

    public int alpha() { return argb >>> 24; }
    public int red() { return (argb >>> 16) & 0xFF; }
    public int green() { return (argb >>> 8) & 0xFF; }
    public int blue() { return argb & 0xFF; }
}
