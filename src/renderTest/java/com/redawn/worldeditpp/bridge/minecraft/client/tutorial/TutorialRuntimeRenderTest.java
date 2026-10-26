package com.redawn.worldeditpp.bridge.minecraft.client.tutorial;



import com.redawn.worldeditpp.bridge.minecraft.client.tutorial.TutorialHost;
import com.redawn.worldeditpp.bridge.minecraft.client.tutorial.TutorialRuntime;
import com.redawn.worldeditpp.bridge.minecraft.client.tutorial.TutorialScreenProvider;

import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TutorialRuntimeRenderTest {
    @Test void externalProviderWinsWithoutRemovingEmbeddedFallback() {
        var embedded = provider("worldeditpp:embedded", TutorialRuntime.EMBEDDED_PRIORITY);
        var external = provider("exampleguide:screen", TutorialRuntime.EXTERNAL_PRIORITY);
        assertSame(external, TutorialRuntime.preferred(List.of(embedded, external)));
        assertSame(embedded, TutorialRuntime.preferred(List.of(embedded)));
    }

    @Test void providerRegistrationIsIdempotentByNamespacedId() {
        var provider = provider("runtime_test:low_priority", -100);
        assertTrue(TutorialRuntime.register(provider));
        assertFalse(TutorialRuntime.register(provider("runtime_test:low_priority", 1000)));
    }

    @Test void incompatibleProviderApiIsRejected() {
        var incompatible = new TutorialScreenProvider() {
            @Override public ResourceLocation id() { return new ResourceLocation("runtime_test:incompatible"); }
            @Override public int apiVersion() { return API_VERSION + 1; }
            @Override public int priority() { return 1000; }
            @Override public Screen create(TutorialHost host, Screen parent) { return null; }
        };
        assertFalse(TutorialRuntime.register(incompatible));
    }

    private static TutorialScreenProvider provider(String id, int priority) {
        return new TutorialScreenProvider() {
            private final ResourceLocation key = new ResourceLocation(id);
            @Override public ResourceLocation id() { return key; }
            @Override public int priority() { return priority; }
            @Override public Screen create(TutorialHost host, Screen parent) { return null; }
        };
    }
}
