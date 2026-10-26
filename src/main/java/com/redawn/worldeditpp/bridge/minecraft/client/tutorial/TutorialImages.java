package com.redawn.worldeditpp.bridge.minecraft.client.tutorial;

import com.mojang.blaze3d.systems.RenderSystem;
import com.redawn.common.ui.tutorial.TutorialImageOptions;
import com.redawn.common.ui.tutorial.TutorialImageRow;
import com.redawn.common.ui.tutorial.TutorialMarkdown;
import com.redawn.common.ui.tutorial.TutorialSize;
import com.redawn.common.ui.tutorial.TutorialStyle;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/** Native texture loading and caption rendering shared by standalone images and image rows. */
final class TutorialImages {
    private static final int CAPTION_GAP = 4;
    private static final int CONNECTOR_SCALE = 4;

    static TutorialContent image(TutorialMarkdown.Image image, ResourceLocation page, TutorialImageResources resources) {
        return new Image(image, page, resources);
    }
    static TutorialContent row(TutorialMarkdown.ImageRow row, ResourceLocation page, TutorialImageResources resources) {
        return new Row(TutorialImageRow.parse(row), page, resources);
    }

    private static final class Image implements TutorialContent {
        private final ResourceLocation path;
        private final TutorialSize width, height;
        private final TutorialImageResources resources;
        private final String alt, title;
        private TutorialSize.Dimensions measuredImage = new TutorialSize.Dimensions(1, 1);
        Image(TutorialMarkdown.Image image, ResourceLocation page, TutorialImageResources resources) {
            this.resources = resources;
            path = image.path().contains(":") ? new ResourceLocation(image.path())
                    : new ResourceLocation(page.getNamespace(), image.path());
            var options = TutorialImageOptions.parse(image.options());
            width = options.sizes().getOrDefault("width", TutorialSize.AUTO);
            height = options.sizes().getOrDefault("height", TutorialSize.AUTO);
            alt = image.alt();
            title = options.title();
            resources.reference(path, page, image.line());
        }
        @Override public void prepareImages(int priority) { resources.cache.request(path, priority); }
        private TutorialSize.Dimensions textureSize() { return resources.cache.dimensions(path); }
        private List<FormattedCharSequence> caption(int width) {
            return title.isEmpty() ? List.of() : Minecraft.getInstance().font.split(Component.literal(title), Math.max(1, width));
        }
        private int captionHeight(int width) {
            return title.isEmpty() ? 0 : CAPTION_GAP + caption(width).size() * TutorialText.LINE_HEIGHT;
        }
        @Override public String searchText() { return alt + " " + title; }
        @Override public TutorialSize.Dimensions measure(int available, int viewport) {
            var texture = textureSize();
            measuredImage = TutorialSize.image(width, height, available, viewport, texture.width(), texture.height());
            return new TutorialSize.Dimensions(available, measuredImage.height() + captionHeight(available));
        }
        @Override public int height(int available, int viewport) { return measure(available, viewport).height(); }
        @Override public void render(GuiGraphics g, int x, int y, int width, int height, int mx, int my, float tick) {
            draw(g, x + (width - measuredImage.width()) / 2, y,
                    measuredImage.width(), measuredImage.height(), width);
        }
        private void draw(GuiGraphics g, int x, int y, int width, int imageHeight, int captionWidth) {
            var font = Minecraft.getInstance().font;
            var texture = resources.cache.texture(path);
            if (texture == null) {
                g.fill(x, y, x + width, y + imageHeight, 0x0CFFFFFF);
                g.renderOutline(x, y, width, imageHeight, TutorialStyle.BORDER);
                String label = resources.cache.failure(path) == null ? "..." : alt.isBlank() ? "!" : alt;
                label = font.plainSubstrByWidth(label, Math.max(1, width - 8));
                if (imageHeight >= font.lineHeight + 4)
                    g.drawString(font, label, x + (width - font.width(label)) / 2,
                            y + (imageHeight - font.lineHeight) / 2, TutorialStyle.MUTED, false);
            } else {
                var size = textureSize();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                g.blit(texture, x, y, width, imageHeight, 0, 0, size.width(), size.height(), size.width(), size.height());
                RenderSystem.disableBlend();
            }
            int captionY = y + imageHeight + CAPTION_GAP;
            for (var line : caption(captionWidth)) {
                g.drawString(font, line, x + (width - font.width(line)) / 2, captionY, TutorialStyle.TEXT, false);
                captionY += TutorialText.LINE_HEIGHT;
            }
        }
    }

