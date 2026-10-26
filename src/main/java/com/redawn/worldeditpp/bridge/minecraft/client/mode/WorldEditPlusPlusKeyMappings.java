package com.redawn.worldeditpp.bridge.minecraft.client.mode;

import com.mojang.blaze3d.platform.InputConstants;
import com.redawn.worldeditpp.WorldEditPlusPlus;
import com.redawn.worldeditpp.bridge.minecraft.client.ui.EditModeIndicatorRenderer;
import com.redawn.worldeditpp.bridge.minecraft.client.screen.AccessibilityScreen;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.SelectionClientController;
import com.redawn.worldeditpp.mode.EditSubMode;
import com.redawn.worldeditpp.bridge.minecraft.network.ClearSelectionPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.WorldEditHistoryPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import com.redawn.worldeditpp.bridge.minecraft.api.client.ClientPlatformBridge;
import java.util.function.Supplier;
import com.google.common.base.Suppliers;
import com.redawn.worldeditpp.bridge.minecraft.api.PlatformBridge;
import com.redawn.worldeditpp.bridge.minecraft.client.input.ClientKeys;
import org.lwjgl.glfw.GLFW;

public final class WorldEditPlusPlusKeyMappings {
    public static final String CATEGORY = "key.categories." + WorldEditPlusPlus.MOD_ID;
    public static final Supplier<KeyMapping> BLOCK_STATE_EDITOR = Suppliers.memoize(() -> ClientPlatformBridge.get().key(
            "key.worldeditpp.block_state_editor", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY));
    private static final long DOUBLE_TAP_INTERVAL_MS = 500;
    private static long lastBackspaceTime;

    public static final Supplier<KeyMapping> CHANGE_MODE = Suppliers.memoize(() -> ClientPlatformBridge.get().key(
            "key." + WorldEditPlusPlus.MOD_ID + ".change_mode",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            CATEGORY
    ));

    public static final Supplier<KeyMapping> ACCESSIBILITY = Suppliers.memoize(() -> ClientPlatformBridge.get().key(
            "key." + WorldEditPlusPlus.MOD_ID + ".accessibility",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    ));

    public static final Supplier<KeyMapping> DELETE_SELECTION = Suppliers.memoize(() -> ClientPlatformBridge.get().key(
            "key." + WorldEditPlusPlus.MOD_ID + ".delete_selection",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_BACKSPACE,
            CATEGORY
    ));

    public static final Supplier<KeyMapping> SUB_MODE_MOVE = Suppliers.memoize(() -> ClientPlatformBridge.get().key(
            "key." + WorldEditPlusPlus.MOD_ID + ".sub_mode_move",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_1,
            CATEGORY
    ));

    public static final Supplier<KeyMapping> SUB_MODE_STRETCH = Suppliers.memoize(() -> ClientPlatformBridge.get().key(
            "key." + WorldEditPlusPlus.MOD_ID + ".sub_mode_stretch",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_2,
            CATEGORY
    ));

    public static final Supplier<KeyMapping> SUB_MODE_ROTATE = Suppliers.memoize(() -> ClientPlatformBridge.get().key(
            "key." + WorldEditPlusPlus.MOD_ID + ".sub_mode_rotate",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_3,
            CATEGORY
    ));

    public static final Supplier<KeyMapping> PREPARE_DRAW = Suppliers.memoize(() -> ClientPlatformBridge.get().key(
            "key." + WorldEditPlusPlus.MOD_ID + ".prepare_draw",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY
    ));

    public static final Supplier<KeyMapping> ADJUST_CLIPBOARD = Suppliers.memoize(() -> ClientPlatformBridge.get().key(
            "key." + WorldEditPlusPlus.MOD_ID + ".adjust_clipboard",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            CATEGORY
    ));

    public static final Supplier<KeyMapping> PASTE_CLIPBOARD = Suppliers.memoize(() -> ClientPlatformBridge.get().key(
            "key." + WorldEditPlusPlus.MOD_ID + ".paste_clipboard",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY
    ));

