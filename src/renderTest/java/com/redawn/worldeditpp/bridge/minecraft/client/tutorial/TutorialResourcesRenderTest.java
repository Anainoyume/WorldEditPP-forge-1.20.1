package com.redawn.worldeditpp.bridge.minecraft.client.tutorial;

import com.redawn.common.ui.tutorial.TutorialMarkdown;
import com.redawn.common.ui.tutorial.TutorialSize;
import com.redawn.common.ui.tutorial.TutorialImageRow;

import com.redawn.worldeditpp.bridge.minecraft.client.tutorial.TutorialComponents;
import com.redawn.worldeditpp.bridge.minecraft.client.tutorial.TutorialHost;
import com.redawn.worldeditpp.bridge.minecraft.client.tutorial.TutorialLibrary;
import com.redawn.worldeditpp.bridge.minecraft.client.tutorial.TutorialSavedPageList;
import com.redawn.worldeditpp.bridge.minecraft.client.tutorial.TutorialText;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import static org.junit.jupiter.api.Assertions.*;

class TutorialResourcesRenderTest {
    @Test void collapsedTreesAndSearchPreserveParentsAndFindArticleBody() {
        var p1 = page("start/intro", "Introduction", "first steps");
        var p2 = page("tools/states/stairs", "Stairs", "upper HALF mapping");
        var library = new TutorialLibrary(List.of(p1, p2), Map.of("tools/states",
                new TutorialLibrary.Group(Component.literal("Properties"), null, false)), "en_us", List.of());
        assertEquals(List.of("start", "tools"), library.rows("", Set.of()).stream().map(TutorialLibrary.Row::path).toList());
        assertEquals(List.of("tools", "tools/states", "tools/states/stairs"),
                library.rows("half STAIRS", Set.of()).stream().map(TutorialLibrary.Row::path).toList());
        assertTrue(library.rows("missing", Set.of()).isEmpty());
        assertEquals(4, library.rows("", Set.of("start", "tools")).size());
        assertEquals(3, library.rows("properties", Set.of()).size());
        assertFalse(library.initiallyExpanded("tools/states"));
    }
    private TutorialLibrary.Page page(String path, String title, String body) {
        var id = new ResourceLocation("worldeditpp:__tutorial/en_us/" + path + ".md");
        return new TutorialLibrary.Page(id, Component.literal(title), Component.empty(), List.of(TutorialText.parse(body, id)));
    }
    @Test void savedPageRowsKeepNavigationAndRemovalHitTargetsSeparate() {
        var first = page("start/intro", "Introduction", "first steps");
        var second = page("tools/states", "States", "properties");
        var list = new TutorialSavedPageList(List.of(first, second));
        assertEquals(TutorialSavedPageList.ROW_HEIGHT * 2, list.height(200, 100));
        assertEquals(first.id().toString(), list.navigationAt(20, 7, 200).path());
        assertEquals(second.id().toString(), list.navigationAt(20, TutorialSavedPageList.ROW_HEIGHT + 2, 200).path());
        assertNull(list.navigationAt(200 - TutorialSavedPageList.REMOVE_WIDTH, 7, 200));
        assertEquals(first, list.removalAt(199, 7, 200));
        assertEquals(second, list.removalAt(199, TutorialSavedPageList.ROW_HEIGHT + 2, 200));
        assertNull(list.removalAt(20, 7, 200));
        assertNull(list.removalAt(199, TutorialSavedPageList.ROW_HEIGHT * 2, 200));
    }
    @Test void localeFallbackIgnoresOtherNamespacesIndexesAndObsoleteJson() {
        var resources = Set.of(
                new ResourceLocation("worldeditpp:__tutorial/zh_cn/start.md"),
                new ResourceLocation("worldeditpp:__tutorial/en_us/start.json"),
                new ResourceLocation("worldeditpp:__tutorial/de_de/__index.md"),
                new ResourceLocation("other:__tutorial/fr_fr/start.md"));
        for (String locale : List.of("zh_cn", "en_us", "de_de", "fr_fr"))
            assertEquals("zh_cn", TutorialLibrary.selectLanguage(resources, locale, "worldeditpp"));
        assertEquals("fr_fr", TutorialLibrary.selectLanguage(resources, "fr_fr", "other"));
        assertEquals("en_us", TutorialLibrary.selectLanguage(Set.of(
                new ResourceLocation("worldeditpp:__tutorial/en_us/start.md")), "ja_jp", "worldeditpp"));
    }
    @Test void imageDimensionsSupportTheDocumentedUnitsAndRejectInvalidOptions() {
        assertEquals(Map.of("width", TutorialSize.parse("33.3%"), "height", TutorialSize.AUTO),
                TutorialComponents.imageOptions("width=33.3% height=auto"));
        assertEquals(TutorialComponents.imageOptions("width:64px height:auto"), TutorialComponents.imageOptions("width 64 height auto"));
        assertTrue(TutorialComponents.imageOptions("").isEmpty());
        assertTrue(TutorialComponents.imageOptions("title:Figure 1").isEmpty());
        for (String invalid : List.of("unknown=something", "width=-5", "width=NaN", "width=", "width=0"))
            assertThrows(IllegalArgumentException.class, () -> TutorialComponents.imageOptions(invalid), invalid);
    }
    @Test void loaderKeepsHealthyContentAroundBadBlocksAndUsesMarkdownLineNumbers() {
        var data = Map.of(
                "worldeditpp:__tutorial/zh_cn/__index.md", "@title **Book**\n@icon minecraft:stone\n@expanded false\n\nignored",
                "worldeditpp:__tutorial/zh_cn/start.md", "@title Page\n@subtitle Details\n\nBefore\n\n![](missing:textures/absent.png \"width:-1\")\n\nAfter",
                "worldeditpp:__tutorial/zh_cn/old.json", "{}",
                "other:__tutorial/zh_cn/foreign.md", "@title Other\n\nForeign");
        try (var library = TutorialLibrary.load(host(), resources(data), "en_us")) {
            assertEquals("zh_cn", library.language());
            assertEquals(1, library.pages().size());
            var page = library.pages().get(0);
            assertEquals("Page", page.title().getString());
            assertEquals("Details", page.subtitle().getString());
            assertEquals("Before", page.content().get(0).searchText());
            assertEquals("After", page.content().get(page.content().size() - 1).searchText());
            assertEquals("Book", library.groupTitle("").getString());
            assertFalse(library.initiallyExpanded(""));
            assertEquals(1, library.errors());
            assertEquals("worldeditpp:__tutorial/zh_cn/start.md#L6", library.failures().get(0).path());
        }
    }
    @Test void unopenedImagesDoNotLoadAndLateFailuresKeepTheArticleAndSourceLine() throws Exception {
        var data = Map.of(
                "worldeditpp:__tutorial/zh_cn/first.md", "@title First\n\nBefore\n\n![Alt](missing:textures/first.png)\n\nAfter",
                "worldeditpp:__tutorial/zh_cn/second.md", "@title Second\n\n![Alt](missing:textures/second.png)");
        try (var library = TutorialLibrary.load(host(), resources(data), "zh_cn")) {
            assertEquals(0, library.errors(), "Discovery must not read image bytes");
            assertEquals(2, library.pages().size());
            var first = library.pages().get(0);
            assertEquals("Before", first.content().get(0).searchText());
            assertEquals("After", first.content().get(first.content().size() - 1).searchText());
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (library.errors() == 0 && System.nanoTime() < deadline) {
                library.images().cache.beginFrame();
                first.content().forEach(item -> item.prepareImages(0));
                library.images().cache.endFrame();
                Thread.sleep(5);
            }
            assertEquals(1, library.errors());
            assertEquals("worldeditpp:__tutorial/zh_cn/first.md#L5", library.failures().get(0).path());
            assertTrue(library.failures().get(0).reason().contains("missing:textures/first.png"));
            assertEquals(0, library.images().cache.residentBytes());
        }
    }
    @Test void badImageRowOnlyReplacesItsOwnBlockAndQuotesAndHeadingsDecode() {
        var data = Map.of("worldeditpp:__tutorial/zh_cn/start.md",
                "@title Page\n\n> Before\n:::image height:20%\n![](a.png)\n:::\n# After");
        try (var library = TutorialLibrary.load(host(), resources(data), "zh_cn")) {
            assertEquals(1, library.errors());
            assertEquals("worldeditpp:__tutorial/zh_cn/start.md#L4", library.failures().get(0).path());
            var content = library.pages().get(0).content();
            assertEquals(3, content.size());
            assertInstanceOf(TutorialAccent.class, content.get(0));
            assertEquals("Before", content.get(0).searchText());
            assertInstanceOf(TutorialAccent.class, content.get(content.size() - 1));
            assertEquals("After", content.get(content.size() - 1).searchText());
        }
    }
    @Test void bundledBooksContainMatchingPagesAndAllLocalLinksAndImagesAreValid() throws Exception {
        Path assets = Path.of("../src/main/resources/assets/worldeditpp");
        try (var files = Files.walk(assets.resolve("__tutorial"))) {
            assertTrue(files.noneMatch(p -> p.toString().endsWith(".json")), "Old guide JSON must not ship");
        }
        var chinese = validateBook(assets, "zh_cn");
        var english = validateBook(assets, "en_us");
        assertEquals(chinese, english, "English must translate every bundled Chinese article");
    }
    private static Set<String> validateBook(Path assets, String language) throws Exception {
        Path root = assets.resolve("__tutorial/" + language);
        var pages = new java.util.HashSet<String>();
        int images = 0, headings = 0;
        try (var files = Files.walk(root)) {
            for (var file : files.filter(p -> p.toString().endsWith(".md")).toList()) {
                assertTrue(Files.isRegularFile(file.getParent().resolve("__index.md")));
                var source = Files.readString(file);
                if (file.getFileName().toString().equals("__index.md")) {
                    assertNotNull(TutorialMarkdown.index(source).get("title")); continue;
                }
                var doc = TutorialMarkdown.parse(source);
                assertNotNull(doc.metadata().get("title"), file.toString());
                assertFalse(doc.blocks().isEmpty(), file.toString());
                pages.add(root.relativize(file).toString().replace('\\', '/'));
                for (var block : doc.blocks()) {
                    List<TutorialMarkdown.Span> textSpans = block instanceof TutorialMarkdown.Paragraph paragraph
                            ? paragraph.spans() : block instanceof TutorialMarkdown.Heading heading ? heading.spans() : block instanceof TutorialMarkdown.Quote quote ? quote.spans() : List.of();
                    if (block instanceof TutorialMarkdown.Heading) headings++;
                    for (var span : textSpans) {
                        if (span.target() != null) assertTrue(Files.isRegularFile(root.resolve(span.target().path())), span.target().path());
                    }
                    List<TutorialMarkdown.Image> media = block instanceof TutorialMarkdown.Image image ? List.of(image)
                            : block instanceof TutorialMarkdown.ImageRow row ? TutorialImageRow.parse(row).images() : List.of();
                    for (var image : media) {
                        var id = new ResourceLocation(image.path());
                        assertTrue(Files.isRegularFile(assets.getParent().resolve(id.getNamespace()).resolve(id.getPath())));
                        TutorialComponents.imageOptions(image.options()); images++;
                    }
                }
            }
        }
        assertEquals(Set.of(
                "01_start/01_preface.md", "01_start/02_quick_start.md",
                "02_selection/01_selection.md", "02_selection/02_move.md",
                "02_selection/03_stretch.md", "02_selection/04_rotate.md",
                "03_drawing/01_set.md", "03_drawing/02_wheel.md", "03_drawing/03_replace.md",
                "03_drawing/04_states.md", "03_drawing/05_compatible.md",
                "03_drawing/06_editor.md", "03_drawing/07_saved_states.md",
                "04_adjustment/01_begin.md", "04_adjustment/02_move.md",
                "04_adjustment/03_stretch.md", "04_adjustment/04_rotate.md",
                "04_adjustment/05_mirror.md", "04_adjustment/06_clipboard.md"), pages);
        assertEquals("minecraft:magenta_concrete", TutorialMarkdown.index(
                Files.readString(root.resolve("03_drawing/__index.md"))).get("icon"));
        assertEquals("minecraft:bricks", TutorialMarkdown.index(
                Files.readString(root.resolve("04_adjustment/__index.md"))).get("icon"));
        assertTrue(images > 0); assertTrue(headings >= 6);
        return pages;
    }
    private static TutorialHost host() {
        return new TutorialHost(new ResourceLocation("worldeditpp:tutorial"), "worldeditpp", "tutorial.worldeditpp.",
                "worldeditpp/tutorial.properties", List.of(), Component::empty, (k, s) -> false, () -> true,
                LoggerFactory.getLogger(TutorialResourcesRenderTest.class));
    }
    private static ResourceManager resources(Map<String, String> data) {
        Map<ResourceLocation, Resource> entries = new LinkedHashMap<>();
        data.forEach((id, text) -> entries.put(new ResourceLocation(id), new Resource(null,
                () -> new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)))));
        return new ResourceManager() {
            @Override public Set<String> getNamespaces() { return Set.of("worldeditpp", "other"); }
            @Override public Optional<Resource> getResource(ResourceLocation id) { return Optional.ofNullable(entries.get(id)); }
            @Override public List<Resource> getResourceStack(ResourceLocation id) { return getResource(id).stream().toList(); }
            @Override public Stream<PackResources> listPacks() { return Stream.empty(); }
            @Override public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter) {
                Map<ResourceLocation, Resource> result = new LinkedHashMap<>();
                entries.forEach((id, value) -> { if (id.getPath().startsWith(path) && filter.test(id)) result.put(id, value); });
                return result;
            }
            @Override public Map<ResourceLocation, List<Resource>> listResourceStacks(String path, Predicate<ResourceLocation> filter) {
                Map<ResourceLocation, List<Resource>> result = new LinkedHashMap<>();
                listResources(path, filter).forEach((id, value) -> result.put(id, List.of(value)));
                return result;
            }
        };
    }
}
