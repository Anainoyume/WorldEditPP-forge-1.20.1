package com.redawn.worldeditpp.config;

import java.util.Objects;
import java.util.function.Supplier;

/** Live configuration reads without exposing a loader's config value types. */
public final class WorldEditPlusPlusConfig {
    private static Supplier<Boolean> debug = () -> false;
    public static final Supplier<Boolean> ENABLE_DEBUG_LOG = () -> debug.get();
    public static void install(Supplier<Boolean> debugLog) {
        debug = Objects.requireNonNull(debugLog);
    }
    private WorldEditPlusPlusConfig() { }
}
