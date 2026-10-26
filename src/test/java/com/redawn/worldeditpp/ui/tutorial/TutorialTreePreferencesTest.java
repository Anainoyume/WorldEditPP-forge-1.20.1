package com.redawn.common.ui.tutorial;

import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class TutorialTreePreferencesTest {
    // Windows occasionally fails JUnit's temp-directory cleanup (open handle from an indexer),
    // which turns a passing assertion into a build failure. Clean up best-effort instead.
    @TempDir(cleanup = CleanupMode.NEVER) Path directory;

    @AfterEach void deleteTemporaryDirectory() {
        try (var entries = java.nio.file.Files.walk(directory)) {
            entries.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { java.nio.file.Files.deleteIfExists(path); }
                catch (java.io.IOException ignored) { }
            });
        } catch (java.io.IOException ignored) { }
    }
    @Test void explicitTogglesSurviveRestartAndLanguagesRemainIndependent() throws Exception {
        Path path = directory.resolve("config/guide.properties");
        var first = new TutorialTreePreferences(path);
        first.load();
        assertTrue(first.expanded("zh_cn", "start", true));
        assertFalse(first.expanded("zh_cn", "start/child", false));
        first.setExpanded("zh_cn", "start", false);
        first.setExpanded("zh_cn", "start/child", true);
        first.setFavorites("zh_cn", java.util.List.of("worldeditpp:a", "worldeditpp:b"));
        first.setRecent("zh_cn", java.util.List.of("worldeditpp:b", "worldeditpp:a"));
        first.save();
        var restarted = new TutorialTreePreferences(path);
        restarted.load();
        assertFalse(restarted.expanded("zh_cn", "start", true));
        assertTrue(restarted.expanded("zh_cn", "start/child", false));
        assertEquals(java.util.List.of("worldeditpp:a", "worldeditpp:b"), restarted.favorites("zh_cn"));
        assertEquals(java.util.List.of("worldeditpp:b", "worldeditpp:a"), restarted.recent("zh_cn"));
        assertTrue(restarted.favorites("en_us").isEmpty());
        assertTrue(restarted.expanded("en_us", "start", true));
        assertFalse(restarted.expanded("zh_cn", "new", false));
        assertTrue(restarted.expanded("zh_cn", "new", true));
        restarted.setExpanded("en_us", "start", false);
        restarted.setFavorites("zh_cn", java.util.List.of("worldeditpp:b"));
        restarted.save();
        var third = new TutorialTreePreferences(path);
        third.load();
        assertFalse(third.expanded("en_us", "start", true));
        assertTrue(third.expanded("zh_cn", "start/child", false));
        assertEquals(java.util.List.of("worldeditpp:b"), third.favorites("zh_cn"));
        assertEquals(java.util.List.of("worldeditpp:b", "worldeditpp:a"), third.recent("zh_cn"));
    }

    @Test void legacyPreferencesAreReadButNextSaveUsesNamespacedPath() throws Exception {
        Path legacy = directory.resolve("config/worldeditpp-tutorial.properties");
        var old = new TutorialTreePreferences(legacy);
        old.load();
        old.setExpanded("en_us", "start", false);
        old.save();

        Path namespaced = directory.resolve("config/worldeditpp/tutorial.properties");
        var migrated = new TutorialTreePreferences(namespaced, java.util.List.of(legacy));
        migrated.load();
        assertFalse(migrated.expanded("en_us", "start", true));
        migrated.save();
        assertTrue(java.nio.file.Files.isRegularFile(namespaced));
    }
}
