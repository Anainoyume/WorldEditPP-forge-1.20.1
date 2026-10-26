package com.redawn.worldeditpp.bridge.minecraft.client.ui;

import com.redawn.worldeditpp.bridge.minecraft.client.mode.ClientEditModeController;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.EditModeIndicator;
import com.redawn.common.ui.UiColor;
import com.redawn.common.ui.UiRect;
import com.redawn.common.ui.UiTexture;
import com.redawn.common.ui.UiText;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class EditModeIndicatorRenderer {
    private static final List<EditModeIndicator> INDICATORS = new ArrayList<>();
    private static final int ICON_SIZE = 16;
    private static final float ICON_SCALE = 1.10F;
    private static final int ICON_RENDER_SIZE = Math.round(ICON_SIZE * ICON_SCALE);
    private static final int HOTBAR_HEIGHT = 22;
    private static final int HOTBAR_HALF_WIDTH = 91;
    private static final int HOTBAR_GAP = 2;
    private static final int ICON_VERTICAL_OFFSET = -4;
    private static final int TEXT_HORIZONTAL_OFFSET = -2;

    public static void register(EditModeIndicator indicator) {
        INDICATORS.add(indicator);
    }

    public static void render(GuiGraphics graphics, float partialTick) {
        if (!ClientEditModeController.isEditing()) return;
        if (INDICATORS.isEmpty()) return;

        EditModeIndicator active = null;
        for (EditModeIndicator indicator : INDICATORS) {
            if (indicator.isActive()) {
                active = indicator;
                break;
            }
        }
        if (active == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        MinecraftUiCanvas canvas = new MinecraftUiCanvas(graphics, mc.font, screenWidth, screenHeight);
        int fontHeight = canvas.fontHeight();

        int hotbarLeft = screenWidth / 2 - HOTBAR_HALF_WIDTH;
        int hotbarTop = screenHeight - HOTBAR_HEIGHT;
        int gap = 4;

        int iconX = hotbarLeft;
        int textY = hotbarTop - HOTBAR_GAP - fontHeight;
        int iconY = textY + (fontHeight - ICON_RENDER_SIZE) / 2 + ICON_VERTICAL_OFFSET;

        ResourceLocation icon = active.icon();
        if (icon != null) {
            int texW = active.iconWidth();
            int texH = active.iconHeight();
            canvas.push();
            canvas.translate(iconX, iconY);
            canvas.scale(ICON_SCALE, ICON_SCALE);
            canvas.texture(new UiTexture(icon.getNamespace(), icon.getPath()),
                    new UiRect(0, 0, ICON_SIZE, ICON_SIZE),
                    new UiRect(0, 0, texW, texH));
            canvas.pop();
        }

        Component label = active.label();
        if (label != null) {
            int textX = iconX + ICON_RENDER_SIZE + gap + TEXT_HORIZONTAL_OFFSET;
            // Keep the mode label inside the left half of the hotbar. English
            // "(Adjustment)" labels are wider than this slot and must marquee.
            int labelWidth = Math.max(24, screenWidth / 2 - textX - 4);
            canvas.textScrolling(UiText.literal(label.getString()), textX, textY, labelWidth,
                    new UiColor(0xFFFFFFFF), true, System.currentTimeMillis());
        }
    }

    private EditModeIndicatorRenderer() {}
}
