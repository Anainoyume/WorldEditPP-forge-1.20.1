package com.redawn.common.ui.tutorial;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TutorialPageHistoryTest {
    @Test void favoritesKeepInsertionOrderAndMovingAReaddedPageToTheEnd() {
        var history = new TutorialPageHistory(List.of("a", "b", "a", ""), List.of());
        assertEquals(List.of("a", "b"), history.favorites());
        assertFalse(history.toggleFavorite("a"));
        assertTrue(history.toggleFavorite("a"));
        assertEquals(List.of("b", "a"), history.favorites());
    }

    @Test void visitsAreUniqueNewestFirstAndLimitedToTwenty() {
        var history = new TutorialPageHistory(List.of(), List.of("old", "older", "old"));
        assertEquals(List.of("old", "older"), history.recent());
        for (int i = 0; i < 24; i++) history.visit("page-" + i);
        assertEquals(TutorialPageHistory.MAX_RECENT, history.recent().size());
        assertEquals("page-23", history.recent().get(0));
        assertEquals("page-4", history.recent().get(history.recent().size() - 1));
        history.visit("page-10");
        assertEquals("page-10", history.recent().get(0));
        assertEquals(TutorialPageHistory.MAX_RECENT, history.recent().size());
        assertEquals(history.recent().size(), new java.util.HashSet<>(history.recent()).size());
    }

    @Test void favoritesAndRecentEntriesCanBeRemovedIndependently() {
        var history = new TutorialPageHistory(List.of("a", "b"), List.of("b", "a"));
        assertTrue(history.removeFavorite("a"));
        assertEquals(List.of("b"), history.favorites());
        assertEquals(List.of("b", "a"), history.recent());
        assertFalse(history.removeFavorite("missing"));

        assertTrue(history.removeRecent("b"));
        assertEquals(List.of("a"), history.recent());
        assertEquals(List.of("b"), history.favorites());
        assertFalse(history.removeRecent("missing"));
    }
}
