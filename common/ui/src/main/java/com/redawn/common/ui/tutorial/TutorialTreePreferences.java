package com.redawn.common.ui.tutorial;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Properties;

/** Client-local folder, favorite and visit preferences owned by one tutorial host. */
public final class TutorialTreePreferences {
    private final Path path;
    private final List<Path> legacyPaths;
    private final Properties values = new Properties();
    public TutorialTreePreferences(Path path) { this(path, List.of()); }
    public TutorialTreePreferences(Path path, List<Path> legacyPaths) {
        this.path = path;
        this.legacyPaths = List.copyOf(legacyPaths);
    }
    public void load() throws IOException {
        Path source = Files.isRegularFile(path) ? path : legacyPaths.stream()
                .filter(Files::isRegularFile).findFirst().orElse(null);
        if (source != null) try (var reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
            values.load(reader);
        }
    }
    public boolean expanded(String language, String folder, boolean fallback) {
        String saved = values.getProperty(language + "/" + folder);
        return "true".equals(saved) || (!"false".equals(saved) && fallback);
    }
    public void setExpanded(String language, String folder, boolean expanded) {
        values.setProperty(language + "/" + folder, Boolean.toString(expanded));
    }
    public List<String> favorites(String language) { return ordered("favorites", language, 4096); }
    public List<String> recent(String language) { return ordered("recent", language, TutorialPageHistory.MAX_RECENT); }
    public void setFavorites(String language, List<String> pages) { setOrdered("favorites", language, pages); }
    public void setRecent(String language, List<String> pages) { setOrdered("recent", language, pages); }
    private List<String> ordered(String kind, String language, int maximum) {
        String prefix = kind + "." + language + ".";
        int count;
        try { count = Math.min(maximum, Math.max(0, Integer.parseInt(values.getProperty(prefix + "count", "0")))); }
        catch (NumberFormatException ignored) { return List.of(); }
        var result = new java.util.ArrayList<String>(count);
        for (int i = 0; i < count; i++) {
            String value = values.getProperty(prefix + i);
            if (value != null && !value.isBlank() && !result.contains(value)) result.add(value);
        }
        return List.copyOf(result);
    }
    private void setOrdered(String kind, String language, List<String> pages) {
        String prefix = kind + "." + language + ".";
        for (String key : List.copyOf(values.stringPropertyNames())) if (key.startsWith(prefix)) values.remove(key);
        for (int i = 0; i < pages.size(); i++) values.setProperty(prefix + i, pages.get(i));
        values.setProperty(prefix + "count", Integer.toString(pages.size()));
    }
    public void save() throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            values.store(writer, "Tutorial folders, favorites and recent pages by language");
        }
        try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
