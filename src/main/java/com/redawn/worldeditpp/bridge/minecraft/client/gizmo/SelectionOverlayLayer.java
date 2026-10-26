package com.redawn.worldeditpp.bridge.minecraft.client.gizmo;

import com.redawn.worldeditpp.bridge.minecraft.client.selection.*;

public interface SelectionOverlayLayer {
    void render(SelectionOverlayContext context, SelectionOverlayState state);
}
