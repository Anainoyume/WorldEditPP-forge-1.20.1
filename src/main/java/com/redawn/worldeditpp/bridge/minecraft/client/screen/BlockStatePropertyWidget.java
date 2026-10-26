package com.redawn.worldeditpp.bridge.minecraft.client.screen;

import java.util.List;
import java.util.function.Consumer;
import com.redawn.worldeditpp.bridge.minecraft.client.ui.GlassPanelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** One reusable enum/boolean/integer property selector with wheel and keyboard support. */
final class BlockStatePropertyWidget extends AbstractWidget {
    static final int HEIGHT = 18;
    static final int CONTROL_HEIGHT = 16;
    // A brighter tint of the selection yellow, reserved for property values.
    static int textColor(int alpha) { return (alpha << 24) | 0xFFF0A0; }
    private static final int ARROW_HIT_WIDTH = 12;
    final String property;
    final List<String> values;
    private final Consumer<String> onValue;
    private final Consumer<BlockStatePropertyWidget> onMenu;
    private String value;
    float opacity = 1;
    private double wheelRemainder;

    BlockStatePropertyWidget(String property, List<String> values, String value,
                             Consumer<String> onValue, Consumer<BlockStatePropertyWidget> onMenu) {
        super(0, 0, 200, HEIGHT, label("property", property));
        this.property = property;
        this.values = values;
        this.value = value;
        this.onValue = onValue;
        this.onMenu = onMenu;
    }

    static Component label(String category, String raw) {
        StringBuilder fallback = new StringBuilder();
        for (String word : raw.split("_")) {
            if (!fallback.isEmpty()) fallback.append(' ');
            if (!word.isEmpty()) fallback.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return Component.translatableWithFallback("screen.worldeditpp.block_state." + category + "." + raw,
                fallback.toString());
    }

    String value() { return value; }
    void syncValue(String value) { this.value = value; }
    int controlWidth() { return Math.min(150, Math.max(42, (int) (width * 0.55))); }
    int controlX() { return getX() + width - controlWidth(); }
    int controlY() { return getY() + (height - CONTROL_HEIGHT) / 2; }

    void choose(String next) {
        if (!values.contains(next) || next.equals(value)) return;
        value = next;
        onValue.accept(next);
    }

    void cycle(int delta) {
        choose(values.get(Math.floorMod(values.indexOf(value) + delta, values.size())));
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        if (isHoveredOrFocused()) graphics.fill(getX(), getY(), getX() + width, getY() + height,
                GlassPanelRenderer.color(0x12FFFFFF, opacity));
        int textY = getY() + (height - font.lineHeight) / 2;
        String name = font.plainSubstrByWidth(getMessage().getString(), Math.max(1, controlX() - getX() - 4));
        graphics.drawString(font, name, getX(), textY,
                GlassPanelRenderer.color(0xFFFFFFFF, opacity), false);
        int x = controlX(), y = controlY(), w = controlWidth();
        int white = GlassPanelRenderer.color(0xFFFFFFFF, opacity);
        graphics.renderOutline(x, y, w, CONTROL_HEIGHT, white);
        int ink = GlassPanelRenderer.color(textColor(255), opacity);
        int center = y + CONTROL_HEIGHT / 2 - 1;
        for (int i = 0; i < 4; i++) {
            graphics.fill(x + 5 + i, center - i, x + 6 + i, center + 1 + i, white);
            graphics.fill(x + w - 6 - i, center - i, x + w - 5 - i, center + 1 + i, white);
        }
        Component display = label("value", value);
        String text = font.plainSubstrByWidth(display.getString(), Math.max(1, w - 2 * ARROW_HIT_WIDTH - 2));
        graphics.pose().pushPose();
        graphics.pose().translate(x + (w - font.width(text)) / 2.0F,
                y + (CONTROL_HEIGHT - font.lineHeight) / 2.0F, 0);
        graphics.drawString(font, text, 0, 0, ink, false);
        graphics.pose().popPose();
    }

    @Override public void onClick(double mouseX, double mouseY) {
        if (mouseY < controlY() || mouseY >= controlY() + CONTROL_HEIGHT) return;
        if (mouseX < controlX() || mouseX >= controlX() + controlWidth()) return;
        if (mouseX < controlX() + ARROW_HIT_WIDTH) cycle(-1);
        else if (mouseX >= controlX() + controlWidth() - ARROW_HIT_WIDTH) cycle(1);
        else onMenu.accept(this);
    }

    @Override public boolean mouseScrolled(double x, double y, double vertical) {
        wheelRemainder += vertical;
        int steps = (int) wheelRemainder;
        wheelRemainder -= steps;
        if (steps != 0) cycle(-steps);
        return true;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            cycle(key == GLFW.GLFW_KEY_LEFT ? -1 : 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            onMenu.accept(this);
            return true;
        }
        return false;
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
                getMessage().copy().append(": ").append(label("value", value)));
    }
}
