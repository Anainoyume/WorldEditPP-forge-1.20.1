package com.redawn.worldeditpp.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Prevent integration dependencies from leaking back into application code. */
class BridgeBoundaryTest {
    private static final Path ROOT = Path.of("src/main/java/com/redawn/worldeditpp");
    /** Every third-party/game API that must stay inside the bridge layer. */
    private static final List<String> BRIDGED_APIS = List.of(
            "net.minecraft.", "com.mojang.", "net.neoforged.", "net.minecraftforge.",
            "com.sk89q.", "org.enginehub.", "org.spongepowered.",
            "com.llamalad7.mixinextras.", "org.lwjgl.", "it.unimi.", "com.google.",
            "io.netty.");

    private static List<Path> sources() throws IOException {
        try (var paths = Files.walk(ROOT)) {
            return paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !ROOT.relativize(path).startsWith("gametest")).toList();
        }
    }

    @Test void externalApisHaveExplicitOwners() throws IOException {
        for (Path path : sources()) {
            String relative = ROOT.relativize(path).toString().replace('\\', '/');
            String source = Files.readString(path);
            if (!relative.startsWith("bridge/")) {
                for (String forbidden : BRIDGED_APIS) {
                    assertFalse(source.contains(forbidden), relative + " leaks " + forbidden);
                }
                for (String forbidden : List.of(
                        "com.redawn.worldeditpp.bridge.forge.",
                        "com.redawn.worldeditpp.bridge.worldedit.",
                        "com.redawn.worldeditpp.bridge.minecraft.")) {
                    assertFalse(source.contains(forbidden), relative + " leaks " + forbidden);
                }
                for (String extension : List.of(".getPersistentData(", ".getModelData(",
                        ".isStencilEnabled(", "mapping.getKey(",
                        "ItemBlockRenderTypes.getRenderLayers(")) {
                    assertFalse(source.contains(extension), relative + " bypasses platform bridge: " + extension);
                }
            }
            if (!relative.startsWith("bridge/forge/")) {
                assertFalse(source.contains("net.neoforged."), relative);
                assertFalse(source.contains("net.minecraftforge."), relative);
                assertFalse(source.contains("com.sk89q.worldedit.forge."), relative);
            }
            if (!relative.startsWith("bridge/minecraft/") && !relative.startsWith("bridge/forge/worldedit/mixin/")) {
                assertFalse(source.contains("org.spongepowered.asm.mixin."), relative);
                assertFalse(source.contains("com.llamalad7.mixinextras."), relative);
            }
            if (relative.startsWith("bridge/api/")) {
                assertFalse(source.contains("net.minecraft."), relative);
                assertFalse(source.contains("com.mojang."), relative);
                assertFalse(source.contains("bridge.minecraft."), relative);
                assertFalse(source.contains("bridge.worldedit."), relative);
                assertFalse(source.contains("com.sk89q."), relative);
                assertFalse(source.contains("bridge.forge."), relative);
            }
            if (relative.startsWith("ui/")) {
                for (String forbidden : BRIDGED_APIS) {
                    assertFalse(source.contains(forbidden), relative + " leaks " + forbidden);
                }
                assertFalse(source.contains("com.redawn.worldeditpp.bridge."), relative);
            }
            if (relative.startsWith("gizmo/")) {
                for (String forbidden : BRIDGED_APIS) {
                    assertFalse(source.contains(forbidden), relative + " leaks " + forbidden);
                }
                assertFalse(source.contains("com.redawn.worldeditpp.bridge."), relative);
            }
            if (relative.startsWith("bridge/minecraft/")) {
                assertFalse(source.contains("net.minecraftforge."), relative);
                assertFalse(source.contains("com.sk89q."), relative);
                assertFalse(source.contains("bridge.forge."), relative);
                assertFalse(source.contains("bridge.worldedit."), relative);
            }
            if (relative.startsWith("bridge/worldedit/")) {
                assertFalse(source.contains("net.minecraftforge."), relative);
                assertFalse(source.contains("net.minecraft.client."), relative);
                assertFalse(source.contains("bridge.minecraft.client."), relative);
                assertFalse(source.contains("bridge.forge."), relative);
            }
        }
    }

    @Test void clientAndCommonBootstrapDoNotResolveWorldEditClientRegistries() throws IOException {        for (Path path : sources()) {
            String relative = ROOT.relativize(path).toString().replace('\\', '/');
            if (relative.startsWith("client/") || relative.startsWith("bridge/api/")
                    || relative.startsWith("bridge/minecraft/client/") || relative.startsWith("bridge/minecraft/api/client/")
                    || relative.startsWith("bridge/forge/client/") || relative.startsWith("bridge/minecraft/mixin/client/")) {
                String source = Files.readString(path);
                assertFalse(source.contains("com.sk89q."), relative);
                assertFalse(source.contains("bridge.worldedit."), relative);
            }
        }
        String platform = Files.readString(ROOT.resolve("bridge/forge/ForgePlatform.java"));
        assertFalse(platform.contains("net.minecraft.client."), "Dedicated server must not link client classes");
    }

    @Test void everyConfiguredMixinExistsInTheBridge() throws IOException {
        int count = 0;
        for (String file : List.of("worldeditpp.mixins.json", "worldeditpp.worldedit.mixins.json")) {
            String config = Files.readString(Path.of("src/main/resources",file));
            var packageMatcher = Pattern.compile("\"package\"\\s*:\\s*\"([^\"]+)\"").matcher(config);
            assertTrue(packageMatcher.find());
            String mixinPackage = packageMatcher.group(1);
            assertTrue(mixinPackage.startsWith("com.redawn.worldeditpp.bridge.") && mixinPackage.endsWith(".mixin"),
                    "Mixin packages must not include ordinary bootstrap or helper classes");
            var names = Pattern.compile("\"((?:[a-z]+\\.)*[A-Za-z]+Mixin)\"").matcher(config);
            while (names.find()) {
                Path source = Path.of("src/main/java", mixinPackage.replace('.', '/'),
                        names.group(1).replace('.', '/') + ".java");
                assertTrue(Files.isRegularFile(source), source.toString());
                count++;
            }
        }
        assertEquals(14, count);
        assertTrue(sources().stream().noneMatch(path -> ROOT.relativize(path).startsWith("mixin")));
    }

    @Test void purePasteAlgorithmsRemainIndependent() throws IOException {
        try (var paths = Files.walk(ROOT.resolve("operation/paste"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                assertFalse(source.contains("net.minecraft."), path.toString());
                assertFalse(source.contains("bridge."), path.toString());
            }
        }
    }
}
