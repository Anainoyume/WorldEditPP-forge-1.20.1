package com.redawn.worldeditpp.bridge.minecraft.client.tutorial;

import com.redawn.common.ui.tutorial.TutorialStyle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Compact hollow button shared by the entry point and wiki navigation. */
public final class TutorialButton extends AbstractButton {
    private final Runnable action;
    public TutorialButton(int x, int y, int width, int height, Component message, Runnable action) {
        super(x, y, width, height, message); this.action = action;
    }
    @Override public void onPress() { action.run(); }
    @Override protected void renderWidget(GuiGraphics g, int mx, int my, float tick) {
        int ink = isHoveredOrFocused() ? TutorialStyle.THEME : TutorialStyle.TEXT;
        if (isHoveredOrFocused()) g.fill(getX(), getY(), getX() + width, getY() + height, 0x18FFFFFF);
        g.renderOutline(getX(), getY(), width, height, ink);
        var font = Minecraft.getInstance().font;
        var text = getMessage().getVisualOrderText();
        int textX = getX() + Math.round((width - font.width(text)) / 2.0F);
        int textY = getY() + Math.round((height - font.lineHeight) / 2.0F);
        g.drawString(font, text, textX, textY, ink, false);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
