package com.redawn.worldeditpp.bridge.minecraft.client.tutorial;

import com.redawn.common.ui.tutorial.TutorialMarkdown;
import com.redawn.common.ui.tutorial.TutorialStyle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/** Adapts the dialect's styled spans to the native font backend, wrapping and glyph hit testing. */
public final class TutorialText implements TutorialContent {
    public static final int LINE_HEIGHT = 13;
    private static final String CODE_MARKER = "tutorial:inline_code";
    private final Component text;
    private final Map<String, TutorialNavigation.Link> links;
    private final float scale;
    private int cachedWidth = -1;
    private Font cachedFont;
    private List<FormattedCharSequence> cachedLines = List.of();

    private TutorialText(Component text, Map<String, TutorialNavigation.Link> links, float scale) {
        this.text = text;
        this.links = Map.copyOf(links);
        this.scale = scale;
    }
    public static TutorialText literal(Component text) { return new TutorialText(text, Map.of(), 1.0F); }
    public static TutorialText parse(String markdown, ResourceLocation source) {
        return compile(TutorialMarkdown.inline(markdown), source);
    }
    public static TutorialText compile(List<TutorialMarkdown.Span> spans, ResourceLocation source) {
        return compile(spans, source, 1.0F, false);
    }
    public static TutorialText heading(List<TutorialMarkdown.Span> spans, ResourceLocation source, int level) {
        float scale = switch (level) {
            case 1 -> 1.60F;
            case 2 -> 1.40F;
            case 3 -> 1.25F;
            case 4 -> 1.15F;
            case 5 -> 1.08F;
            default -> 1.0F;
        };
        return compile(spans, source, scale, true);
    }
    private static TutorialText compile(List<TutorialMarkdown.Span> spans, ResourceLocation source,
                                        float scale, boolean forceBold) {
        var text = Component.empty();
        Map<String, TutorialNavigation.Link> links = new LinkedHashMap<>();
        Map<TutorialMarkdown.Target, String> tokens = new LinkedHashMap<>();
        for (var span : spans) {
            var f = span.format();
            Style style = Style.EMPTY;
            if (forceBold || f.bold()) style = style.withBold(true);
            if (f.italic()) style = style.withItalic(true);
            if (f.strike()) style = style.withStrikethrough(true);
            if (f.underline()) style = style.withUnderlined(true);
            if (f.color() != null) style = style.withColor(f.color());
            if (f.code()) {
                style = style.withInsertion(CODE_MARKER);
                if (f.color() == null) style = style.withColor(TutorialStyle.THEME & 0xFFFFFF);
            }
            if (span.target() != null) {
                var target = span.target();
                String token = tokens.computeIfAbsent(target, ignored -> "tutorial_link_" + tokens.size());
                links.computeIfAbsent(token, ignored -> new TutorialNavigation.Link(source, target.path(),
                        target.tip().isEmpty() ? null : compile(target.tip(), source)));
                style = style.withClickEvent(new ClickEvent(ClickEvent.Action.CHANGE_PAGE, token));
            }
            text.append((span.keybind() ? Component.keybind(span.text()) : Component.literal(span.text())).setStyle(style));
        }
        return new TutorialText(text, links, scale);
    }
    public Component component() { return text; }
    float textScale() { return scale; }
    public List<FormattedCharSequence> lines(Font font, int width) {
        int available = Math.max(1, (int) Math.floor(width / scale));
        if (font != cachedFont || available != cachedWidth) {
            cachedFont = font; cachedWidth = available;
            cachedLines = font.split(text, available);
        }
        return cachedLines;
    }
    @Override public int height(int width, int viewportHeight) {
        return (int) Math.ceil(lines(Minecraft.getInstance().font, width).size() * LINE_HEIGHT * scale) + 2;
    }
    @Override public void render(GuiGraphics g, int x, int y, int width, int height, int mx, int my, float tick) {
        var font = Minecraft.getInstance().font;
        if (scale == 1.0F) {
            for (var line : lines(font, width)) {
                renderLine(g, font, line, x, y, TutorialStyle.TEXT);
                y += LINE_HEIGHT;
            }
            return;
        }
        g.pose().pushPose();
        try {
            g.pose().translate(x, y, 0);
            g.pose().scale(scale, scale, 1.0F);
            int lineY = 0;
            for (var line : lines(font, width)) {
                renderLine(g, font, line, 0, lineY, TutorialStyle.TEXT);
                lineY += LINE_HEIGHT;
            }
        } finally { g.pose().popPose(); }
    }
    /** Backgrounds follow wrapped glyph runs, including codes that contain several styles. */
    public static void renderLine(GuiGraphics g, Font font, FormattedCharSequence line, int x, int y, int color) {
        float[] cursor = {0};
        List<float[]> codeRanges = new ArrayList<>();
        line.accept((index, style, codepoint) -> {
            float advance = font.getSplitter().stringWidth(FormattedCharSequence.codepoint(codepoint, style));
            if (CODE_MARKER.equals(style.getInsertion())) {
                if (!codeRanges.isEmpty() && Math.abs(codeRanges.get(codeRanges.size() - 1)[1] - cursor[0]) < 0.001F)
                    codeRanges.get(codeRanges.size() - 1)[1] += advance;
                else codeRanges.add(new float[]{cursor[0], cursor[0] + advance});
            }
            cursor[0] += advance;
            return true;
        });
        for (var range : codeRanges) g.fill(x + (int) Math.floor(range[0]), y - 1,
                x + (int) Math.ceil(range[1]), y + font.lineHeight + 1, 0x38FFFFFF);
        g.drawString(font, line, x, y, color, false);
    }
    @Override public String searchText() { return text.getString(); }
    @Override public TutorialNavigation.Link navigationAt(double x, double y, int width) {
        if (links.isEmpty() || x < 0 || x >= width || y < 0) return null;
        var font = Minecraft.getInstance().font;
        var lines = lines(font, width);
        double localX = x / scale;
        double localY = y / scale;
        int row = (int) localY / LINE_HEIGHT;
        if (row >= lines.size() || localY % LINE_HEIGHT >= font.lineHeight || localX >= font.width(lines.get(row))) return null;
        var style = font.getSplitter().componentStyleAtWidth(lines.get(row), (int) localX);
        return style == null || style.getClickEvent() == null ? null : links.get(style.getClickEvent().getValue());
    }
}
