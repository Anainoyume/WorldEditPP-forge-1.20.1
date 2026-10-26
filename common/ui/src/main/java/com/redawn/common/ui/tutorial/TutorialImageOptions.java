package com.redawn.common.ui.tutorial;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Image metadata independent of the resource and font backends. */
public record TutorialImageOptions(Map<String, TutorialSize> sizes, String title) {
    private static final Pattern KEY = Pattern.compile("([a-zA-Z_]+)(?:\\s*[:=]\\s*|\\s+)");
    private static final Pattern NEXT = Pattern.compile("\\s+(?=[a-zA-Z_]+\\s*[:=]|(?:width|height|title)\\s+)");

    public TutorialImageOptions { sizes = Map.copyOf(sizes); }

    public static TutorialImageOptions parse(String input) {
        Map<String, TutorialSize> sizes = new LinkedHashMap<>();
        String title = "";
        int cursor = 0;
        while (cursor < input.length()) {
            while (cursor < input.length() && Character.isWhitespace(input.charAt(cursor))) cursor++;
            if (cursor == input.length()) break;
            var key = KEY.matcher(input).region(cursor, input.length());
            if (!key.lookingAt()) throw new IllegalArgumentException("Invalid image options: " + input.substring(cursor));
            String name = key.group(1);
            if (!name.equals("width") && !name.equals("height") && !name.equals("title"))
                throw new IllegalArgumentException("Unknown image option: " + name);
            cursor = key.end();
            String value;
            if (cursor < input.length() && (input.charAt(cursor) == '\'' || input.charAt(cursor) == '"')) {
                char quote = input.charAt(cursor++);
                int end = input.indexOf(quote, cursor);
                if (end < 0) throw new IllegalArgumentException("Unclosed image option: " + name);
                value = input.substring(cursor, end);
                cursor = end + 1;
                if (cursor < input.length() && !Character.isWhitespace(input.charAt(cursor)))
                    throw new IllegalArgumentException("Image options must be separated by spaces");
            } else if (name.equals("title")) {
                var next = NEXT.matcher(input);
                int end = next.find(cursor) ? next.start() : input.length();
                value = input.substring(cursor, end).strip();
                cursor = end;
            } else {
                int start = cursor;
                while (cursor < input.length() && !Character.isWhitespace(input.charAt(cursor))) cursor++;
                value = input.substring(start, cursor);
            }
            if (name.equals("title")) title = value;
            else sizes.put(name, TutorialSize.parse(value));
        }
        return new TutorialImageOptions(sizes, title);
    }
}
