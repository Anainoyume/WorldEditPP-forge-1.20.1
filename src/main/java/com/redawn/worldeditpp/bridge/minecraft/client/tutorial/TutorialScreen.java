package com.redawn.worldeditpp.bridge.minecraft.client.tutorial;

import com.redawn.common.ui.tutorial.TutorialPageHistory;
import com.redawn.common.ui.tutorial.TutorialScroll;
import com.redawn.common.ui.tutorial.TutorialStyle;
import com.redawn.common.ui.tutorial.TutorialTreePreferences;
import com.redawn.common.ui.tutorial.TutorialImageCache;

import com.redawn.worldeditpp.bridge.minecraft.client.ui.GlassPanelRenderer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Two-pane, non-pausing resource-driven wiki. It never touches editing or selection state. */
public final class TutorialScreen extends Screen {
    // Layout dimensions are approximately 85% of the original design, rounded to native GUI pixels.
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_CONTENT_HEIGHT = ROW_HEIGHT - 2;
    private static final int STAR_WIDTH = 16;
    private final GlassPanelRenderer glass = new GlassPanelRenderer();
    private final TutorialHost host;
    private final Screen parent;
    private final String initialPage;
    private final Set<String> expanded = new HashSet<>();
    private final Map<String, ItemStack> groupIcons = new HashMap<>();
    private final TutorialScroll treeScroll = new TutorialScroll(), articleScroll = new TutorialScroll();
    private final List<Placed> content = new ArrayList<>();
    private TutorialLibrary library;
    private TutorialLibrary.Page page;
    private List<TutorialLibrary.Row> rows = List.of();
    private EditBox search;
    private int left, split, right, top, bottom, treeTop, articleTop, articleX, articleWidth;
    private int focusedRow;
    private TutorialScroll draggingScroll;
    private double grab;
    private TutorialTreePreferences preferences;
    private TutorialPageHistory history = new TutorialPageHistory(List.of(), List.of());
    private View view = View.ARTICLE;
    private enum View { ARTICLE, FAVORITES, RECENT, FAILURES }
    private record Placed(TutorialContent item, int x, int y, int width, int height) { }

