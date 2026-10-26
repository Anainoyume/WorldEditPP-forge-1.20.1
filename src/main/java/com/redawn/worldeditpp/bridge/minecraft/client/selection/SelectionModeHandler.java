package com.redawn.worldeditpp.bridge.minecraft.client.selection;

import com.redawn.worldeditpp.mode.EditSubMode;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import com.redawn.worldeditpp.bridge.minecraft.api.client.ClientInput;

/** Isolated interaction surface for one persistent selection mode. */
interface SelectionModeHandler {
    EditSubMode mode();

    void prepareFrame(Minecraft minecraft, Camera camera, boolean emptyHand);

    default boolean handleMouseButton(Minecraft minecraft, ClientInput.Button event) {
        return false;
    }

    default boolean handleBlockHighlight(Minecraft minecraft, Camera camera) {
        return false;
    }

    default void deactivate() {
    }
}
