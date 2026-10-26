package com.redawn.worldeditpp.bridge.minecraft.client.mode;

import com.mojang.blaze3d.platform.InputConstants;
import com.redawn.worldeditpp.WorldEditPlusPlus;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class EditModeKeyInterceptor {
    private static final Map<String, RegisteredKey> KEYS = new LinkedHashMap<>();
    private static final Set<String> CONSUMED_PRESSES = new HashSet<>();

    public static void register(String id, KeyMapping mapping, KeyMode mode, Consumer<KeyMapping> onPress) {
        register(id, mapping, mode, onPress, null);
    }

    public static void register(String id, KeyMapping mapping, KeyMode mode, Consumer<KeyMapping> onPress, BooleanSupplier activeWhen) {
        KEYS.put(id, new RegisteredKey(id, mapping, mode, onPress, activeWhen, false));
    }

    public static void registerSinglePress(String id, KeyMapping mapping, KeyMode mode,
                                          Consumer<KeyMapping> onPress, BooleanSupplier activeWhen) {
        KEYS.put(id, new RegisteredKey(id, mapping, mode, onPress, activeWhen, true));
    }

    public static boolean onRawKey(InputConstants.Key key) {
        // KeyBindsScreen (and other GUIs) drive KeyMapping.click/set while rebinding.
        // Never consume raw keys under a screen; vanilla must own that interaction.
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.screen != null) return false;
        boolean editing = ClientEditModeController.isEditing();

        for (RegisteredKey reg : KEYS.values()) {
            if (!reg.mapping.matches(key.getValue(), -1)) {
                continue;
            }
            if (reg.activeWhen != null && !reg.activeWhen.getAsBoolean()) {
                continue;
            }

            boolean shouldProcess = switch (reg.mode) {
                case ANY -> true;
                case EDIT_MODE_ONLY -> editing;
            };

            if (!shouldProcess) {
                return false;
            }

            WorldEditPlusPlus.LOGGER.debug("[EditModeKeyInterceptor] 拦截按键: {} (mode={}, editing={})",
                    reg.id, reg.mode, editing);
            if (!reg.singlePress || CONSUMED_PRESSES.add(reg.id)) {
                reg.onPress.accept(reg.mapping);
            }
            return editing;
        }
        return false;
    }

    public static boolean onRawKeySet(InputConstants.Key key, boolean state) {
        // Closing a screen calls KeyMapping.setAll(); never turn that into an editor shortcut.
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.screen != null) return false;
        for (RegisteredKey reg : KEYS.values()) {
            if (!reg.mapping.matches(key.getValue(), -1)) continue;
            if (!state) {
                CONSUMED_PRESSES.remove(reg.id);
                reg.mapping.setDown(false);
            } else if (reg.singlePress && !reg.mapping.isDown()) {
                CONSUMED_PRESSES.remove(reg.id);
            }
        }
        // Closing a Screen calls KeyMapping.setAll(), which can hold vanilla mappings
        // sharing this key (notably X: load hotbar). Always let vanilla release them.
        // Release our mappings even if their active condition changed while held.
        if (!state) return false;
        if (!ClientEditModeController.isEditing()) return false;

        for (RegisteredKey reg : KEYS.values()) {
            if (!reg.mapping.matches(key.getValue(), -1)) {
                continue;
            }
            if (reg.activeWhen != null && !reg.activeWhen.getAsBoolean()) continue;

            reg.mapping.setDown(state);
            return true;
        }
        return false;
    }

    // 绘制模式不是子模式，是按键瞬态。通过 PREPARE_DRAW.isDown() 实时检测。
    public static boolean isDrawMode() {
        return DrawModeController.isDrawing();
    }

    public enum KeyMode {
        ANY,
        EDIT_MODE_ONLY
    }

    private record RegisteredKey(String id, KeyMapping mapping, KeyMode mode,
                                  Consumer<KeyMapping> onPress, BooleanSupplier activeWhen, boolean singlePress) {}

    private EditModeKeyInterceptor() {}
}
