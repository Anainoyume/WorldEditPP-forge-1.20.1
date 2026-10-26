package com.redawn.worldeditpp.bridge.minecraft.network;

/** Common-side contract installed by the client entry point before joining a server. */
public interface ClientPayloadHandler {
    void handle(BlockStateEditResultPayload payload);
    void handle(CuboidSelectionPayload payload);
    void handle(SyncAccessibilityPayload payload);
    void handle(SelectionBlocksPayload payload);
    void handle(ClipboardPlacementStatusPayload payload);
    void handle(WelcomeMessagePayload payload);
}
