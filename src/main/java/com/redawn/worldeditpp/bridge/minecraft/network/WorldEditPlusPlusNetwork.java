package com.redawn.worldeditpp.bridge.minecraft.network;

import java.util.Objects;
import java.util.function.Consumer;

/** Logical packet dispatch. The loader bridge owns codecs registration and thread handoff. */
public final class WorldEditPlusPlusNetwork {
    private static ClientPayloadHandler clientHandler;
    public static void installClientHandler(ClientPayloadHandler handler) { clientHandler = Objects.requireNonNull(handler); }
    public static void dispatchClient(Consumer<ClientPayloadHandler> action) {
        action.accept(Objects.requireNonNull(clientHandler, "Client payload handler not installed"));
    }
    private WorldEditPlusPlusNetwork() { }
}
