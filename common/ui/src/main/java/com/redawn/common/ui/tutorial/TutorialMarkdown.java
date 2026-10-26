package com.redawn.common.ui.tutorial;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** The guide dialect's syntax tree and parser. No Minecraft classes or JSON text components. */
public final class TutorialMarkdown {
    private static final int MAX_DEPTH = 32;
    private static final Pattern META = Pattern.compile("^@([a-zA-Z_][a-zA-Z0-9_]*)[ \\t]+(.*)$");
    private static final Map<String, Integer> COLORS = Map.ofEntries(
            Map.entry("black", 0x000000), Map.entry("dark_blue", 0x0000AA),
            Map.entry("dark_green", 0x00AA00), Map.entry("dark_aqua", 0x00AAAA),
            Map.entry("dark_red", 0xAA0000), Map.entry("dark_purple", 0xAA00AA),
            Map.entry("gold", 0xFFAA00), Map.entry("gray", 0xAAAAAA),
            Map.entry("dark_gray", 0x555555), Map.entry("blue", 0x5555FF),
            Map.entry("green", 0x55FF55), Map.entry("aqua", 0x55FFFF),
            Map.entry("red", 0xFF5555), Map.entry("light_purple", 0xFF55FF),
            Map.entry("yellow", 0xFFFF55), Map.entry("white", 0xFFFFFF), Map.entry("theme", 0xFFF0A0));

    public record Format(boolean bold, boolean italic, boolean strike, boolean underline, boolean code, Integer color) {
        public static final Format PLAIN = new Format(false, false, false, false, false, null);
        Format marked(String marker) {
            return new Format(bold || marker.equals("**"), italic || marker.equals("__"),
                    strike || marker.equals("~~"), underline || marker.equals("--"), code, color);
        }
        Format colored(int value) { return new Format(bold, italic, strike, underline, code, value); }
        Format asCode() { return new Format(bold, italic, strike, underline, true, color); }
    }
    public record Target(String path, List<Span> tip) {
        public Target { tip = List.copyOf(tip); }
    }
    public record Span(String text, Format format, Target target, boolean keybind) { }
    public sealed interface Block permits Paragraph, Heading, Quote, ImageRow, Image { int line(); }
    public record Paragraph(int line, List<Span> spans) implements Block { }
    public record Heading(int line, int level, List<Span> spans) implements Block { }
    public record Quote(int line, List<Span> spans) implements Block { }
    public record ImageRow(int line, String options, List<String> body, boolean closed) implements Block {
        public ImageRow { body = List.copyOf(body); }
    }
    public record Image(int line, String path, String options, String alt) implements Block { }
    public record Document(Map<String, String> metadata, List<Block> blocks) { }
    private record Header(Map<String, String> values, String[] lines, int bodyStart) { }
    private record Destination(String path, String option, int end) { }

