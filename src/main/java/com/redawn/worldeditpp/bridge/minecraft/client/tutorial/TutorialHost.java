package com.redawn.worldeditpp.bridge.minecraft.client.tutorial;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/** Namespaced content and client behavior supplied by the mod that owns a guide. */
public record TutorialHost(
        ResourceLocation id,
        String resourceNamespace,
        String translationPrefix,
        String preferencesPath,
        List<String> legacyPreferencesPaths,
        Supplier<Component> closeKeyName,
        KeyMatcher closeKey,
        BooleanSupplier available,
        Logger logger
) {
    @FunctionalInterface
    public interface KeyMatcher {
        boolean matches(int keyCode, int scanCode);
    }

    public TutorialHost {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(resourceNamespace, "resourceNamespace");
        Objects.requireNonNull(translationPrefix, "translationPrefix");
        Objects.requireNonNull(preferencesPath, "preferencesPath");
        legacyPreferencesPaths = List.copyOf(legacyPreferencesPaths);
        Objects.requireNonNull(closeKeyName, "closeKeyName");
        Objects.requireNonNull(closeKey, "closeKey");
        Objects.requireNonNull(available, "available");
        Objects.requireNonNull(logger, "logger");
        if (!ResourceLocation.isValidNamespace(resourceNamespace)) {
            throw new IllegalArgumentException("Invalid tutorial resource namespace: " + resourceNamespace);
        }
        validateRelativePath(preferencesPath);
        legacyPreferencesPaths.forEach(TutorialHost::validateRelativePath);
    }

    public String translationKey(String suffix) {
        return translationPrefix + suffix;
    }

    public Path preferencesFile(Path gameDirectory) {
        return gameDirectory.resolve("config").resolve(preferencesPath);
    }

    public List<Path> legacyPreferencesFiles(Path gameDirectory) {
        Path config = gameDirectory.resolve("config");
        return legacyPreferencesPaths.stream().map(config::resolve).toList();
    }

    private static void validateRelativePath(String value) {
        Path path = Path.of(value);
        if (path.isAbsolute() || value.isBlank() || path.normalize().startsWith("..")) {
            throw new IllegalArgumentException("Tutorial preference path must stay inside config: " + value);
        }
    }
}
