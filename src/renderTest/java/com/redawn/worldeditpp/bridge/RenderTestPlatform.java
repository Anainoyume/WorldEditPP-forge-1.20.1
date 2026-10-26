package com.redawn.worldeditpp.bridge;

import com.redawn.worldeditpp.bridge.minecraft.api.client.ClientPlatformBridge;
import com.redawn.worldeditpp.bridge.forge.client.ForgeClientPlatform;

/** Shared bootstrap for JUnit processes that do not run the mod loader. */
public final class RenderTestPlatform {
    private static boolean installed;

    public static synchronized void install() {
        if (installed) return;
        ClientPlatformBridge.install(new ForgeClientPlatform());
        installed = true;
    }

    private RenderTestPlatform() {}
}