    private static final class Row implements TutorialContent {
        private final TutorialImageRow row;
        private final List<Image> images;
        Row(TutorialImageRow row, ResourceLocation page, TutorialImageResources resources) {
            this.row = row;
            images = row.images().stream().map(image -> new Image(image, page, resources)).toList();
        }
        @Override public void prepareImages(int priority) { images.forEach(image -> image.prepareImages(priority)); }
        private List<TutorialSize.Dimensions> textureSizes() { return images.stream().map(Image::textureSize).toList(); }
        private TutorialImageRow.Layout layout(int width) {
            var font = Minecraft.getInstance().font;
            int connectorWidth = row.connectors().stream().mapToInt(font::width).max().orElse(0);
            return row.layout(width, connectorWidth * CONNECTOR_SCALE + 12);
        }
        @Override public int width(int available) { return TutorialImageRow.width(available); }
        private int captionWidth(TutorialImageRow.Layout layout, int width, int index) {
            int centerTwice = 2 * layout.imageX(index) + layout.cellWidth();
            int edgeLimit = Math.min(centerTwice, 2 * width - centerTwice);
            int neighborLimit = layout.count() == 1 ? width
                    : layout.cellWidth() + Math.max(0, layout.gap() - CAPTION_GAP);
            return Math.max(1, Math.min(edgeLimit, neighborLimit));
        }
        @Override public int height(int width, int viewport) {
            var layout = layout(width);
            int cell = layout.cellWidth();
            var heights = row.imageHeights(cell, textureSizes());
            int maximum = 0;
            for (int i = 0; i < images.size(); i++)
                maximum = Math.max(maximum, heights.get(i)
                        + images.get(i).captionHeight(captionWidth(layout, width, i)));
            return maximum;
        }
        @Override public String searchText() {
            return String.join(" ", images.stream().map(Image::searchText).toList());
        }
        @Override public void render(GuiGraphics g, int x, int y, int width, int height, int mx, int my, float tick) {
            var layout = layout(width);
            var heights = row.imageHeights(layout.cellWidth(), textureSizes());
            int imageAreaHeight = heights.stream().mapToInt(Integer::intValue).max().orElse(1);
            var font = Minecraft.getInstance().font;
            g.enableScissor(x, y, x + width, y + height);
            try {
                for (int i = 0; i < images.size(); i++) {
                    images.get(i).draw(g, x + layout.imageX(i), y, layout.cellWidth(), heights.get(i),
                            captionWidth(layout, width, i));
                    if (i < row.connectors().size()) {
                        String connector = row.connectors().get(i);
                        int gapX = x + layout.imageX(i) + layout.cellWidth();
                        g.enableScissor(gapX, y, gapX + layout.gap(), y + imageAreaHeight);
                        g.pose().pushPose();
                        try {
                            g.pose().translate(gapX + (layout.gap() - font.width(connector) * CONNECTOR_SCALE) / 2,
                                    y + (imageAreaHeight - font.lineHeight * CONNECTOR_SCALE) / 2, 0);
                            g.pose().scale(CONNECTOR_SCALE, CONNECTOR_SCALE, 1);
                            g.drawString(font, connector, 0, 0, TutorialStyle.THEME, false);
                        } finally {
                            g.pose().popPose();
                            g.disableScissor();
                        }
                    }
                }
            } finally { g.disableScissor(); }
        }
    }
    private TutorialImages() { }
}
