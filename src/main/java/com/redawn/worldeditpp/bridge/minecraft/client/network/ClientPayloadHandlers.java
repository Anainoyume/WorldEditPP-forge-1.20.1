package com.redawn.worldeditpp.bridge.minecraft.client.network;

import com.redawn.worldeditpp.bridge.minecraft.client.mode.AccessibilityClientState;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.SelectionClientController;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.projection.SelectionBlockProjectionController;
import com.redawn.worldeditpp.bridge.minecraft.network.ClipboardPlacementStatusPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.ClientPayloadHandler;
import com.redawn.worldeditpp.bridge.minecraft.network.CuboidSelectionPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.SelectionBlocksPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.SyncAccessibilityPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.WelcomeMessagePayload;

/** Typed client packet handling; never loaded by common/server initialization. */
public final class ClientPayloadHandlers implements ClientPayloadHandler {
    @Override
    public void handle(com.redawn.worldeditpp.bridge.minecraft.network.BlockStateEditResultPayload payload) {
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof
                com.redawn.worldeditpp.bridge.minecraft.client.screen.BlockStateEditorScreen screen) {
            screen.receive(payload);
        }
    }

    @Override
    public void handle(CuboidSelectionPayload payload) {
        SelectionClientController.updateCuboidSelection(payload);
    }

    @Override
    public void handle(SyncAccessibilityPayload payload) {
        AccessibilityClientState.apply(payload);
    }

    @Override
    public void handle(SelectionBlocksPayload payload) {
        SelectionBlockProjectionController.handleSelectionBlocks(payload);
    }

    @Override
    public void handle(ClipboardPlacementStatusPayload payload) {
        SelectionClientController.handleClipboardPlacementStatus(payload);
    }

    @Override
    public void handle(WelcomeMessagePayload payload) {
        com.redawn.worldeditpp.bridge.minecraft.client.tutorial.WelcomeMessageController.show();
    }
}
