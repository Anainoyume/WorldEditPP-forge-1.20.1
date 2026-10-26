package com.redawn.worldeditpp.bridge.minecraft.client.mode;

import com.mojang.blaze3d.platform.InputConstants;
import com.redawn.worldeditpp.bridge.minecraft.api.client.ClientPlatformBridge;
import com.redawn.worldeditpp.bridge.minecraft.client.screen.radial.RadialMenuScreen;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.SelectionClientController;
import com.redawn.worldeditpp.mode.DrawMode;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Session-only draw choice; opening a GUI must not lose the physically held draw key. */
public final class DrawModeController {
    private static DrawMode current = DrawMode.SET;
    private static boolean wheelWasDown;

    public static DrawMode current() { return current; }
    public static void reset() { current = DrawMode.SET; wheelWasDown = false; }

    public static boolean isDrawing() {
        return WorldEditPlusPlusKeyMappings.PREPARE_DRAW.get().isDown()
                || Minecraft.getInstance().screen instanceof RadialMenuScreen<?>
                && keyDown(WorldEditPlusPlusKeyMappings.PREPARE_DRAW.get());
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean down = wheelKeyDown();
        boolean open = shouldOpenWheel(wheelWasDown, down, WorldEditPlusPlusKeyMappings.isCtrlDown(),
                mc.screen == null && available());
        wheelWasDown = down;
        if (open) {
            var entries = Arrays.stream(DrawMode.values()).map(mode -> new RadialMenuScreen.Entry<>(mode,
                    Component.translatable("draw_mode.worldeditpp." + mode.id()))).toList();
            mc.setScreen(new RadialMenuScreen<>(Component.translatable("screen.worldeditpp.draw_wheel"),
                    entries, current, 32, 88, DrawModeController::wheelKeyDown,
                    DrawModeController::available, mode -> current = mode));
        }
    }

    static boolean shouldOpenWheel(boolean wasDown, boolean down, boolean control, boolean available) {
        return down && !wasDown && !control && available;
    }

    public static boolean canInterceptWheel() {
        return Minecraft.getInstance().screen == null && available() && !WorldEditPlusPlusKeyMappings.isCtrlDown();
    }

    private static boolean available() {
        return Minecraft.getInstance().isWindowActive() && ClientEditModeController.isEditing()
                && !SelectionClientController.hasClipboardAdjustment()
                && !SelectionClientController.isRotationInProgress()
                && !SelectionClientController.gizmoManager().isHolded()
                && !SelectionClientController.gizmoManager().isOrientationTransitioning();
    }

    private static boolean wheelKeyDown() {
        return keyDown(WorldEditPlusPlusKeyMappings.DRAW_WHEEL.get());
    }

    private static boolean keyDown(KeyMapping mapping) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isWindowActive()) return false;
        var key = ClientPlatformBridge.get().boundKey(mapping);
        long window = mc.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        return key.getType() == InputConstants.Type.KEYSYM && key.getValue() != GLFW.GLFW_KEY_UNKNOWN
                && InputConstants.isKeyDown(window, key.getValue());
    }

    private DrawModeController() { }
}