    public TutorialScreen(TutorialHost host, Screen parent) {
        this(host, parent, null);
    }
    public TutorialScreen(TutorialHost host, Screen parent, String initialPage) {
        super(Component.translatable(host.translationKey("title")));
        this.host = host;
        this.parent = parent;
        this.initialPage = initialPage;
    }
    @Override protected void init() {
        if (preferences == null) {
            preferences = new TutorialTreePreferences(host.preferencesFile(minecraft.gameDirectory.toPath()),
                    host.legacyPreferencesFiles(minecraft.gameDirectory.toPath()));
            try { preferences.load(); }
            catch (Exception exception) { host.logger().warn("Could not load tutorial folder preferences", exception); }
        }
        boolean firstLoad = library == null;
        String query = search == null ? "" : search.getValue();
        var selectedId = page == null ? null : page.id();
        View restoreView = view;
        double previousTreeOffset = treeScroll.offset();
        double previousArticleOffset = articleScroll.offset();
        String previousLanguage = library == null ? null : library.language();
        String requestedLanguage = minecraft.getLanguageManager().getSelected();
        if (library == null || library.images() != null && library.images().invalidated()
                || !requestedLanguage.equals(loadedLanguage)) {
            if (library != null) library.close();
            library = TutorialLibrary.load(host, minecraft.getResourceManager(), requestedLanguage);
            loadedLanguage = requestedLanguage;
        }
        history = new TutorialPageHistory(preferences.favorites(library.language()), preferences.recent(library.language()));
        expanded.clear();
        for (String folder : library.folders()) {
            if (preferences.expanded(library.language(), folder, library.initiallyExpanded(folder))) expanded.add(folder);
        }
        groupIcons.clear();
        library.groups().forEach((path, group) -> {
            if (group.icon() != null && BuiltInRegistries.ITEM.containsKey(group.icon())) {
                groupIcons.put(path, new ItemStack(BuiltInRegistries.ITEM.get(group.icon())));
            }
        });
        left = 10; right = width - 10; top = 36; bottom = height - 23;
        split = left + Math.max(65, (int) ((right - left) * 0.27));
        treeTop = top + 37;
        articleX = split + 19; articleWidth = Math.max(34, right - articleX - 14);
        // Borderless EditBox renders text at its own y, rather than vertically centering it.
        search = addRenderableWidget(new EditBox(font, left + 10, top + 8 + (21 - font.lineHeight) / 2,
                split - left - 21, font.lineHeight, Component.translatable(host.translationKey("search"))));
        search.setBordered(false);
        search.setMaxLength(160);
        search.setTextColor(TutorialStyle.TEXT);
        search.setHint(Component.translatable(host.translationKey("search")));
        search.setValue(query);
        search.setResponder(value -> { treeScroll.set(0); focusedRow = 0; rebuildTree(); });
        addRenderableWidget(new TutorialButton(right - 17, 10, 17, 15, Component.literal("x"), this::onClose));
        if (parent != null) {
            Component back = Component.translatable(host.translationKey("back"));
            addRenderableWidget(new TutorialButton(right - 27 - font.width(back) - 14, 10, font.width(back) + 14, 15,
                    back, () -> minecraft.setScreen(parent)));
        }
        addHeaderButtons();
        rebuildTree();
        var requestedPage = firstLoad && initialPage != null ? library.pages().stream()
                .filter(p -> p.id().getPath().endsWith("/" + initialPage)).findFirst().orElse(null) : null;
        choose(requestedPage != null ? requestedPage : library.pages().stream().filter(p -> p.id().equals(selectedId)).findFirst()
                .orElse(library.pages().isEmpty() ? null : library.pages().get(0)), firstLoad);
        if (restoreView == View.FAILURES && library.errors() > 0) showFailures();
        else if (restoreView == View.FAVORITES) showFavorites();
        else if (restoreView == View.RECENT) showRecent();
        if (library.language().equals(previousLanguage)) {
            treeScroll.set(previousTreeOffset);
            if (view == restoreView && (view != View.ARTICLE || page != null && page.id().equals(selectedId)))
                articleScroll.set(previousArticleOffset);
        }
        draggingScroll = null;
        setFocused(null);
    }
    private void addHeaderButtons() {
        int x = left + 2 + (groupIcons.containsKey("") ? 19 : 0)
                + font.width(library.groups().containsKey("") ? library.groupTitle("") : title) + 9;
        Component favorites = Component.translatable(host.translationKey("favorites"));
        int favoritesWidth = font.width(favorites) + 12;
        addRenderableWidget(new TutorialButton(x, 10, favoritesWidth, 15, favorites, this::showFavorites));
        x += favoritesWidth + 6;
        Component recent = Component.translatable(host.translationKey("recent"));
        int recentWidth = font.width(recent) + 12;
        addRenderableWidget(new TutorialButton(x, 10, recentWidth, 15, recent, this::showRecent));
    }
    private void rebuildTree() {
        rows = library.rows(search.getValue(), expanded);
        treeScroll.layout(bottom - treeTop - 7, rows.size() * ROW_HEIGHT);
        focusedRow = Math.max(0, Math.min(rows.size() - 1, focusedRow));
    }
    private void choose(TutorialLibrary.Page next) { choose(next, true); }
    private void choose(TutorialLibrary.Page next, boolean recordVisit) {
        page = next; view = View.ARTICLE;
        if (next != null && recordVisit) {
            history.visit(next.id().toString());
            savePreferences();
        }
        layoutArticle();
    }
    private Component articleTitle() {
        Component heading = switch (view) {
            case FAVORITES -> Component.translatable(host.translationKey("favorites_title"));
            case RECENT -> Component.translatable(host.translationKey("recent_title"));
            case FAILURES -> Component.translatable(host.translationKey("errors_title"));
            case ARTICLE -> page == null ? Component.empty() : page.title();
        };
        return Component.empty().withStyle(style -> style.withBold(true)).append(heading);
    }
    private Component articleSubtitle() {
        return switch (view) {
            case FAVORITES -> Component.translatable(host.translationKey("favorites_subtitle"), visiblePages(history.favorites()).size());
            case RECENT -> Component.translatable(host.translationKey("recent_subtitle"), visiblePages(history.recent()).size());
            case FAILURES -> Component.translatable(host.translationKey("errors_subtitle"));
            case ARTICLE -> page == null ? Component.empty() : page.subtitle();
        };
    }
    private void showFailures() { view = View.FAILURES; layoutArticle(); }
    private void showFavorites() { view = View.FAVORITES; layoutArticle(); }
    private void showRecent() { view = View.RECENT; layoutArticle(); }
    private void layoutArticle() {
        articleScroll.set(0); draggingScroll = null;
        content.clear();
        int subtitleLines = font.split(articleSubtitle(), articleWidth).size();
        int titleLines = Math.max(1, font.split(articleTitle(), articleWidth).size());
        articleTop = top + 19 + titleLines * 12 + subtitleLines * 10 + 14;
        List<TutorialContent> items = view == View.ARTICLE && page != null ? page.content() : List.of();
        if (view == View.FAILURES) {
            items = new ArrayList<>();
            for (var failure : library.failures()) {
                items.add(TutorialText.literal(Component.empty()
                        .append(Component.literal(failure.path() + "\n")
                                .withStyle(style -> style.withColor(TutorialStyle.THEME).withBold(true)))
                        .append(Component.literal(failure.reason())
                                .withStyle(style -> style.withColor(net.minecraft.ChatFormatting.RED).withBold(false)))));
            }
        }
        else if (view == View.FAVORITES) items = pageLinks(history.favorites(), "favorites_empty");
        else if (view == View.RECENT) items = pageLinks(history.recent(), "recent_empty");
        int y = 0, gap = 0;
        for (var item : items) {
            y += gap;
            var size = item.measure(articleWidth, Math.max(1, bottom - articleTop - 10));
            content.add(new Placed(item, (articleWidth - size.width()) / 2, y, size.width(), size.height()));
            y += size.height();
            gap = item.spacingAfter();
        }
        articleScroll.layout(Math.max(1, bottom - articleTop - 10), y);
    }
    private List<TutorialLibrary.Page> visiblePages(List<String> ids) {
        List<TutorialLibrary.Page> result = new ArrayList<>();
        for (String id : ids) library.pages().stream().filter(candidate -> candidate.id().toString().equals(id))
                .findFirst().ifPresent(result::add);
        return result;
    }
    private List<TutorialContent> pageLinks(List<String> ids, String emptyKey) {
        var pages = visiblePages(ids);
        if (pages.isEmpty()) return List.of(TutorialText.literal(Component.translatable(host.translationKey(emptyKey))
                .withStyle(style -> style.withColor(TutorialStyle.MUTED))));
        return List.of(new TutorialSavedPageList(pages));
    }
    @Override public void renderBackground(GuiGraphics g) { }
    private String loadedLanguage;
    private void prepareImages() {
        if (library.images() == null) return;
        var cache = library.images().cache;
        library.images().beginFrame();
        long revision = cache.revision();
        int failures = library.errors();
        for (var part : content) {
            int y = articleTop + part.y - (int) articleScroll.offset();
            int priority = y + part.height > articleTop && y < bottom - 10 ? TutorialImageCache.VISIBLE
                    : y + part.height > articleTop - 160 && y < bottom + 150 ? TutorialImageCache.NEARBY
                    : TutorialImageCache.METADATA;
            part.item.prepareImages(priority);
        }
        cache.endFrame();
        if (revision != cache.revision() || view == View.FAILURES && failures != library.errors()) {
            // Keep the first visible component anchored when an async header reveals an image's aspect ratio.
            double offset = articleScroll.offset();
            Placed anchor = content.stream().filter(part -> part.y + part.height > offset).findFirst().orElse(null);
            TutorialScroll previousDrag = draggingScroll;
            layoutArticle();
            double adjusted = offset;
            if (anchor != null) {
                for (var part : content) if (part.item == anchor.item) { adjusted += part.y - anchor.y; break; }
            }
            articleScroll.set(adjusted);
            draggingScroll = previousDrag;
        }
    }
    @Override public void render(GuiGraphics g, int mx, int my, float tick) {
        if (library.images() != null && library.images().invalidated()) rebuildWidgets();
        prepareImages();
        // Existing acrylic tint (39%) followed by 62% black gives approximately 77% total dimming.
        glass.render(g, 0, 0, width, height, 1);
        g.fill(0, 0, width, height, 0x9E000000);
        renderGuide(g, mx, my, tick);
    }
    private void renderGuide(GuiGraphics g, int mx, int my, float tick) {
        int titleX = left + 2;
        if (groupIcons.containsKey("")) { g.renderItem(groupIcons.get(""), titleX, 10); titleX += 19; }
        g.drawString(font, library.groups().containsKey("") ? library.groupTitle("") : title,
                titleX, 14, TutorialStyle.TEXT, false);
        g.renderOutline(left, top, split - left, bottom - top, TutorialStyle.BORDER);
        g.renderOutline(split + 5, top, right - split - 5, bottom - top, TutorialStyle.BORDER);
        g.renderOutline(left + 7, top + 8, split - left - 14, 21,
                search.isFocused() ? TutorialStyle.THEME : TutorialStyle.BORDER);
        renderTree(g, mx, my);
        renderArticle(g, mx, my, tick);
        Component hint = Component.translatable(host.translationKey("close_hint"), host.closeKeyName().get());
        int hintWidth = library.errors() > 0 ? Math.max(0, errorLeft() - left - 10) : right - left;
        g.drawString(font, font.plainSubstrByWidth(hint.getString(), hintWidth), left + 2, height - 14, TutorialStyle.MUTED, false);
        if (library.errors() > 0) {
            if (overErrors(mx, my)) g.fill(errorLeft() - 3, height - 18, right + 3, height - 4, 0x28FFFFFF);
            g.drawString(font, errorLabel(), errorLeft(), height - 14, TutorialStyle.THEME, false);
        }
        super.render(g, mx, my, tick);
        renderNavigationTip(g, mx, my);
    }
    private TutorialNavigation.Link navigationAt(Placed part, double mx, double my) {
        return part == null ? null : part.item.navigationAt(mx - articleX - part.x,
                my - articleTop - part.y + (int) articleScroll.offset(), part.width);
    }
    private void renderNavigationTip(GuiGraphics g, int mx, int my) {
        var link = navigationAt(contentAt(mx, my), mx, my);
        if (link == null || link.tip() == null) return;
        var lines = link.tip().lines(font, Math.max(1, Math.min(240, width - 16)));
        if (lines.isEmpty()) return;
        int lineCount = Math.min(lines.size(), Math.max(1, (height - 16) / TutorialText.LINE_HEIGHT));
        int w = lines.stream().limit(lineCount).mapToInt(font::width).max().orElse(0) + 8;
        int h = lineCount * TutorialText.LINE_HEIGHT + 6;
        int x = Math.max(4, Math.min(mx + 10, width - w - 4));
        int y = my + 12 + h > height - 4 ? Math.max(4, my - h - 6) : my + 12;
        g.pose().pushPose();
        try {
            g.pose().translate(0, 0, 700);
            g.fill(x, y, x + w, y + h, 0xF0000000);
            for (int i = 0; i < lineCount; i++) TutorialText.renderLine(g, font, lines.get(i),
                    x + 4, y + 4 + i * TutorialText.LINE_HEIGHT, TutorialStyle.TEXT);
        } finally { g.pose().popPose(); }
    }
    private boolean navigate(TutorialNavigation.Link link) {
        var next = library.resolvePage(link.source(), link.path());
        if (next == null) return false;
        String path = next.path();
        boolean expansionChanged = false;
        while (path.contains("/")) {
            path = path.substring(0, path.lastIndexOf('/'));
            if (expanded.add(path)) expansionChanged = true;
            preferences.setExpanded(library.language(), path, true);
        }
        if (expansionChanged) savePreferences();
        search.setValue(""); rebuildTree(); choose(next);
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).page() == next) {
            focusedRow = i; treeScroll.set(i * ROW_HEIGHT); break;
        }
        return true;
    }
    private Component errorLabel() { return Component.translatable(host.translationKey("load_errors"), library.errors()); }
    private int errorLeft() { return right - font.width(errorLabel()); }
    private boolean overErrors(double x, double y) {
        return library.errors() > 0 && x >= errorLeft() - 3 && x < right + 3 && y >= height - 18 && y < height - 4;
    }
    private void renderTree(GuiGraphics g, int mx, int my) {
        TutorialGraphics.scissor(g, left + 3, treeTop, split - 7, bottom - 7);
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            int y = treeTop + i * ROW_HEIGHT - (int) treeScroll.offset();
            if (y + ROW_CONTENT_HEIGHT <= treeTop || y >= bottom - 7) continue;
            boolean active = view == View.ARTICLE && row.page() != null && row.page() == page;
            boolean hover = mx >= left + 3 && mx < split - 7 && my >= Math.max(y, treeTop)
                    && my < Math.min(y + ROW_CONTENT_HEIGHT, bottom - 7);
            if (hover || active) g.fill(left + 3, y, split - 7, y + ROW_CONTENT_HEIGHT,
                    active ? 0x19FFF0A0 : 0x12FFFFFF);
            if (active) {
                int indicatorHeight = ROW_CONTENT_HEIGHT - 6;
                int indicatorY = y + (ROW_CONTENT_HEIGHT - indicatorHeight) / 2;
                g.fill(left + 3, indicatorY, left + 5, indicatorY + indicatorHeight, TutorialStyle.THEME);
            }
            int textY = y + (ROW_CONTENT_HEIGHT - font.lineHeight) / 2;
            int x = left + 10 + Math.min(row.depth() * 9, (split - left) / 3);
            if (row.group()) {
                String marker = !search.getValue().isBlank() || expanded.contains(row.path()) ? "\u25BC" : "\u25B6";
                g.drawString(font, marker, x, textY, TutorialStyle.MUTED, false);
            }
            int labelX = x + 9;
            if (row.group() && groupIcons.containsKey(row.path())) {
                g.renderItem(groupIcons.get(row.path()), labelX, y + (ROW_CONTENT_HEIGHT - 16) / 2);
                labelX += 18;
            }
            Component label = row.group() ? library.groupTitle(row.path()) : row.page().title();
            g.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(
                    font.substrByWidth(label, Math.max(10, split - labelX - (row.group() ? 10 : STAR_WIDTH + 8)))), labelX, textY,
                    active ? TutorialStyle.THEME : TutorialStyle.TEXT, false);
            if (!row.group()) {
                int starX = split - STAR_WIDTH - 7;
                boolean starHover = mx >= starX && mx < split - 7 && my >= Math.max(y, treeTop)
                        && my < Math.min(y + ROW_CONTENT_HEIGHT, bottom - 7);
                boolean favorite = history.isFavorite(row.page().id().toString());
                g.drawString(font, favorite ? "\u2605" : "\u2606", starX + 3, textY,
                        favorite ? TutorialStyle.THEME : starHover ? TutorialStyle.TEXT : 0xFFDDDDDD, false);
            }
        }
        if (rows.isEmpty()) g.drawWordWrap(font, Component.translatable(host.translationKey("no_results")),
                left + 10, treeTop + 9, split - left - 20, TutorialStyle.MUTED);
        g.disableScissor();
        scrollbar(g, treeScroll, split - 4, treeTop);
    }
    private void renderArticle(GuiGraphics g, int mx, int my, float tick) {
        if (page == null && view == View.ARTICLE) {
            g.drawWordWrap(font, Component.translatable(host.translationKey("empty")),
                    articleX, top + 19, articleWidth, TutorialStyle.MUTED);
            return;
        }
        int titleY = top + 15;
        int titleLines = 0;
        for (var line : font.split(articleTitle(), articleWidth)) {
            g.drawString(font, line, articleX, titleY + titleLines++ * 12, TutorialStyle.TEXT, false);
        }
        int subtitleY = titleY + titleLines * 12 + 5;
        for (var line : font.split(articleSubtitle(), articleWidth)) {
            g.drawString(font, line, articleX, subtitleY, TutorialStyle.MUTED, false); subtitleY += 10;
        }
        g.fill(articleX, articleTop - 8, articleX + articleWidth, articleTop - 7, TutorialStyle.BORDER);
        TutorialGraphics.scissor(g, articleX, articleTop, articleX + articleWidth, bottom - 10);
        for (var part : content) {
            int y = articleTop + part.y - (int) articleScroll.offset();
            if (y + part.height > articleTop && y < bottom - 10) {
                part.item.render(g, articleX + part.x, y, part.width, part.height, mx, my, tick);
            }
        }
        g.disableScissor();
        scrollbar(g, articleScroll, right - 5, articleTop);
    }
    private void scrollbar(GuiGraphics g, TutorialScroll scroll, int x, int y) {
        if (scroll.maximum() > 0) g.fill(x, y + scroll.top(), x + 2, y + scroll.top() + scroll.thumb(),
                draggingScroll == scroll ? TutorialStyle.THEME : 0x88FFFFFF);
    }
    private boolean startScrollbar(TutorialScroll scroll, int x, int y, double mx, double my) {
        if (scroll.maximum() <= 0 || mx < x - 3 || mx > x + 4 || my < y || my >= bottom - 7) return false;
        double relative = my - y - scroll.top();
        grab = relative >= 0 && relative < scroll.thumb() ? relative : scroll.thumb() / 2.0;
        draggingScroll = scroll; scroll.drag(my - y, grab); return true;
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        setFocused(null); search.setFocused(false);
        if (button == 0 && mx >= left + 7 && mx < split - 7 && my >= top + 8 && my < top + 29) {
            setFocused(search); search.setFocused(true); return true;
        }
        if (button == 0 && overErrors(mx, my)) {
            if (view == View.FAILURES) choose(page); else showFailures();
            return true;
        }
        if (button == 0 && (startScrollbar(treeScroll, split - 4, treeTop, mx, my)
                || startScrollbar(articleScroll, right - 5, articleTop, mx, my))) return true;
        if (button == 0 && mx >= left && mx < split && my >= treeTop && my < bottom - 7) {
            int index = (int) ((my - treeTop + treeScroll.offset()) / ROW_HEIGHT);
            if (index >= 0 && index < rows.size()) {
                focusedRow = index;
                var row = rows.get(index);
                if (!row.group() && mx >= split - STAR_WIDTH - 7 && mx < split - 7) toggleFavorite(row.page());
                else activateRow();
            }
            return true;
        }
        Placed hit = contentAt(mx, my);
        if (button == 0) {
            if (hit != null && hit.item instanceof TutorialSavedPageList savedPages) {
                var removed = savedPages.removalAt(mx - articleX - hit.x,
                        my - articleTop - hit.y + articleScroll.offset(), hit.width);
                if (removed != null) { removeSavedPage(removed); return true; }
            }
            var link = navigationAt(hit, mx, my);
            if (link != null) return navigate(link);
        }
        return false;
    }
    private void activateRow() {
        if (rows.isEmpty()) return;
        var row = rows.get(focusedRow);
        if (row.group()) {
            if (!expanded.remove(row.path())) expanded.add(row.path());
            preferences.setExpanded(library.language(), row.path(), expanded.contains(row.path()));
            savePreferences();
            rebuildTree();
        }
        else choose(row.page());
    }
    private void toggleFavorite(TutorialLibrary.Page target) {
        history.toggleFavorite(target.id().toString());
        savePreferences();
        if (view == View.FAVORITES) layoutArticle();
    }
    private void removeSavedPage(TutorialLibrary.Page target) {
        String id = target.id().toString();
        boolean changed = view == View.FAVORITES ? history.removeFavorite(id)
                : view == View.RECENT && history.removeRecent(id);
        if (!changed) return;
        savePreferences();
        layoutArticle();
    }
    private void savePreferences() {
        preferences.setFavorites(library.language(), history.favorites());
        preferences.setRecent(library.language(), history.recent());
        try { preferences.save(); }
        catch (Exception exception) { host.logger().warn("Could not save tutorial preferences", exception); }
    }
    private Placed contentAt(double x, double y) {
        if (x < articleX || x >= articleX + articleWidth || y < articleTop || y >= bottom - 10) return null;
        double local = y - articleTop + articleScroll.offset();
        return content.stream().filter(p -> local >= p.y && local < p.y + p.height
                && x >= articleX + p.x && x < articleX + p.x + p.width).findFirst().orElse(null);
    }
    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingScroll != null) {
            draggingScroll.drag(my - (draggingScroll == treeScroll ? treeTop : articleTop), grab); return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }
    @Override public boolean mouseReleased(double mx, double my, int button) {
        boolean consumed = draggingScroll != null;
        draggingScroll = null;
        return super.mouseReleased(mx, my, button) || consumed;
    }
    @Override public boolean mouseScrolled(double mx, double my, double vertical) {
        if (mx < split) treeScroll.wheel(vertical);
        else articleScroll.wheel(vertical);
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE || !search.isFocused() && host.closeKey().matches(key, scan)) {
            onClose(); return true;
        }
        if (!search.isFocused() && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
            focusedRow = Math.max(0, Math.min(rows.size() - 1, focusedRow + (key == GLFW.GLFW_KEY_UP ? -1 : 1)));
            treeScroll.set(focusedRow * ROW_HEIGHT); return true;
        }
        if (!search.isFocused() && key == GLFW.GLFW_KEY_ENTER) { activateRow(); return true; }
        if (!search.isFocused() && (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP)) {
            articleScroll.set(articleScroll.offset() + (key == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1) * (bottom - articleTop) * 0.8);
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public void tick() {
        if (minecraft.level == null || !host.available().getAsBoolean()) onClose();
    }
    @Override public void removed() {
        glass.close();
        if (library != null) { library.close(); library = null; }
    }
    @Override public boolean isPauseScreen() { return false; }
}
