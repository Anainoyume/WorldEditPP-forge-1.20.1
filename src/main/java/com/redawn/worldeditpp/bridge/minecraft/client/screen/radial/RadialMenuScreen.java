package com.redawn.worldeditpp.bridge.minecraft.client.screen.radial;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.redawn.common.ui.radial.RadialMenuLayout;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Reusable hold-to-select wheel: arbitrary entries, radii and activation predicates. */
public final class RadialMenuScreen<T> extends Screen {
    private static final int HINT_GAP = 12;
    private static final int SCREEN_MARGIN = 8;
    public record Entry<T>(T value, Component label) { }

    private final List<Entry<T>> entries;
    private final double innerRadius, outerRadius;
    private final BooleanSupplier held, available;
    private final Consumer<T> onSelect;
    private final double[] hoverProgress;
    private final int selectedIndex;
    private RadialMenuLayout layout;
    private int hovered = -1;
    private boolean finished;
    private final long openedAt = System.nanoTime();
    private long lastFrame = openedAt;

    public RadialMenuScreen(Component title, List<Entry<T>> entries, T selectedValue, double innerRadius, double outerRadius,
                            BooleanSupplier held, BooleanSupplier available, Consumer<T> onSelect) {
        super(title);
        this.entries = List.copyOf(entries);
        this.selectedIndex = selectedIndex(entries, selectedValue);
        this.innerRadius = innerRadius;
        this.outerRadius = outerRadius;
        this.layout = new RadialMenuLayout(entries.size(), innerRadius, outerRadius, 9);
        this.held = held;
        this.available = available;
        this.onSelect = onSelect;
        this.hoverProgress = new double[entries.size()];
    }

    static <T> int selectedIndex(List<Entry<T>> entries, T selectedValue) {
        for (int i = 0; i < entries.size(); i++) {
            if (Objects.equals(entries.get(i).value(), selectedValue)) return i;
        }
        return -1;
    }

    @Override protected void init() {
        double availableRadius = Math.min(width / 2.0 - SCREEN_MARGIN,
                height / 2.0 - SCREEN_MARGIN - HINT_GAP - font.lineHeight);
        double scale = Math.min(1, Math.max(1, availableRadius) / (outerRadius + 9));
        layout = new RadialMenuLayout(entries.size(), innerRadius * scale, outerRadius * scale, 9 * scale);
        hovered = -1;
        // Screen opening releases the mouse; explicitly start in the neutral center.
        GLFW.glfwSetCursorPos(minecraft.getWindow().getWindow(),
                minecraft.getWindow().getScreenWidth() / 2.0, minecraft.getWindow().getScreenHeight() / 2.0);
    }

