package com.redawn.worldeditpp.bridge.minecraft.client.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.redawn.common.ui.UiCanvas;
import com.redawn.common.ui.UiColor;
import com.redawn.common.ui.UiRect;
import com.redawn.common.ui.UiTexture;
import com.redawn.common.ui.UiText;
import net.minecraft.client.gui.GuiGraphics;

/** Minecraft GuiGraphics adapter. Must be used on the client render thread. */
public final class MinecraftUiCanvas implements UiCanvas {
    private final GuiGraphics graphics;
    private final net.minecraft.client.gui.Font font;
    private final int width, height;

    public MinecraftUiCanvas(GuiGraphics graphics, net.minecraft.client.gui.Font font, int width, int height) {
        this.graphics = graphics;
        this.font = font;
        this.width = width;
        this.height = height;
    }

    public GuiGraphics graphics() { return graphics; }

    @Override public int width() { return width; }
    @Override public int height() { return height; }
    @Override public int fontHeight() { return font.lineHeight; }

    @Override public int textWidth(UiText text) {
        return font.width(MinecraftUiText.toComponent(text));
    }

    @Override public void fill(UiRect rect, UiColor color) {
        if (rect.width() == 0 || rect.height() == 0 || color.alpha() == 0) return;
        graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), color.argb());
    }

    @Override public void text(UiText text, int x, int y, UiColor fallbackColor, boolean shadow) {
        graphics.drawString(font, MinecraftUiText.toComponent(text), x, y, fallbackColor.argb(), shadow);
    }

    @Override public void texture(UiTexture texture, UiRect destination, UiRect source) {
        graphics.blit(new net.minecraft.resources.ResourceLocation(texture.namespace(), texture.path()),
                destination.x(), destination.y(),
                destination.width(), destination.height(),
                source.x(), source.y(),
                source.width(), source.height(),
                source.width(), source.height());
    }

    @Override public void pushClip(UiRect rect) {
        graphics.enableScissor(rect.x(), rect.y(), rect.right(), rect.bottom());
    }

    @Override public void popClip() {
        graphics.disableScissor();
    }

    @Override public void push() {
        graphics.pose().pushPose();
    }

    @Override public void pop() {
        graphics.pose().popPose();
    }

    @Override public void translate(float x, float y) {
        graphics.pose().translate(x, y, 0.0F);
    }

    @Override public void scale(float x, float y) {
        graphics.pose().scale(x, y, 1.0F);
    }

    /** Pose stack access for bridge code that needs a temporary 3D-ish transform. */
    public PoseStack pose() {
        return graphics.pose();
    }
}
