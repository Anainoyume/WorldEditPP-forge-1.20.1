package com.redawn.worldeditpp.bridge.minecraft.client.item;

import com.redawn.worldeditpp.WorldEditPlusPlus;
import com.redawn.worldeditpp.bridge.minecraft.item.BlockItemSavedData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Draws the saved-state marker over block items in GUI slots. */
public final class BlockItemStateMarker {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            WorldEditPlusPlus.MOD_ID, "textures/gui/state_marker.png");
    private static final int SIZE = 16;
    private static final int BLIT_OFFSET = 199;


    public static boolean render(
            GuiGraphics guiGraphics, Font font, ItemStack stack, int xOffset, int yOffset) {
        if (!BlockItemSavedData.hasSavedData(stack)) return false;

        guiGraphics.blit(
                TEXTURE,
                xOffset,
                yOffset,
                BLIT_OFFSET,
                0.0F,
                0.0F,
                SIZE,
                SIZE,
                SIZE,
                SIZE);
        return false;
    }

    private BlockItemStateMarker() { }
}
