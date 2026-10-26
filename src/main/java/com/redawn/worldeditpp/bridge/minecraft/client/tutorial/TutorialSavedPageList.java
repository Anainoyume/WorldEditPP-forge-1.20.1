package com.redawn.worldeditpp.bridge.minecraft.client.tutorial;

import com.redawn.common.ui.tutorial.TutorialStyle;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Compact clickable page rows used by favorites and recent-history views. */
final class TutorialSavedPageList implements TutorialContent {
    static final int ROW_HEIGHT = 15;
    static final int REMOVE_WIDTH = 18;
    private final List<TutorialLibrary.Page> pages;

    TutorialSavedPageList(List<TutorialLibrary.Page> pages) {
        this.pages = List.copyOf(pages);
    }

    @Override public int height(int width, int viewportHeight) {
        return pages.size() * ROW_HEIGHT;
    }

    @Override public String searchText() {
        return String.join(" ", pages.stream().map(page -> page.title().getString()).toList());
    }

    @Override public void render(GuiGraphics graphics, int x, int y, int width, int height,
                                 int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        int numberWidth = font.width("00  ");
        for (int index = 0; index < pages.size(); index++) {
            int rowY = y + index * ROW_HEIGHT;
            boolean hovered = mouseX >= x && mouseX < x + width
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            boolean removeHovered = hovered && mouseX >= x + width - REMOVE_WIDTH;
            if (hovered) graphics.fill(x, rowY, x + width, rowY + ROW_HEIGHT - 1, 0x14FFFFFF);
            if (removeHovered) graphics.fill(x + width - REMOVE_WIDTH, rowY,
                    x + width, rowY + ROW_HEIGHT - 1, 0x26FFFFFF);

            int textY = rowY + (ROW_HEIGHT - font.lineHeight) / 2;
            graphics.drawString(font, String.format("%02d  ", index + 1), x + 2, textY,
                    TutorialStyle.MUTED, false);
            int titleX = x + 2 + numberWidth;
            int titleWidth = Math.max(1, width - numberWidth - REMOVE_WIDTH - 6);
            Component title = pages.get(index).title().copy().withStyle(style ->
                    style.withColor(TutorialStyle.THEME & 0xFFFFFF).withUnderlined(true));
            graphics.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(
                    font.substrByWidth(title, titleWidth)), titleX, textY, TutorialStyle.THEME, false);

            String remove = "-";
            int removeX = x + width - REMOVE_WIDTH + (REMOVE_WIDTH - font.width(remove)) / 2;
            graphics.drawString(font, remove, removeX, textY,
                    removeHovered ? TutorialStyle.TEXT : TutorialStyle.MUTED, false);
        }
    }

    TutorialLibrary.Page removalAt(double x, double y, int width) {
        int row = rowAt(y);
        return row < 0 || x < width - REMOVE_WIDTH || x >= width ? null : pages.get(row);
    }

    @Override public TutorialNavigation.Link navigationAt(double x, double y, int width) {
        int row = rowAt(y);
        if (row < 0 || x < 0 || x >= width - REMOVE_WIDTH) return null;
        var page = pages.get(row);
        return new TutorialNavigation.Link(page.id(), page.id().toString(), null);
    }

    private int rowAt(double y) {
        if (y < 0) return -1;
        int row = (int) (y / ROW_HEIGHT);
        return row >= pages.size() ? -1 : row;
    }
}
