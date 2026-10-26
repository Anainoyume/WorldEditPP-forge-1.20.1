package com.redawn.common.ui.tutorial;

import java.util.Locale;

/** CSS-like lengths in GUI pixels. Percentages use the containing article viewport. */
public record TutorialSize(Unit unit, double value) {
    public enum Unit { AUTO, PIXELS, PERCENT }
    public static final TutorialSize AUTO = new TutorialSize(Unit.AUTO, 0);
    public static TutorialSize parse(String text) {
        String input = text.trim().toLowerCase(Locale.ROOT);
        if (input.equals("auto")) return AUTO;
        Unit unit = input.endsWith("%") ? Unit.PERCENT : Unit.PIXELS;
        String number = input.replaceFirst("(px|%)$", "");
        double value = Double.parseDouble(number);
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("Invalid size: " + text);
        return new TutorialSize(unit, value);
    }
    public double resolve(double container, double automatic) {
        return switch (unit) {
            case AUTO -> automatic;
            case PIXELS -> value;
            case PERCENT -> container * value / 100;
        };
    }
    public record Dimensions(int width, int height) { }
    public static int mediaWidth(int articleWidth) { return Math.max(1, (int) Math.floor(articleWidth * 0.9)); }
    public static Dimensions image(TutorialSize width, TutorialSize height,
                                   int containerWidth, int containerHeight, int imageWidth, int imageHeight) {
        int maximumWidth = mediaWidth(containerWidth);
        double w = width.resolve(containerWidth, maximumWidth);
        double h = height.resolve(containerHeight, w * imageHeight / Math.max(1.0, imageWidth));
        double clamp = Math.min(1, Math.min(maximumWidth / w, 4096 / h));
        return new Dimensions(Math.max(1, (int) Math.round(w * clamp)), Math.max(1, (int) Math.round(h * clamp)));
    }
}
