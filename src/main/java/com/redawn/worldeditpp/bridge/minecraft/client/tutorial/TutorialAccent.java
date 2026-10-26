package com.redawn.worldeditpp.bridge.minecraft.client.tutorial;

import com.redawn.common.ui.tutorial.TutorialStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Shared decoration whose text insets also apply to wrapping and navigation hit testing. */
final class TutorialAccent implements TutorialContent {
    private static final int INSET = 8;
    private final TutorialContent text;
    private final boolean quote;

    TutorialAccent(TutorialContent text, boolean quote) { this.text = text; this.quote = quote; }
    private int padding() { return quote ? 6 : 0; }
    private int textOffsetY() {
        if (!quote && text instanceof TutorialText heading) {
            // Center the glyph box, excluding the extra leading in the last line.
            return Math.max(0, Math.round((TutorialText.LINE_HEIGHT - Minecraft.getInstance().font.lineHeight)
                    * heading.textScale() / 2));
        }
        return padding();
    }
    @Override public int spacingAfter() { return quote ? TutorialContent.super.spacingAfter() : 2; }
    private int textWidth(int width) { return Math.max(1, width - INSET - (quote ? 6 : 0)); }
    @Override public int height(int width, int viewportHeight) {
        return text.height(textWidth(width), viewportHeight) + 2 * padding();
    }
    @Override public void render(GuiGraphics g, int x, int y, int width, int height, int mx, int my, float tick) {
        if (quote) g.fill(x, y, x + width, y + height, 0x10FFFFFF);
        g.fill(x, y, x + 2, y + Math.max(1, height - (quote ? 0 : 2)), TutorialStyle.THEME);
        text.render(g, x + INSET, y + textOffsetY(), textWidth(width), height - 2 * padding(), mx, my, tick);
    }
    @Override public TutorialNavigation.Link navigationAt(double x, double y, int width) {
        return text.navigationAt(x - INSET, y - textOffsetY(), textWidth(width));
    }
    @Override public String searchText() { return text.searchText(); }
    @Override public void close() { text.close(); }
}