    public static final Supplier<KeyMapping> DRAW_WHEEL = Suppliers.memoize(() -> ClientPlatformBridge.get().key(
            "key.worldeditpp.draw_wheel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY));

    public static final Supplier<KeyMapping> MIRROR_ADJUSTMENT = Suppliers.memoize(() -> ClientPlatformBridge.get().key(
            "key." + WorldEditPlusPlus.MOD_ID + ".mirror_adjustment",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY
    ));

    public static final Supplier<KeyMapping> UNDO = Suppliers.memoize(() -> ClientPlatformBridge.get().key(
            "key." + WorldEditPlusPlus.MOD_ID + ".undo",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            CATEGORY
    ));

    public static final Supplier<KeyMapping> REDO = Suppliers.memoize(() -> ClientPlatformBridge.get().key(
            "key." + WorldEditPlusPlus.MOD_ID + ".redo",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            CATEGORY
    ));

    private static final Supplier<KeyMapping> CONFIRM_ADJUSTMENT_ENTER = Suppliers.memoize(() -> ClientPlatformBridge.get().key(
            "key." + WorldEditPlusPlus.MOD_ID + ".confirm_adjustment_enter",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_ENTER,
            CATEGORY
    ));

    public static void registerAll() {
        EditModeKeyInterceptor.registerSinglePress("block_state_editor", BLOCK_STATE_EDITOR.get(),
                EditModeKeyInterceptor.KeyMode.EDIT_MODE_ONLY,
                key -> com.redawn.worldeditpp.bridge.minecraft.client.screen.BlockStateEditorScreen.openTarget(),
                com.redawn.worldeditpp.bridge.minecraft.client.screen.BlockStateEditorScreen::canOpen);
        EditModeIndicatorRenderer.register(new MirrorAdjustmentIndicator());
        EditModeIndicatorRenderer.register(new DrawIndicator());
        for (EditSubMode mode : EditSubMode.values()) {
            EditModeIndicatorRenderer.register(new EditSubModeIndicator(mode));
        }

        EditModeKeyInterceptor.register("change_mode", CHANGE_MODE.get(), EditModeKeyInterceptor.KeyMode.ANY,
                km -> {
                    if (!SelectionClientController.isRotationInProgress()) {
                        ClientEditModeController.handleToggleEditMode();
                    }
                });

        EditModeKeyInterceptor.register("accessibility", ACCESSIBILITY.get(), EditModeKeyInterceptor.KeyMode.EDIT_MODE_ONLY,
                km -> {
                    if (SelectionClientController.hasClipboardAdjustment()
                            || SelectionClientController.isRotationInProgress()) return;
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.screen instanceof AccessibilityScreen) {
                        mc.setScreen(null);
                    } else if (mc.screen == null) {
                        mc.setScreen(new AccessibilityScreen());
                    }
                });

        EditModeKeyInterceptor.register("delete_selection", DELETE_SELECTION.get(), EditModeKeyInterceptor.KeyMode.EDIT_MODE_ONLY,
                km -> {
                    if (SelectionClientController.hasClipboardAdjustment()
                            || SelectionClientController.isRotationInProgress()) return;
                    if (!SelectionClientController.hasCuboidSelection()) return;
                    LocalPlayer player = Minecraft.getInstance().player;
                    if (player == null) return;

                    long now = System.currentTimeMillis();
                    if (now - lastBackspaceTime < DOUBLE_TAP_INTERVAL_MS && lastBackspaceTime > 0) {
                        player.displayClientMessage(Component.empty(), true);
                        PlatformBridge.sendToServer(new ClearSelectionPayload());
                        lastBackspaceTime = 0;
                        WorldEditPlusPlus.LOGGER.debug("WorldEdit++ 双击删除选区键，清除选区");
                    } else {
                        String keyName = DELETE_SELECTION.get().getTranslatedKeyMessage().getString();
                        player.displayClientMessage(
                                Component.translatable("message.worldeditpp.selection.double_tap_delete", keyName)
                                        .withStyle(ChatFormatting.RED), true);
                        lastBackspaceTime = now;
                    }
                });

        EditModeKeyInterceptor.register("sub_mode_move", SUB_MODE_MOVE.get(), EditModeKeyInterceptor.KeyMode.EDIT_MODE_ONLY,
                km -> {
                    if (!SelectionClientController.isRotationInProgress()
                            && !SelectionClientController.isClipboardAdjustmentPlacing()) {
                        EditSubModeState.switchTo(EditSubMode.MOVE);
                    }
                }, WorldEditPlusPlusKeyMappings::isAltDown);

        EditModeKeyInterceptor.register("sub_mode_stretch", SUB_MODE_STRETCH.get(), EditModeKeyInterceptor.KeyMode.EDIT_MODE_ONLY,
                km -> {
                    if (!SelectionClientController.isRotationInProgress()
                            && !SelectionClientController.isClipboardAdjustmentPlacing()) {
                        EditSubModeState.switchTo(EditSubMode.STRETCH);
                    }
                }, WorldEditPlusPlusKeyMappings::isAltDown);

        EditModeKeyInterceptor.register("sub_mode_rotate", SUB_MODE_ROTATE.get(), EditModeKeyInterceptor.KeyMode.EDIT_MODE_ONLY,
                km -> {
                    if (!SelectionClientController.isRotationInProgress()
                            && !SelectionClientController.isClipboardAdjustmentPlacing()) {
                        EditSubModeState.switchTo(EditSubMode.ROTATE);
                    }
                }, WorldEditPlusPlusKeyMappings::isAltDown);

        EditModeKeyInterceptor.register("prepare_draw", PREPARE_DRAW.get(), EditModeKeyInterceptor.KeyMode.EDIT_MODE_ONLY,
                km -> {},
                () -> !SelectionClientController.gizmoManager().isHolded()
                        && !SelectionClientController.gizmoManager().isOrientationTransitioning()
                        && !SelectionClientController.hasClipboardAdjustment()
                        && !SelectionClientController.isRotationInProgress());

        EditModeKeyInterceptor.register("adjust_clipboard", ADJUST_CLIPBOARD.get(),
                EditModeKeyInterceptor.KeyMode.EDIT_MODE_ONLY, km -> {
                    if (isCtrlDown()) {
                        SelectionClientController.requestClipboardCopy();
                    } else if (SelectionClientController.isClipboardAdjustmentCancellable()) {
                        SelectionClientController.cancelClipboardAdjustment();
                    } else if (!SelectionClientController.hasClipboardAdjustment()
                            && SelectionClientController.hasWorldSelection()) {
                        SelectionClientController.requestSelectionAdjustment();
                    } else if (!SelectionClientController.hasClipboardAdjustment()) {
                        SelectionClientController.requestClipboardAdjustment();
                    }
                });

        EditModeKeyInterceptor.register("paste_clipboard", PASTE_CLIPBOARD.get(),
                EditModeKeyInterceptor.KeyMode.EDIT_MODE_ONLY,
                km -> SelectionClientController.requestClipboardAdjustment(),
                WorldEditPlusPlusKeyMappings::isCtrlDown);

        // The controller opens on the physical press edge; this intercepts plain V only.
        EditModeKeyInterceptor.register("draw_wheel", DRAW_WHEEL.get(),
                EditModeKeyInterceptor.KeyMode.EDIT_MODE_ONLY, km -> {}, DrawModeController::canInterceptWheel);

        EditModeKeyInterceptor.registerSinglePress("mirror_adjustment", MIRROR_ADJUSTMENT.get(),
                EditModeKeyInterceptor.KeyMode.EDIT_MODE_ONLY,
                km -> SelectionClientController.toggleMirrorSelection(),
                SelectionClientController::isMirrorShortcutAvailable);

        EditModeKeyInterceptor.register("undo", UNDO.get(), EditModeKeyInterceptor.KeyMode.EDIT_MODE_ONLY,
                km -> PlatformBridge.sendToServer(new WorldEditHistoryPayload(
                        WorldEditHistoryPayload.Action.UNDO)),
                WorldEditPlusPlusKeyMappings::isCtrlDown);

        EditModeKeyInterceptor.register("redo", REDO.get(), EditModeKeyInterceptor.KeyMode.EDIT_MODE_ONLY,
                km -> PlatformBridge.sendToServer(new WorldEditHistoryPayload(
                        WorldEditHistoryPayload.Action.REDO)),
                WorldEditPlusPlusKeyMappings::isCtrlDown);

        EditModeKeyInterceptor.register("confirm_adjustment_enter", CONFIRM_ADJUSTMENT_ENTER.get(),
                EditModeKeyInterceptor.KeyMode.EDIT_MODE_ONLY,
                km -> SelectionClientController.confirmClipboardAdjustment(isAltDown()),
                SelectionClientController::isClipboardAdjustmentActive);
    }

    private WorldEditPlusPlusKeyMappings() {}

    static boolean isAltDown() {
        return ClientKeys.isAltDown(Minecraft.getInstance());
    }

    static boolean isCtrlDown() {
        return ClientKeys.isCtrlDown(Minecraft.getInstance());
    }
}
