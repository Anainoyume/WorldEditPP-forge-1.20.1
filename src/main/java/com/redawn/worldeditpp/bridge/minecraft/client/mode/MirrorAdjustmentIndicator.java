package com.redawn.worldeditpp.bridge.minecraft.client.mode;

import com.redawn.worldeditpp.WorldEditPlusPlus;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.SelectionClientController;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Transient mirror selection overrides the persistent mode's indicator. */
public final class MirrorAdjustmentIndicator implements EditModeIndicator {
    @Override
    public ResourceLocation icon() {
        return new ResourceLocation(WorldEditPlusPlus.MOD_ID, "textures/gui/mirror_mode.png");
    }

    @Override
    public Component label() {
        return Component.translatable("mode.worldeditpp.mirror.adjustment");
    }

    @Override
    public boolean isActive() {
        return SelectionClientController.isMirrorSelecting();
    }
}