    /** Indexes deliberately ignore everything except the three permitted header fields. */
    public static Map<String, String> index(String source) {
        var header = header(source);
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : List.of("title", "icon", "expanded")) {
            if (header.values.containsKey(key)) result.put(key, header.values.get(key));
        }
        return Map.copyOf(result);
    }

    private static Header header(String source) {
        if (source.length() > 1_048_576) throw new IllegalArgumentException("Tutorial exceeds 1 MiB of text");
        String[] lines = source.replace("\r\n", "\n").replace('\r', '\n').replaceFirst("^\uFEFF", "").split("\n", -1);
        Map<String, String> metadata = new LinkedHashMap<>();
        int cursor = 0;
        while (cursor < lines.length) {
            if (lines[cursor].isBlank()) { cursor++; continue; }
            var match = META.matcher(lines[cursor]);
            if (!match.matches()) break;
            metadata.put(match.group(1).toLowerCase(Locale.ROOT), match.group(2).strip());
            cursor++;
        }
        return new Header(Map.copyOf(metadata), lines, cursor);
    }

    public static Document parse(String source) {
        var header = header(source);
        List<Block> blocks = new ArrayList<>();
        String[] lines = header.lines;
        int i = header.bodyStart;
        while (i < lines.length) {
            if (lines[i].isBlank()) { i++; continue; }
            String trimmed = lines[i].strip();
            if (imageRowStart(trimmed)) {
                int first = i++;
                List<String> body = new ArrayList<>();
                while (i < lines.length && !lines[i].strip().equals(":::")) body.add(lines[i++]);
                boolean closed = i < lines.length;
                if (closed) i++;
                blocks.add(new ImageRow(first + 1, trimmed.substring(8).strip(), body, closed));
                continue;
            }
            if (trimmed.startsWith(">")) {
                blocks.add(new Quote(i + 1, inline(trimmed.substring(1).stripLeading())));
                i++;
                continue;
            }
            Heading heading = heading(trimmed, i + 1);
            if (heading != null) { blocks.add(heading); i++; continue; }
            Image image = image(trimmed, i + 1);
            if (image != null) { blocks.add(image); i++; continue; }
            int first = i;
            StringBuilder text = new StringBuilder(lines[i++]);
            while (i < lines.length && !lines[i].isBlank()
                    && !imageRowStart(lines[i].strip()) && !lines[i].strip().startsWith(">")
                    && heading(lines[i].strip(), i + 1) == null
                    && image(lines[i].strip(), i + 1) == null) text.append('\n').append(lines[i++]);
            blocks.add(new Paragraph(first + 1, inline(text.toString())));
        }
        return new Document(header.values, List.copyOf(blocks));
    }

    private static boolean imageRowStart(String text) {
        return text.equals(":::image") || text.startsWith(":::image ") || text.startsWith(":::image\t");
    }
    static Image image(String text, int line) {
        if (!text.startsWith("![")) return null;
        int close = bracketEnd(text, 1);
        if (close < 0) return null;
        Destination destination = destination(text, close + 1);
        return destination != null && text.substring(destination.end).isBlank()
                ? new Image(line, destination.path, destination.option, unescape(text.substring(2, close))) : null;
    }
    private static Heading heading(String text, int line) {
        int level = 0;
        while (level < text.length() && text.charAt(level) == '#') level++;
        if (level < 1 || level > 6 || level >= text.length() || !Character.isWhitespace(text.charAt(level))) return null;
        String value = text.substring(level).strip();
        return value.isEmpty() ? null : new Heading(line, level, inline(value));
    }

    public static List<Span> inline(String text) {
        return inline(text, Format.PLAIN, null, true, 0);
    }
    private static List<Span> inline(String text, Format format, Target target, boolean allowLinks, int depth) {
        if (depth > MAX_DEPTH) return List.of(new Span(text, format, target, false));
        List<Span> out = new ArrayList<>();
        StringBuilder plain = new StringBuilder();
        for (int i = 0; i < text.length();) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length() && escapable(text.charAt(i + 1))) {
                plain.append(text.charAt(i + 1)); i += 2; continue;
            }
            if (c == '`') {
                String marker = text.startsWith("``", i) ? "``" : "`";
                int close = codeEnd(text, i + marker.length(), marker);
                if (close >= 0) {
                    flush(out, plain, format, target);
                    out.add(new Span(text.substring(i + marker.length(), close), format.asCode(), target, false));
                    i = close + marker.length(); continue;
                }
            }
            if (c == '[') {
                int close = bracketEnd(text, i);
                if (close >= 0) {
                    String inner = text.substring(i + 1, close);
                    Destination dest = allowLinks ? destination(text, close + 1) : null;
                    if (dest != null) {
                        flush(out, plain, format, target);
                        var tip = inline(dest.option, Format.PLAIN, null, false, depth + 1);
                        out.addAll(inline(inner, format.marked("--"), new Target(dest.path, tip), false, depth + 1));
                        i = dest.end; continue;
                    }
                    int colon = inner.indexOf(':');
                    Integer color = colon < 0 ? null : color(inner.substring(0, colon));
                    if (color != null) {
                        flush(out, plain, format, target);
                        out.addAll(inline(inner.substring(colon + 1), format.colored(color), target, allowLinks, depth + 1));
                        i = close + 1; continue;
                    }
                    if (inner.startsWith("key:") && inner.substring(4).matches("[a-zA-Z0-9_.:-]+")) {
                        flush(out, plain, format, target);
                        out.add(new Span(inner.substring(4), format, target, true));
                        i = close + 1; continue;
                    }
                }
            }
            String marker = markerAt(text, i);
            if (marker != null && canOpen(text, i, marker)) {
                int close = delimiterEnd(text, i + marker.length(), marker);
                if (close >= 0) {
                    flush(out, plain, format, target);
                    out.addAll(inline(text.substring(i + marker.length(), close), format.marked(marker), target, allowLinks, depth + 1));
                    i = close + marker.length(); continue;
                }
            }
            plain.append(c); i++;
        }
        flush(out, plain, format, target);
        return List.copyOf(out);
    }

    private static void flush(List<Span> out, StringBuilder plain, Format format, Target target) {
        if (!plain.isEmpty()) { out.add(new Span(plain.toString(), format, target, false)); plain.setLength(0); }
    }
    private static Integer color(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        if (normalized.matches("#[0-9a-f]{6}")) return Integer.parseInt(normalized.substring(1), 16);
        return COLORS.get(normalized);
    }
    private static String markerAt(String text, int i) {
        for (String marker : List.of("**", "__", "~~", "--")) {
            if (text.startsWith(marker, i)) return marker;
        }
        return null;
    }
    private static boolean canOpen(String text, int i, String marker) {
        return i + marker.length() < text.length();
    }
    private static int delimiterEnd(String text, int start, String marker) {
        for (int i = start; i < text.length();) {
            if (text.charAt(i) == '\\') { i += 2; continue; }
            if (text.charAt(i) == '`') {
                String code = text.startsWith("``", i) ? "``" : "`";
                int close = codeEnd(text, i + code.length(), code);
                if (close >= 0) { i = close + code.length(); continue; }
            }
            if (text.charAt(i) == '[') {
                int close = bracketEnd(text, i);
                if (close >= 0) {
                    Destination dest = destination(text, close + 1);
                    i = dest == null ? close + 1 : dest.end; continue;
                }
            }
            if (text.startsWith(marker, i) && i > start) {
                return i;
            }
            i++;
        }
        return -1;
    }
    private static int codeEnd(String text, int start, String marker) {
        for (int i = start; i < text.length(); i++) {
            if (text.startsWith(marker, i) && (i == 0 || text.charAt(i - 1) != '`')
                    && (i + marker.length() == text.length() || text.charAt(i + marker.length()) != '`')) return i;
        }
        return -1;
    }
    private static int bracketEnd(String text, int start) {
        int nesting = 1;
        for (int i = start + 1; i < text.length(); i++) {
            if (text.charAt(i) == '\\') { i++; continue; }
            if (text.charAt(i) == '`') {
                String marker = text.startsWith("``", i) ? "``" : "`";
                int close = codeEnd(text, i + marker.length(), marker);
                if (close >= 0) { i = close + marker.length() - 1; continue; }
            }
            if (text.charAt(i) == '[') nesting++;
            if (text.charAt(i) == ']' && --nesting == 0) return i;
        }
        return -1;
    }

    private static Destination destination(String text, int start) {
        if (start >= text.length() || text.charAt(start) != '(') return null;
        int i = start + 1;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
        StringBuilder path = new StringBuilder();
        int parens = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length() && escapable(text.charAt(i + 1))) { path.append(text.charAt(i + 1)); i += 2; continue; }
            if (c == '(') parens++;
            if (c == ')') { if (parens == 0) break; parens--; }
            if (Character.isWhitespace(c) && parens == 0) break;
            path.append(c); i++;
        }
        if (path.isEmpty()) return null;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
        String option = "";
        if (i < text.length() && text.charAt(i) == '"') {
            i++;
            StringBuilder value = new StringBuilder();
            boolean closed = false;
            while (i < text.length()) {
                char c = text.charAt(i++);
                if (c == '\\' && i < text.length()) {
                    char next = text.charAt(i++);
                    if (next == '"') value.append(next);
                    else if (next == 'n') value.append('\n');
                    else value.append('\\').append(next);
                } else if (c == '"') { closed = true; break; }
                else value.append(c);
            }
            if (!closed) return null;
            option = value.toString();
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) i++;
        }
        return i < text.length() && text.charAt(i) == ')' ? new Destination(path.toString(), option, i + 1) : null;
    }
    private static boolean escapable(char c) { return "\\*_~-`[]()!\"".indexOf(c) >= 0; }
    private static String unescape(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '\\' && i + 1 < value.length() && escapable(value.charAt(i + 1))) i++;
            result.append(value.charAt(i));
        }
        return result.toString();
    }
    private TutorialMarkdown() { }
}
