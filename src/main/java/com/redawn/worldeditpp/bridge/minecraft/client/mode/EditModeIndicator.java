package com.redawn.worldeditpp.bridge.minecraft.client.mode;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public interface EditModeIndicator {
    ResourceLocation icon();
    default int iconWidth() { return 32; }
    default int iconHeight() { return 32; }
    Component label();
    default boolean isActive() { return true; }
}
