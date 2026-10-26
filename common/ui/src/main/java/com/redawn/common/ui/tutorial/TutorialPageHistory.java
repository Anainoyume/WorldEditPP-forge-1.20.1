package com.redawn.common.ui.tutorial;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Ordered favorites and most-recent-first visits, independent of loaded page objects. */
public final class TutorialPageHistory {
    public static final int MAX_RECENT = 20;
    private final List<String> favorites;
    private final List<String> recent;

    public TutorialPageHistory(List<String> favorites, List<String> recent) {
        this.favorites = clean(favorites, Integer.MAX_VALUE);
        this.recent = clean(recent, MAX_RECENT);
    }
    public List<String> favorites() { return List.copyOf(favorites); }
    public List<String> recent() { return List.copyOf(recent); }
    public boolean isFavorite(String page) { return favorites.contains(page); }
    public boolean toggleFavorite(String page) {
        if (favorites.remove(page)) return false;
        if (!page.isBlank()) favorites.add(page);
        return true;
    }
    public boolean removeFavorite(String page) { return favorites.remove(page); }
    public boolean removeRecent(String page) { return recent.remove(page); }
    public void visit(String page) {
        if (page.isBlank()) return;
        recent.remove(page);
        recent.add(0, page);
        if (recent.size() > MAX_RECENT) recent.subList(MAX_RECENT, recent.size()).clear();
    }
    private static ArrayList<String> clean(List<String> input, int limit) {
        var unique = new LinkedHashSet<String>();
        for (String page : input) if (page != null && !page.isBlank() && unique.size() < limit) unique.add(page);
        return new ArrayList<>(unique);
    }
}
