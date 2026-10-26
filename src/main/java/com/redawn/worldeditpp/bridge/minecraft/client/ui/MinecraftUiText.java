package com.redawn.worldeditpp.bridge.minecraft.client.ui;

import com.redawn.common.ui.UiColor;
import com.redawn.common.ui.UiText;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** Converts the renderer-neutral UiText tree into Minecraft chat components. */
final class MinecraftUiText {
    static Component toComponent(UiText text) {
        if (text instanceof UiText.Literal literal) return Component.literal(literal.text());
        if (text instanceof UiText.Translated translated) {
            return Component.translatable(translated.key(),
                    translated.arguments().stream().map(MinecraftUiText::toComponent).toArray(Component[]::new));
        }
        if (text instanceof UiText.Styled styled) return styled(styled);
        if (text instanceof UiText.Sequence sequence) return sequence(sequence);
        throw new IllegalArgumentException("Unknown UI text node: " + text.getClass().getName());
    }

    private static Component styled(UiText.Styled value) {
        MutableComponent result = toComponent(value.content()).copy();
        Style style = result.getStyle();
        if (value.color() != null) style = style.withColor(value.color().argb());
        if (value.bold()) style = style.withBold(true);
        if (value.italic()) style = style.withItalic(true);
        if (value.underlined()) style = style.withUnderlined(true);
        return result.setStyle(style);
    }

    private static Component sequence(UiText.Sequence value) {
        MutableComponent result = Component.empty();
        for (UiText part : value.parts()) result.append(toComponent(part));
        return result;
    }

    private MinecraftUiText() { }
}
