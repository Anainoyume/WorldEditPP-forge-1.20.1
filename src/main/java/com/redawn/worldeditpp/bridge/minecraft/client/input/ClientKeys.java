package com.redawn.worldeditpp.bridge.minecraft.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** GLFW modifier queries shared by the client input paths. */
public final class ClientKeys {
    public static boolean isAltDown(Minecraft minecraft) {
        return isAltDown(minecraft.getWindow().getWindow());
    }

    public static boolean isAltDown(long window) {
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
    }

    public static boolean isCtrlDown(Minecraft minecraft) {
        return isCtrlDown(minecraft.getWindow().getWindow());
    }

    public static boolean isCtrlDown(long window) {
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private ClientKeys() { }
}
