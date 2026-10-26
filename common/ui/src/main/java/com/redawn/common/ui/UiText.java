package com.redawn.common.ui;

import java.util.List;

/**
 * Renderer-neutral rich text. A bridge converts these nodes to its native text component;
 * the UI layer never imports Minecraft, Adventure or loader text types.
 */
public sealed interface UiText permits UiText.Literal, UiText.Translated, UiText.Styled, UiText.Sequence {
    record Literal(String text) implements UiText {
        public Literal {
            if (text == null) throw new IllegalArgumentException("UI text cannot be null");
        }
    }

    record Translated(String key, List<UiText> arguments) implements UiText {
        public Translated {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("Translation key cannot be blank");
            arguments = List.copyOf(arguments);
        }
    }

    /** {@code color} may be null to inherit the canvas fallback colour. */
    record Styled(UiText content, UiColor color, boolean bold, boolean italic, boolean underlined)
            implements UiText {
        public Styled {
            if (content == null) throw new IllegalArgumentException("Styled UI text cannot be null");
        }
    }

    record Sequence(List<UiText> parts) implements UiText {
        public Sequence {
            parts = List.copyOf(parts);
        }
    }

    static UiText literal(String text) {
        return new Literal(text);
    }

    static UiText translated(String key, UiText... arguments) {
        return new Translated(key, List.of(arguments));
    }

    static UiText styled(UiText content, UiColor color, boolean bold, boolean italic, boolean underlined) {
        return new Styled(content, color, bold, italic, underlined);
    }

    static UiText colored(UiText content, UiColor color) {
        return styled(content, color, false, false, false);
    }

    static UiText sequence(UiText... parts) {
        return new Sequence(List.of(parts));
    }
}
