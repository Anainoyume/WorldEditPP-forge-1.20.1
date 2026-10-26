package com.redawn.worldeditpp.bridge.forge.worldedit;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Translates WorldEdit's annotation-based command descriptions. The built-in WorldEdit
 * translations cover messages, but command descriptions are plain English TextComponents.
 * Piston wraps help text in a TranslatableComponent, so both children and translation
 * arguments have to be visited.
 */
public final class ChineseHelp {
    private static final Type STRING_MAP = new TypeToken<Map<String, String>>() { }.getType();
    private static final Map<String, String> TEXT = load();

    public static Component translate(Component component, Locale locale) {
        if (locale == null || !"zh".equalsIgnoreCase(locale.getLanguage())) return component;
        return translateNode(component);
    }

    private static Component translateNode(Component component) {
        List<Component> children = component.children();
        List<Component> translatedChildren = children.stream().map(ChineseHelp::translateNode).toList();
        boolean childrenChanged = !children.equals(translatedChildren);

        Component result = component;
        if (component instanceof TranslatableComponent translatable) {
            List<Component> args = translatable.args();
            List<Component> translatedArgs = args.stream().map(ChineseHelp::translateNode).toList();
            if (!args.equals(translatedArgs)) result = translatable.args(translatedArgs);
        }

        if (!(component instanceof TextComponent text)) {
            return childrenChanged ? result.children(translatedChildren) : result;
        }

        String replacement = translateText(text.content());
        if (replacement == null || replacement.equals(text.content())) {
            return childrenChanged ? result.children(translatedChildren) : result;
        }
        TextComponent translated = TextComponent.of(replacement, text.style());
        return translatedChildren.isEmpty() ? translated : translated.children(translatedChildren);
    }

    private static String translateText(String text) {
        String label = switch (text) {
            case "\nUsage: ", "Usage: " -> "\n用法：";
            case "Arguments:\n", "Arguments:" -> "参数：\n";
            case "Flags:\n", "Flags:" -> "开关：\n";
            case " (defaults to " -> "（默认为 ";
            default -> null;
        };
        if (label != null) return label;
        String exact = TEXT.get(text);
        if (exact != null) return exact;
        if (text.startsWith("Help for ")) return "帮助：" + text.substring("Help for ".length());
        if (text.startsWith("Subcommands: ")) return "子命令：" + text.substring("Subcommands: ".length());
        return null;
    }

    private static Map<String, String> load() {
        try (InputStream input = ChineseHelp.class.getResourceAsStream(
                "/assets/worldeditpp/lang/worldedit_help_zh_cn.json")) {
            if (input == null) return Map.of();
            try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                return Map.copyOf(new Gson().fromJson(reader, STRING_MAP));
            }
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private ChineseHelp() { }
}
