package com.redawn.worldeditpp.bridge.minecraft.client.mode;

import com.redawn.worldeditpp.mode.EditSubMode;
import com.redawn.worldeditpp.bridge.minecraft.network.AccessibilityPayload;
import com.redawn.worldeditpp.bridge.minecraft.api.PlatformBridge;

/*
 * 编辑模式下的子模式状态管理。
 *
 * 子模式(EditSubMode): 持久化到 NBT，退出编辑模式保留，重进恢复。通过 Alt+1/Alt+2/Alt+3 切换。
 * 绘制模式(DrawIndicator): 非子模式，按住 R 键的瞬态叠加层，不持久化，不通过此 State 管理。
 */
public final class EditSubModeState {
    private static EditSubMode current = EditSubMode.MOVE;

    public static EditSubMode current() {
        return current;
    }

    public static void applyFromSync(String subModeId) {
        EditSubMode mode = EditSubMode.fromId(subModeId);
        if (mode != current) {
            current = mode;
        }
    }

    public static void switchTo(EditSubMode mode) {
        if (com.redawn.worldeditpp.bridge.minecraft.client.selection.SelectionClientController.isMirrorSelecting()) return;
        if (mode == current) return;
        current = mode;
        PlatformBridge.sendToServer(new AccessibilityPayload("edit_sub_mode", mode.networkValue()));
    }

    public static boolean supports(EditSubMode.Capability capability) {
        return !com.redawn.worldeditpp.bridge.minecraft.client.selection.SelectionClientController.isMirrorSelecting()
                && current.supports(capability);
    }

    public static void reset() {
        current = EditSubMode.MOVE;
    }

    private EditSubModeState() {}
}
