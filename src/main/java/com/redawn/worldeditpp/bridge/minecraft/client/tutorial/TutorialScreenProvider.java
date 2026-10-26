package com.redawn.worldeditpp.bridge.minecraft.client.tutorial;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-side integration point for a standalone guide engine.
 * Provider IDs must use the provider mod's namespace; registration is idempotent by ID.
 */
public interface TutorialScreenProvider {
    int API_VERSION = 2;
    ResourceLocation id();
    default int apiVersion() { return API_VERSION; }
    int priority();
    Screen create(TutorialHost host, Screen parent);
    default Screen create(TutorialHost host, Screen parent, String initialPage) { return create(host, parent); }
}
