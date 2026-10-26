package com.redawn.worldeditpp.bridge.minecraft.client.mode;

import com.redawn.worldeditpp.WorldEditPlusPlus;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.SelectionClientController;
import com.redawn.worldeditpp.mode.EditSubMode;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Client presentation for a common persistent edit mode. */
public record EditSubModeIndicator(EditSubMode mode) implements EditModeIndicator {
    @Override
    public ResourceLocation icon() {
        String prefix = SelectionClientController.isClipboardAdjustmentActive() ? "" : "selection_";
        return new ResourceLocation(
                WorldEditPlusPlus.MOD_ID,
                "textures/gui/" + prefix + mode.id() + "_mode.png"
        );
    }

    @Override
    public Component label() {
        String suffix = SelectionClientController.isClipboardAdjustmentActive() ? ".adjustment" : "";
        return Component.translatable("mode." + WorldEditPlusPlus.MOD_ID + "." + mode.id() + suffix);
    }

    @Override
    public boolean isActive() {
        return EditSubModeState.current() == mode;
    }
}