    @Override public void renderBackground(GuiGraphics graphics) { }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (finishIfReleased()) return;
        hovered = layout.hit(mouseX - width / 2.0, mouseY - height / 2.0);
        long now = System.nanoTime();
        double elapsed = Math.min(0.1, (now - lastFrame) / 1_000_000_000.0);
        lastFrame = now;
        float opacity = (float) Math.min(1, (now - openedAt) / 120_000_000.0);
        if (opacity < 0.02F) return;
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        for (int i = 0; i < entries.size(); i++) {
            hoverProgress[i] = RadialMenuLayout.animate(hoverProgress[i], i == hovered, elapsed);
            double progress = hoverProgress[i];
            double angle = layout.centerAngle(i);
            float cx = (float) (width / 2.0 + Math.cos(angle) * layout.pushDistance() * progress);
            float cy = (float) (height / 2.0 + Math.sin(angle) * layout.pushDistance() * progress);
            int red = (int) (24 + (255 - 24) * progress);
            int green = (int) (26 + (240 - 26) * progress);
            int blue = (int) (30 + (160 - 30) * progress);
            int alpha = (int) ((180 + 45 * progress) * opacity);
            drawSector(graphics, i, cx, cy, red, green, blue, alpha);
        }
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        for (int i = 0; i < entries.size(); i++) {
            double angle = layout.centerAngle(i);
            double radius = (layout.innerRadius() + layout.outerRadius()) / 2 + layout.pushDistance() * hoverProgress[i];
            int x = (int) Math.round(width / 2.0 + Math.cos(angle) * radius);
            int y = (int) Math.round(height / 2.0 + Math.sin(angle) * radius) - font.lineHeight / 2;
            graphics.drawCenteredString(font, entries.get(i).label(), x, y,
                    labelColor(i, opacity));
        }
        int hintIndex = hovered >= 0 ? hovered : selectedIndex;
        if (hintIndex >= 0) {
            // Reserve the full hover displacement so the hint never moves or touches a pushed sector.
            int hintY = Math.max(SCREEN_MARGIN,
                    (int) Math.floor(height / 2.0 - layout.outerRadius() - layout.pushDistance())
                            - HINT_GAP - font.lineHeight);
            graphics.drawCenteredString(font, entries.get(hintIndex).label(), width / 2,
                    hintY, labelColor(hintIndex, opacity));
        }
    }

    private int labelColor(int index, float opacity) {
        int rgb = index == hovered ? 0xFFFFFF : index == selectedIndex ? 0xFFF0A0 : 0xBBBBBB;
        return ((int) (255 * opacity) << 24) | rgb;
    }

    private void drawSector(GuiGraphics graphics, int index, float cx, float cy, int r, int g, int b, int alpha) {
        int segments = Math.max(2, (int) Math.ceil(layout.step() * layout.outerRadius() / 3));
        BufferBuilder vertices = Tesselator.getInstance().getBuilder();
        vertices.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        var pose = graphics.pose().last().pose();
        for (int j = 0; j < segments; j++) {
            double a = layout.startAngle(index) + layout.step() * j / segments;
            double next = layout.startAngle(index) + layout.step() * (j + 1) / segments;
            vertices.vertex(pose, cx + (float) (Math.cos(a) * layout.innerRadius()), cy + (float) (Math.sin(a) * layout.innerRadius()), 0).color(r, g, b, alpha).endVertex();
            vertices.vertex(pose, cx + (float) (Math.cos(a) * layout.outerRadius()), cy + (float) (Math.sin(a) * layout.outerRadius()), 0).color(r, g, b, alpha).endVertex();
            vertices.vertex(pose, cx + (float) (Math.cos(next) * layout.outerRadius()), cy + (float) (Math.sin(next) * layout.outerRadius()), 0).color(r, g, b, alpha).endVertex();
            vertices.vertex(pose, cx + (float) (Math.cos(next) * layout.innerRadius()), cy + (float) (Math.sin(next) * layout.innerRadius()), 0).color(r, g, b, alpha).endVertex();
        }
        if (entries.size() > 1) {
            double angle = layout.startAngle(index);
            float dx = (float) (-Math.sin(angle) * 0.5), dy = (float) (Math.cos(angle) * 0.5);
            float x1 = cx + (float) (Math.cos(angle) * layout.innerRadius());
            float y1 = cy + (float) (Math.sin(angle) * layout.innerRadius());
            float x2 = cx + (float) (Math.cos(angle) * layout.outerRadius());
            float y2 = cy + (float) (Math.sin(angle) * layout.outerRadius());
            vertices.vertex(pose, x1 - dx, y1 - dy, 0).color(255, 255, 255, alpha / 3).endVertex();
            vertices.vertex(pose, x2 - dx, y2 - dy, 0).color(255, 255, 255, alpha / 3).endVertex();
            vertices.vertex(pose, x2 + dx, y2 + dy, 0).color(255, 255, 255, alpha / 3).endVertex();
            vertices.vertex(pose, x1 + dx, y1 + dy, 0).color(255, 255, 255, alpha / 3).endVertex();
        }
        BufferUploader.drawWithShader(vertices.end());
    }

    private boolean finishIfReleased() {
        if (!available.getAsBoolean()) { finish(false); return true; }
        if (!held.getAsBoolean()) { finish(true); return true; }
        return finished;
    }

    private void finish(boolean confirm) {
        if (finished) return;
        finished = true;
        if (confirm && hovered >= 0) onSelect.accept(entries.get(hovered).value());
        onClose();
    }

    @Override public void tick() { finishIfReleased(); }
    @Override public void mouseMoved(double x, double y) { hovered = layout.hit(x - width / 2.0, y - height / 2.0); }
    @Override public boolean mouseClicked(double x, double y, int button) { return true; }
    @Override public boolean mouseScrolled(double x, double y, double vertical) { return true; }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) finish(false);
        return true;
    }
    @Override public boolean keyReleased(int key, int scan, int modifiers) {
        finishIfReleased();
        // Preserve vanilla release propagation, including shared hotbar keys.
        return false;
    }
    @Override public boolean isPauseScreen() { return false; }
}
