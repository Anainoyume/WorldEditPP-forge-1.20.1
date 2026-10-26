package com.redawn.worldeditpp.bridge.minecraft.client.tutorial;

import com.redawn.common.ui.tutorial.TutorialSize;

import net.minecraft.client.gui.GuiGraphics;

/** A measured article component. */
public interface TutorialContent extends AutoCloseable {
    /** Called only for the selected page; priority distinguishes visible, nearby and header-only work. */
    default void prepareImages(int priority) { }
    default int spacingAfter() { return 14; }
    default int width(int availableWidth) { return availableWidth; }
    int height(int width, int viewportHeight);
    default TutorialSize.Dimensions measure(int availableWidth, int viewportHeight) {
        int measuredWidth = width(availableWidth);
        return new TutorialSize.Dimensions(measuredWidth, height(measuredWidth, viewportHeight));
    }
    void render(GuiGraphics graphics, int x, int y, int width, int height, int mouseX, int mouseY, float partialTick);
    default String searchText() { return ""; }
    default TutorialNavigation.Link navigationAt(double x, double y, int width) { return null; }
    @Override default void close() { }
}
