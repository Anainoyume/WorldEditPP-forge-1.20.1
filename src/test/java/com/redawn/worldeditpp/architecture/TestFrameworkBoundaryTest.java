package com.redawn.worldeditpp.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The reusable test-framework package must stay loadable without the game. */
class TestFrameworkBoundaryTest {
    private static final Path ROOT = Path.of("common/test/src/main/java");

    @Test void portableFrameworkHasNoGameDependencies() throws IOException {
        try (Stream<Path> files = Files.walk(ROOT)) {
            List<Path> sources = files.filter(path -> path.toString().endsWith(".java")).toList();
            assertTrue(sources.size() >= 4, "Expected the portable test framework sources");
            for (Path path : sources) {
                String relative = ROOT.relativize(path).toString().replace('\\', '/');
                String source = Files.readString(path);
                assertTrue(relative.startsWith("com/redawn/common/test/"), relative);
                for (String forbidden : List.of(
                        "net.minecraft.", "net.minecraftforge.", "com.mojang.",
                        "com.sk89q.", "org.lwjgl.", "com.redawn.worldeditpp.bridge.")) {
                    assertFalse(source.contains(forbidden), relative + " leaks " + forbidden);
                }
            }
        }
    }
}
