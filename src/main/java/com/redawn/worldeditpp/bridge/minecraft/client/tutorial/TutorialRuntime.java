package com.redawn.worldeditpp.bridge.minecraft.client.tutorial;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

/** Selects one guide UI implementation, with the embedded implementation as a fallback. */
public final class TutorialRuntime {
    public static final int EMBEDDED_PRIORITY = 0;
    public static final int EXTERNAL_PRIORITY = 100;
    private static final Map<ResourceLocation, TutorialScreenProvider> PROVIDERS = new LinkedHashMap<>();
    private static final TutorialScreenProvider EMBEDDED = new TutorialScreenProvider() {
        private final ResourceLocation id = new ResourceLocation("worldeditpp", "embedded_tutorial");
        @Override public ResourceLocation id() { return id; }
        @Override public int priority() { return EMBEDDED_PRIORITY; }
        @Override public Screen create(TutorialHost host, Screen parent) { return new TutorialScreen(host, parent); }
        @Override public Screen create(TutorialHost host, Screen parent, String initialPage) {
            return new TutorialScreen(host, parent, initialPage);
        }
    };

    static {
        register(EMBEDDED);
    }

    /** Returns false when this exact provider ID was already claimed. */
    public static synchronized boolean register(TutorialScreenProvider provider) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(provider.id(), "provider.id");
        if (provider.apiVersion() != TutorialScreenProvider.API_VERSION) return false;
        return PROVIDERS.putIfAbsent(provider.id(), provider) == null;
    }

    public static void open(TutorialHost host, Screen parent) {
        open(host, parent, null);
    }

    public static void open(TutorialHost host, Screen parent, String initialPage) {
        TutorialScreenProvider provider = activeProvider();
        Screen screen;
        try {
            screen = Objects.requireNonNull(provider.create(host, parent, initialPage), "Tutorial provider returned no screen");
        } catch (RuntimeException exception) {
            if (provider == EMBEDDED) throw exception;
            host.logger().warn("Tutorial provider {} failed; using embedded fallback", provider.id(), exception);
            screen = EMBEDDED.create(host, parent, initialPage);
        }
        Minecraft.getInstance().setScreen(screen);
    }

    public static synchronized TutorialScreenProvider activeProvider() {
        return preferred(PROVIDERS.values());
    }

    static TutorialScreenProvider preferred(Collection<TutorialScreenProvider> providers) {
        return providers.stream().max(Comparator.comparingInt(TutorialScreenProvider::priority)
                .thenComparing(provider -> provider.id().toString())).orElseThrow();
    }

    private TutorialRuntime() { }
}
