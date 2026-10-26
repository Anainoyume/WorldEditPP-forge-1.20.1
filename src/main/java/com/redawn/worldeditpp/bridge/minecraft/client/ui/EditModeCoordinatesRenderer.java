package com.redawn.worldeditpp.bridge.minecraft.client.ui;

import com.redawn.worldeditpp.bridge.minecraft.client.mode.ClientEditModeController;
import com.redawn.common.ui.UiColor;
import com.redawn.common.ui.UiText;
import java.util.Locale;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.HumanoidArm;

/** Renders the player's position and horizontal facing above the hotbar while editing. */
public final class EditModeCoordinatesRenderer {
    private static final int HOTBAR_HALF_WIDTH = 91;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int SIDE_GAP = 4;
    private static final int ABOVE_HOTBAR_GAP = 2;
    private static final int ATTACK_INDICATOR_OFFSET = 6;
    private static final int ATTACK_INDICATOR_WIDTH = 18;
    private static final int BOTTOM_MARGIN = 2;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int VALUE_COLOR = 0xFFFFFF;
    private static final int X_COLOR = 0xFF5555;
    private static final int Y_COLOR = 0x55FF55;
    private static final int Z_COLOR = 0x5555FF;

    public static void render(GuiGraphics graphics, float partialTick) {
        if (!ClientEditModeController.isEditing()) return;

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui) return;

        MinecraftUiCanvas canvas = new MinecraftUiCanvas(graphics, minecraft.font,
                graphics.guiWidth(), graphics.guiHeight());
        UiText xText = coordinate("x", player.getX(), X_COLOR);
        UiText yText = coordinate("y", player.getY(), Y_COLOR);
        UiText zText = coordinate("z", player.getZ(), Z_COLOR);
        UiText facingText = UiText.translated(
                "hud.worldeditpp.facing",
                UiText.translated(directionKey(player.getDirection()))
        );

        int hotbarRight = canvas.width() / 2 + HOTBAR_HALF_WIDTH;
        int coordinateX = hotbarRight + SIDE_GAP;
        if (minecraft.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR
                && player.getMainArm() == HumanoidArm.RIGHT) {
            coordinateX = hotbarRight + ATTACK_INDICATOR_OFFSET + ATTACK_INDICATOR_WIDTH + SIDE_GAP;
        }

        int zY = canvas.height() - canvas.fontHeight() - BOTTOM_MARGIN;
        int yY = zY - canvas.fontHeight();
        int xY = yY - canvas.fontHeight();
        UiColor textColor = new UiColor(TEXT_COLOR);

        canvas.text(xText, coordinateX, xY, textColor, true);
        canvas.text(yText, coordinateX, yY, textColor, true);
        canvas.text(zText, coordinateX, zY, textColor, true);

        int facingX = hotbarRight - canvas.textWidth(facingText);
        int facingY = canvas.height() - HOTBAR_HEIGHT - ABOVE_HOTBAR_GAP - canvas.fontHeight();
        canvas.text(facingText, facingX, facingY, textColor, true);
    }

    private static UiText coordinate(String axis, double value, int axisColor) {
        return UiText.sequence(
                UiText.colored(UiText.literal(axis), new UiColor(axisColor)),
                UiText.colored(UiText.literal(String.format(Locale.ROOT, ": %.2f", value)),
                        new UiColor(VALUE_COLOR)));
    }

    private static String directionKey(Direction direction) {
        return "hud.worldeditpp.direction." + direction.getName();
    }

    private EditModeCoordinatesRenderer() { }
}
