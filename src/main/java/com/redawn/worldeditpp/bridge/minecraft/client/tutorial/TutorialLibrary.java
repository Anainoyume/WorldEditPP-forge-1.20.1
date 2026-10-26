package com.redawn.worldeditpp.bridge.minecraft.client.tutorial;

import com.redawn.common.ui.tutorial.TutorialMarkdown;
import com.redawn.common.ui.tutorial.TutorialStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** Resource-pack-aware discovery. A bad page cannot hide other tutorials. No filesystem assumptions. */
public record TutorialLibrary(List<Page> pages, Map<String, Group> groups, String language, List<Failure> failures,
                              TutorialImageResources images) implements AutoCloseable {
    public TutorialLibrary(List<Page> pages, Map<String, Group> groups, String language, List<Failure> failures) {
        this(pages, groups, language, failures, null);
    }
    public static final String ROOT = "__tutorial/";
    public static final String INDEX = "__index.md";
    public record Group(Component title, ResourceLocation icon, boolean expanded) { }
    public record Failure(ResourceLocation resource, int line, String reason) {
        public String path() { return resource + (line <= 0 ? "" : "#L" + line); }
    }
    public int errors() { return failures.size(); }
    /** All folders, including descendants of collapsed parents, are needed to restore saved state. */
    public Set<String> folders() {
        Set<String> result = new TreeSet<>(groups.keySet());
        for (Page page : pages) {
            String path = page.path();
            while (path.contains("/")) { path = path.substring(0, path.lastIndexOf('/')); result.add(path); }
        }
        for (String folder : List.copyOf(result)) {
            while (folder.contains("/")) { folder = folder.substring(0, folder.lastIndexOf('/')); result.add(folder); }
        }
        result.remove("");
        return Set.copyOf(result);
    }
    public record Page(ResourceLocation id, Component title, Component subtitle, List<TutorialContent> content) {
        public String path() {
            String localized = id.getPath().substring(ROOT.length());
            return localized.substring(localized.indexOf('/') + 1, localized.length() - 3);
        }
        public String searchable() {
            return (title.getString() + " " + subtitle.getString() + " " + path() + " "
                    + String.join(" ", content.stream().map(TutorialContent::searchText).toList())).toLowerCase(Locale.ROOT);
        }
    }
    /** A missing locale falls back as a whole book, without mixing languages page by page. */
    public static String selectLanguage(Set<ResourceLocation> resources, String requested, String namespace) {
        for (String locale : List.of(requested.toLowerCase(Locale.ROOT), "en_us", "zh_cn")) {
            String prefix = ROOT + locale + "/";
            if (resources.stream().anyMatch(id -> id.getNamespace().equals(namespace)
                    && id.getPath().startsWith(prefix) && id.getPath().endsWith(".md")
                    && !id.getPath().endsWith("/" + INDEX))) return locale;
        }
        return "en_us";
    }
    public static TutorialLibrary load(TutorialHost host, ResourceManager resources, String requestedLanguage) {
        List<Page> pages = new ArrayList<>();
        Map<String, Group> groups = new HashMap<>();
        List<Failure> failures = new ArrayList<>();
        var images = new TutorialImageResources(host.resourceNamespace(), resources, failure -> {
            failures.add(failure);
            host.logger().warn("Could not load tutorial image in {}: {}", failure.path(), failure.reason());
        });
        var entries = resources.listResources("__tutorial", id -> id.getNamespace().equals(host.resourceNamespace())
                && id.getPath().endsWith(".md"));
        String language = selectLanguage(entries.keySet(), requestedLanguage, host.resourceNamespace());
        String prefix = ROOT + language + "/";
        for (var entry : entries.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
            if (!entry.getKey().getPath().startsWith(prefix)) continue;
            List<TutorialContent> content = new ArrayList<>();
            try (var reader = entry.getValue().openAsReader()) {
                String source = readSource(reader);
                String relative = entry.getKey().getPath().substring(prefix.length());
                if (relative.equals(INDEX) || relative.endsWith("/" + INDEX)) {
                    String folder = relative.equals(INDEX) ? "" : relative.substring(0, relative.length() - INDEX.length() - 1);
                    var meta = TutorialMarkdown.index(source);
                    Component title = meta.containsKey("title") ? TutorialText.parse(meta.get("title"), entry.getKey()).component()
                            : fallbackGroupTitle(folder);
                    ResourceLocation icon = meta.containsKey("icon") ? new ResourceLocation(meta.get("icon")) : null;
                    String expandedValue = meta.getOrDefault("expanded", Boolean.toString(!folder.contains("/")));
                    if (!expandedValue.equals("true") && !expandedValue.equals("false"))
                        throw new IllegalArgumentException("@expanded requires true or false");
                    boolean expanded = Boolean.parseBoolean(expandedValue);
                    groups.put(folder, new Group(title, icon, expanded));
                    continue;
                }
                var context = new TutorialComponents.Context(entry.getKey(), resources, images);
                var document = TutorialMarkdown.parse(source);
                Component title = TutorialText.parse(document.metadata().getOrDefault("title",
                        relative.substring(0, relative.length() - 3)), entry.getKey()).component();
                Component subtitle = TutorialText.parse(document.metadata().getOrDefault("subtitle", ""), entry.getKey()).component();
                for (var block : document.blocks()) {
                    try { content.add(TutorialComponents.decode(block, context)); }
                    catch (RuntimeException exception) {
                        failures.add(new Failure(entry.getKey(), block.line(), exception.toString()));
                        host.logger().warn("Invalid component in tutorial {}", entry.getKey(), exception);
                        content.add(TutorialText.literal(Component.translatable(host.translationKey("invalid_component"))
                                .withStyle(style -> style.withColor(TutorialStyle.THEME))));
                    }
                }
                pages.add(new Page(entry.getKey(), title, subtitle, List.copyOf(content)));
            } catch (Exception exception) {
                content.forEach(TutorialContent::close);
                failures.add(new Failure(entry.getKey(), -1, exception.toString()));
                host.logger().warn("Could not load tutorial {}", entry.getKey(), exception);
            }
        }
        return new TutorialLibrary(List.copyOf(pages), Map.copyOf(groups), language,
                java.util.Collections.unmodifiableList(failures), images);
    }
    public record Row(String path, int depth, Page page) { public boolean group() { return page == null; } }
    private static String readSource(java.io.Reader reader) throws java.io.IOException {
        StringBuilder text = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            if (text.length() + count > 1_048_576) throw new java.io.IOException("Tutorial exceeds 1 MiB of text");
            text.append(buffer, 0, count);
        }
        return text.toString();
    }
    public Page resolvePage(ResourceLocation source, String target) {
        String path = target.strip();
        if (path.isEmpty() || path.contains("..") || path.indexOf('\\') >= 0) return null;
        if (!path.endsWith(".md")) path += ".md";
        ResourceLocation id = path.contains(":") ? ResourceLocation.tryParse(path)
                : ResourceLocation.tryBuild(source.getNamespace(), path.startsWith(ROOT) ? path : ROOT + language + "/" + path);
        return id == null ? null : pages.stream().filter(page -> page.id().equals(id)).findFirst().orElse(null);
    }
    public List<Row> rows(String query, Set<String> expanded) {
        String[] terms = query.toLowerCase(Locale.ROOT).strip().split("\\s+");
        var found = pages.stream().filter(page -> {
            String searchable = page.searchable();
            String ancestor = page.path();
            while (ancestor.contains("/")) {
                ancestor = ancestor.substring(0, ancestor.lastIndexOf('/'));
                searchable += " " + groupTitle(ancestor).getString().toLowerCase(Locale.ROOT);
            }
            for (String term : terms) if (!searchable.contains(term)) return false;
            return true;
        }).toList();
        Set<String> paths = new TreeSet<>();
        if (query.isBlank()) for (String path : groups.keySet()) {
            if (path.isEmpty()) continue;
            paths.add(path + "/");
            while (path.contains("/")) { path = path.substring(0, path.lastIndexOf('/')); paths.add(path + "/"); }
        }
        for (Page page : found) {
            paths.add(page.path() + ".md");
            String path = page.path();
            while (path.contains("/")) { path = path.substring(0, path.lastIndexOf('/')); paths.add(path + "/"); }
        }
        List<Row> result = new ArrayList<>();
        for (String raw : paths) {
            boolean group = raw.endsWith("/");
            String path = raw.substring(0, raw.length() - (group ? 1 : 3));
            String[] parts = path.split("/");
            String ancestor = "";
            boolean visible = true;
            for (int i = 0; i < parts.length - 1; i++) {
                ancestor += (i == 0 ? "" : "/") + parts[i];
                if (query.isBlank() && !expanded.contains(ancestor)) { visible = false; break; }
            }
            if (visible) result.add(new Row(path, parts.length - 1,
                    group ? null : found.stream().filter(page -> page.path().equals(path)).findFirst().orElseThrow()));
        }
        return List.copyOf(result);
    }
    public Component groupTitle(String path) {
        return groups.containsKey(path) ? groups.get(path).title() : fallbackGroupTitle(path);
    }
    public boolean initiallyExpanded(String path) {
        return groups.containsKey(path) ? groups.get(path).expanded() : !path.contains("/");
    }
    private static Component fallbackGroupTitle(String path) {
        String leaf = path.substring(path.lastIndexOf('/') + 1).replaceFirst("^\\d+[_-]", "").replace('_', ' ');
        return Component.literal(leaf);
    }
    @Override public void close() {
        if (images != null) images.close();
        pages.forEach(page -> page.content().forEach(TutorialContent::close));
    }
}
