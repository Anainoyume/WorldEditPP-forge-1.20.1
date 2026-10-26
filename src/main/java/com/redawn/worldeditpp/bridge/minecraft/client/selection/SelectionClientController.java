package com.redawn.worldeditpp.bridge.minecraft.client.selection;

import com.redawn.worldeditpp.bridge.minecraft.client.gizmo.SelectionOverlayState;

import com.redawn.common.selection.SelectionBox;
import com.redawn.common.selection.SelectionFace;
import com.redawn.common.selection.SelectionFaceHit;
import com.redawn.common.selection.SelectionVector;

import com.redawn.worldeditpp.WorldEditPlusPlus;
import com.redawn.worldeditpp.bridge.minecraft.client.input.ClientKeys;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.ClientEditModeController;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.EditSubModeState;
import com.redawn.worldeditpp.bridge.minecraft.client.selection.projection.SelectionBlockProjectionController;
import com.redawn.worldeditpp.bridge.minecraft.item.ModItems;
import com.redawn.worldeditpp.mode.EditSubMode;
import com.redawn.worldeditpp.bridge.minecraft.network.ClipboardAdjustmentPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.ClipboardPlacementStatusPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.CuboidSelectionPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.DrawSelectionPayload;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.DrawModeController;
import com.redawn.worldeditpp.bridge.minecraft.network.WandClickPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.WandTeleportPayload;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import com.redawn.worldeditpp.bridge.minecraft.api.client.ClientInput;
import com.redawn.worldeditpp.bridge.minecraft.api.PlatformBridge;
import org.lwjgl.glfw.GLFW;

/** Owns selection interaction state and routes input to the active mode. */
public final class SelectionClientController {
    private static final SelectionOverlayState STATE = new SelectionOverlayState();
    private static final WandInteractionState WAND = new WandInteractionState();
    private static final GizmoInteractionManager GIZMO = new GizmoInteractionManager();
    private static final SelectionEditActions EDITS = new SelectionEditActions(STATE);
    private static final Map<EditSubMode, SelectionModeHandler> MODE_HANDLERS = createModeHandlers();

    private static EditSubMode activeMode = EditSubModeState.current();
    private static CuboidSelectionPayload worldSelection = CuboidSelectionPayload.hidden();
    private static boolean clipboardAdjustmentPending;
    private static boolean clipboardAdjustmentActive;
    private static boolean clipboardAdjustmentPlacing;
    private static boolean mirrorSelecting;
    private static boolean mirrorSelectionPending;
    private static int lastPlacementProgress = -1;
    private static boolean placePendingAdjustmentAtSelection;
    private static int pendingAdjustmentMinX;
    private static int pendingAdjustmentMinY;
    private static int pendingAdjustmentMinZ;

    public static GizmoInteractionManager gizmoManager() {
        return GIZMO;
    }

    public static void updateCuboidSelection(CuboidSelectionPayload payload) {
        worldSelection = payload;
        if (!hasClipboardAdjustment()) {
            STATE.updateCuboidSelection(payload);
        }
    }

    public static SelectionFace selectedFace() {
        return STATE.selectedFace();
    }

    public static boolean hasCuboidSelection() {
        return STATE.hasCuboidSelection();
    }

    public static boolean hasWorldSelection() {
        return worldSelection.visible();
    }

    public static boolean isClipboardAdjustmentActive() {
        return clipboardAdjustmentActive;
    }

    public static boolean isClipboardAdjustmentPlacing() {
        return clipboardAdjustmentPlacing;
    }

    public static boolean isClipboardAdjustmentCancellable() {
        return clipboardAdjustmentActive || clipboardAdjustmentPending || clipboardAdjustmentPlacing;
    }

    public static boolean hasClipboardAdjustment() {
        return clipboardAdjustmentActive || clipboardAdjustmentPending || clipboardAdjustmentPlacing;
    }

    public static boolean isMirrorSelecting() {
        return mirrorSelecting;
    }

    public static void toggleMirrorSelection() {
        if (mirrorSelectionPending) {
            mirrorSelectionPending = false;
            cancelClipboardAdjustment();
            return;
        }
        if (!clipboardAdjustmentActive) {
            if (!hasClipboardAdjustment() && worldSelection.visible() && !isRotationInProgress()) {
                mirrorSelectionPending = true;
                startSelectionAdjustment();
            }
            return;
        }
        if (STATE.isSelectionTransformInProgress()
                || !SelectionBlockProjectionController.state().isReady()) return;
        setMirrorSelecting(!mirrorSelecting);
    }

    public static boolean isMirrorShortcutAvailable() {
        return mirrorSelectionPending
                || clipboardAdjustmentActive
                || (!hasClipboardAdjustment() && worldSelection.visible());
    }

    private static void setMirrorSelecting(boolean active) {
        activeHandler().deactivate();
        GIZMO.reset();
        STATE.resetInteraction();
        STATE.resetRotationHandles();
        mirrorSelecting = active;
        STATE.setFaceMasksHidden(!mirrorSelecting);
        STATE.setSuppressDrawMode(true);
    }

    public static void finishMirrorSelection() {
        mirrorSelecting = false;
        STATE.resetInteraction();
        STATE.setFaceMasksHidden(activeMode != EditSubMode.STRETCH);
    }

    private static void updateMirrorHover(Minecraft minecraft, Camera camera) {
        long window = minecraft.getWindow().getWindow();
        boolean back = isAltDown(minecraft) || ClientKeys.isCtrlDown(window);
        SelectionInteractionController.updateHover(minecraft, camera, STATE, back);
    }

    private static void mirrorHoveredFace() {
        if (STATE.isSelectionTransformInProgress() || STATE.hoverHit().isEmpty()) return;
        SelectionFace face = STATE.hoverHit().orElseThrow().face();
        if (STATE.mirrorTo(face, System.nanoTime())) {
            SelectionBlockProjectionController.scheduleMirror(face.ordinal() / 2);
        }
    }

    public static void requestClipboardCopy() {
        if (!hasClipboardAdjustment() && worldSelection.visible()) {
            PlatformBridge.sendToServer(ClipboardAdjustmentPayload.copy());
        }
    }

    public static void requestClipboardAdjustment() {
        if (hasClipboardAdjustment() || isRotationInProgress()) return;
        mirrorSelectionPending = false;
        placePendingAdjustmentAtSelection = false;
        clipboardAdjustmentPending = true;
        STATE.replaceCuboidSelection(CuboidSelectionPayload.hidden());
        SelectionBlockProjectionController.requestAdjustment();
    }

    public static void requestSelectionAdjustment() {
        if (!worldSelection.visible()) {
            requestClipboardAdjustment();
            return;
        }
        if (hasClipboardAdjustment() || isRotationInProgress()) return;
        mirrorSelectionPending = false;
        startSelectionAdjustment();
    }

    private static void startSelectionAdjustment() {
        placePendingAdjustmentAtSelection = true;
        pendingAdjustmentMinX = worldSelection.minX();
        pendingAdjustmentMinY = worldSelection.minY();
        pendingAdjustmentMinZ = worldSelection.minZ();
        clipboardAdjustmentPending = true;
        STATE.replaceCuboidSelection(CuboidSelectionPayload.hidden());
        SelectionBlockProjectionController.requestSelectionAdjustment();
    }

    public static void activateClipboardAdjustment(int sizeX, int sizeY, int sizeZ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!clipboardAdjustmentPending || minecraft.player == null) return;
        int minX;
        int minY;
        int minZ;
        if (placePendingAdjustmentAtSelection) {
            minX = pendingAdjustmentMinX;
            minY = pendingAdjustmentMinY;
            minZ = pendingAdjustmentMinZ;
        } else {
            Vec3 center = minecraft.player.getEyePosition()
                    .add(minecraft.player.getLookAngle().normalize().scale(7.0D));
            minX = (int) Math.floor(center.x - sizeX * 0.5D);
            minY = (int) Math.floor(center.y - sizeY * 0.5D);
            minZ = (int) Math.floor(center.z - sizeZ * 0.5D);
        }
        int maxX = minX + sizeX - 1;
        int maxY = minY + sizeY - 1;
        int maxZ = minZ + sizeZ - 1;
        clipboardAdjustmentPending = false;
        clipboardAdjustmentActive = true;
        placePendingAdjustmentAtSelection = false;
        STATE.replaceCuboidSelection(new CuboidSelectionPayload(
                true, minX, minY, minZ, maxX, maxY, maxZ,
                minX, minY, minZ, maxX, maxY, maxZ));
        if (mirrorSelectionPending) {
            mirrorSelectionPending = false;
            setMirrorSelecting(true);
        }
    }

    public static void cancelClipboardAdjustment() {
        if (!isClipboardAdjustmentCancellable()) return;
        clipboardAdjustmentPlacing = false;
        lastPlacementProgress = -1;
        clipboardAdjustmentActive = false;
        mirrorSelecting = false;
        mirrorSelectionPending = false;
        clipboardAdjustmentPending = false;
        placePendingAdjustmentAtSelection = false;
        SelectionBlockProjectionController.cancelAdjustment(true);
        STATE.replaceCuboidSelection(worldSelection);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.worldeditpp.adjustment.cancelled"),
                    true);
        }
    }

    public static void cancelClipboardAdjustmentFromServer() {
        clipboardAdjustmentActive = false;
        mirrorSelecting = false;
        mirrorSelectionPending = false;
        clipboardAdjustmentPending = false;
        clipboardAdjustmentPlacing = false;
        lastPlacementProgress = -1;
        placePendingAdjustmentAtSelection = false;
        SelectionBlockProjectionController.cancelAdjustment(false);
        STATE.replaceCuboidSelection(worldSelection);
    }

    public static void confirmClipboardAdjustment(boolean ignoreAir) {
        if (!clipboardAdjustmentActive || mirrorSelecting || isRotationInProgress()) return;
        SelectionBox box = STATE.currentSelectionBox();
        PlatformBridge.sendToServer(ClipboardAdjustmentPayload.confirm(
                (int) box.minX(), (int) box.minY(), (int) box.minZ(), ignoreAir));
        clipboardAdjustmentActive = false;
        mirrorSelecting = false;
        mirrorSelectionPending = false;
        clipboardAdjustmentPending = false;
        clipboardAdjustmentPlacing = true;
        lastPlacementProgress = -1;
        placePendingAdjustmentAtSelection = false;
        SelectionBlockProjectionController.cancelAdjustment(false);
        STATE.replaceCuboidSelection(CuboidSelectionPayload.hidden());
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                                    "message.worldeditpp.adjustment.placing")
                            .withStyle(net.minecraft.ChatFormatting.YELLOW),
                    true);
        }
    }

    public static void handleClipboardPlacementStatus(ClipboardPlacementStatusPayload payload) {
        LocalPlayer player = Minecraft.getInstance().player;
        switch (payload.status()) {
            case STARTED -> {
                boolean newlyStarted = !clipboardAdjustmentPlacing;
                clipboardAdjustmentActive = false;
                mirrorSelecting = false;
                mirrorSelectionPending = false;
                clipboardAdjustmentPending = false;
                clipboardAdjustmentPlacing = true;
                placePendingAdjustmentAtSelection = false;
                SelectionBlockProjectionController.cancelAdjustment(false);
                STATE.replaceCuboidSelection(CuboidSelectionPayload.hidden());
                if (newlyStarted && player != null) {
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable(
                                            "message.worldeditpp.adjustment.placing")
                                    .withStyle(net.minecraft.ChatFormatting.YELLOW),
                            true);
                }
            }
            case PROGRESS -> {
                clipboardAdjustmentPlacing = true;
                int percent = payload.totalCells() == 0
                        ? 100
                        : (int) ((long) payload.completedCells() * 100L / payload.totalCells());
                if (percent != lastPlacementProgress && player != null) {
                    lastPlacementProgress = percent;
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable(
                                            "message.worldeditpp.adjustment.placing_progress", percent)
                                    .withStyle(net.minecraft.ChatFormatting.YELLOW),
                            true);
                }
            }
            case COMPLETED -> finishClipboardPlacement(player, true);
            case FAILED -> finishClipboardPlacement(player, false);
        }
    }

    private static void finishClipboardPlacement(LocalPlayer player, boolean success) {
        clipboardAdjustmentActive = false;
        mirrorSelecting = false;
        mirrorSelectionPending = false;
        clipboardAdjustmentPending = false;
        clipboardAdjustmentPlacing = false;
        lastPlacementProgress = -1;
        placePendingAdjustmentAtSelection = false;
        SelectionBlockProjectionController.cancelAdjustment(false);
        STATE.replaceCuboidSelection(worldSelection);
        if (player != null) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(success
                                    ? "message.worldeditpp.adjustment.confirmed"
                                    : "message.worldeditpp.adjustment.failed")
                            .withStyle(success
                                    ? net.minecraft.ChatFormatting.GREEN
                                    : net.minecraft.ChatFormatting.RED),
                    true);
        }
    }

    public static void resetClipboardAdjustment() {
        clipboardAdjustmentActive = false;
        mirrorSelecting = false;
        mirrorSelectionPending = false;
        clipboardAdjustmentPending = false;
        clipboardAdjustmentPlacing = false;
        lastPlacementProgress = -1;
        placePendingAdjustmentAtSelection = false;
        worldSelection = CuboidSelectionPayload.hidden();
        STATE.replaceCuboidSelection(worldSelection);
    }

    public static void resetConnectionState() {
        for (SelectionModeHandler handler : MODE_HANDLERS.values()) {
            handler.deactivate();
        }
        GIZMO.reset();
        WAND.reset();
        activeMode = EditSubModeState.current();
        pendingAdjustmentMinX = 0;
        pendingAdjustmentMinY = 0;
        pendingAdjustmentMinZ = 0;
        resetClipboardAdjustment();
        STATE.setSuppressDrawMode(false);
        STATE.setFaceMasksHidden(activeMode != EditSubMode.STRETCH);
        STATE.resetOverlayStyle();
    }

    public static void rejectClipboardAdjustment() {
        clipboardAdjustmentActive = false;
        mirrorSelecting = false;
        mirrorSelectionPending = false;
        clipboardAdjustmentPending = false;
        clipboardAdjustmentPlacing = false;
        lastPlacementProgress = -1;
        placePendingAdjustmentAtSelection = false;
        STATE.replaceCuboidSelection(worldSelection);
    }

    public static SelectionBox currentSelectionBox() {
        return STATE.currentSelectionBox();
    }

    public static WandInteractionState wandState() {
        return WAND;
    }

    public static boolean isRotationInProgress() {
        return STATE.isRotationAnimating();
    }

    public static SelectionOverlayState state() {
        return STATE;
    }

    public static boolean tryNudgeSelection(double scrollDelta, SelectionVector eye, SelectionVector look) {
        return !mirrorSelecting && !clipboardAdjustmentPlacing
                && EditSubModeState.supports(EditSubMode.Capability.STRETCH)
                && EDITS.nudge(scrollDelta, eye, look);
    }

    public static boolean tryExpandContractSelection(double scrollDelta) {
        return !mirrorSelecting && !clipboardAdjustmentPlacing
                && EditSubModeState.supports(EditSubMode.Capability.STRETCH)
                && EDITS.resize(scrollDelta);
    }

    public static void prepareFrame(Minecraft minecraft, Camera camera) {
        SelectionModeHandler handler = activeHandler();
        boolean emptyHand = minecraft.player.getMainHandItem().isEmpty();

        STATE.tickDisplayAnimation();
        if (SelectionBlockProjectionController.finishRotationIfReady()) {
            SelectionBlockProjectionController.state().tickBake(minecraft);
        }
        STATE.updateEmptyHand(emptyHand);
        WAND.update(minecraft);

        if (clipboardAdjustmentPlacing) {
            handler.deactivate();
            GIZMO.reset();
            STATE.updateHover(Optional.empty());
            STATE.setFaceMasksHidden(true);
        } else if (mirrorSelecting) {
            GIZMO.reset();
            STATE.setSuppressDrawMode(true);
            STATE.setFaceMasksHidden(STATE.isRotationAnimating());
            if (!STATE.isRotationAnimating()) updateMirrorHover(minecraft, camera);
        } else if (STATE.isDrawMode() && !clipboardAdjustmentActive) {
            handler.deactivate();
            GIZMO.reset();
            STATE.setSuppressDrawMode(false);
            STATE.setFaceMasksHidden(true);
            if (STATE.hasCuboidSelection()) {
                SelectionInteractionController.updateHover(minecraft, camera, STATE);
            } else {
                STATE.updateHover(Optional.empty());
            }
        } else {
            handler.prepareFrame(minecraft, camera, emptyHand);
        }

        if (hasClipboardAdjustment()) {
            STATE.setSuppressDrawMode(true);
        }

        GIZMO.tickAnimations();
        STATE.updateDrawState(STATE.hoverHit().isPresent());
        STATE.updateSelectionStyle(
                hasClipboardAdjustment()
                        || emptyHand
                        || WAND.isHoldingWand()
                        || DrawModeController.isDrawing()
        );
        STATE.tickInteractionAnimations();
        if (WAND.isHoldingWand()) {
            WAND.computeTargetPos(minecraft);
        }
    }

    public static void handleMouseButton(ClientInput.Button event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.screen != null) {
            return;
        }

        boolean editing = ClientEditModeController.isEditing();
        if (editing && (mirrorSelecting || STATE.isMirrorAnimating())) {
            if (!STATE.isRotationAnimating() && event.getAction() == GLFW.GLFW_PRESS
                    && event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                updateMirrorHover(minecraft, minecraft.gameRenderer.getMainCamera());
                mirrorHoveredFace();
            }
            event.setCanceled(true);
            return;
        }
        boolean middleClick = event.getAction() == GLFW.GLFW_PRESS
                && event.getButton() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
        boolean holdingModWand = minecraft.player.getMainHandItem().is(ModItems.WORLD_EDIT_WAND.get());
        if (middleClick && holdingModWand && (!editing || !isAltDown(minecraft))) {
            PlatformBridge.sendToServer(new WandTeleportPayload());
            event.setCanceled(true);
            return;
        }
        if (!editing) {
            return;
        }

        if (clipboardAdjustmentPlacing) {
            activeHandler().deactivate();
            GIZMO.reset();
            return;
        }

        SelectionModeHandler handler = activeHandler();
        STATE.updateEmptyHand(minecraft.player.getMainHandItem().isEmpty());
        if (middleClick && !hasClipboardAdjustment()
                && !(WAND.isHoldingWand() && isAltDown(minecraft))) {
            return;
        }
        if (STATE.isRotationAnimating()) {
            event.setCanceled(true);
            return;
        }
        if (hasClipboardAdjustment() && WAND.isHoldingWand() && isAltDown(minecraft)) {
            event.setCanceled(true);
            return;
        }
        if (!STATE.isDrawMode() && handler.handleMouseButton(minecraft, event)) {
            event.setCanceled(true);
            return;
        }

        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }

        // Native WorldEdit selection is driven by the server-side block interaction; the client
        // only knows a physical press. Only play feedback for a real block hit here. The Alt path
        // below sends an explicit corner point and reports its own success.
        if (!isAltDown(minecraft)
                && WAND.isHoldingWand()
                && !STATE.isDrawMode()
                && (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                        || event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                && minecraft.hitResult instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK) {
            minecraft.player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0F, 2.0F);
        }

        if (WAND.isHoldingWand() && isAltDown(minecraft)) {
            if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                    || event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                BlockPos target = WAND.computeTargetPos(minecraft);
                boolean isPrimary = event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT;
                PlatformBridge.sendToServer(new WandClickPayload(
                        target.getX(), target.getY(), target.getZ(), isPrimary));
                // This path explicitly selects the computed point, independent of a block hit.
                minecraft.player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0F, 2.0F);
                WorldEditPlusPlus.LOGGER.debug("WorldEdit++ wand click: pos{} -> ({},{},{})",
                        isPrimary ? "1" : "2", target.getX(), target.getY(), target.getZ());
                event.setCanceled(true);
                return;
            }
            if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                if (minecraft.hitResult instanceof BlockHitResult blockHitResult
                        && blockHitResult.getType() == HitResult.Type.BLOCK) {
                    BlockPos targetPos = blockHitResult.getBlockPos();
                    WAND.alignTo(minecraft, targetPos);
                    WorldEditPlusPlus.LOGGER.debug(
                            "WorldEdit++ wand radius aligned to block ({},{},{}) at distance {}",
                            targetPos.getX(), targetPos.getY(), targetPos.getZ(), WAND.wandDistance()
                    );
                } else {
                    WAND.resetDistance();
                    WAND.computeTargetPos(minecraft);
                    WorldEditPlusPlus.LOGGER.debug(
                            "WorldEdit++ wand distance reset to {}",
                            WAND.wandDistance()
                    );
                }
                event.setCanceled(true);
                return;
            }
        }

        if (STATE.isDrawMode()) {
            if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                    || event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                        && STATE.hoverHit().isPresent()) {
                    SelectionFaceHit hit = STATE.hoverHit().orElseThrow();
                    AABB selectionBox = STATE.cuboidSelectionBox();
                    PlatformBridge.sendToServer(new DrawSelectionPayload(
                            minecraftDirection(hit.face()),
                            hit.location().y() > (selectionBox.minY + selectionBox.maxY) * 0.5D,
                            DrawModeController.current()));
                }
                event.setCanceled(true);
            }
            return;
        }

    }

    private static net.minecraft.core.Direction minecraftDirection(SelectionFace face) {
        return switch (face) {
            case WEST -> net.minecraft.core.Direction.WEST;
            case EAST -> net.minecraft.core.Direction.EAST;
            case DOWN -> net.minecraft.core.Direction.DOWN;
            case UP -> net.minecraft.core.Direction.UP;
            case NORTH -> net.minecraft.core.Direction.NORTH;
            case SOUTH -> net.minecraft.core.Direction.SOUTH;
        };
    }

    public static void handleRenderBlockHighlight(ClientInput.Highlight event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ClientEditModeController.isEditing()
                || minecraft.player == null
                || minecraft.level == null) {
            return;
        }

        if (clipboardAdjustmentPlacing) {
            return;
        }

        if (mirrorSelecting) {
            if (!STATE.isRotationAnimating()) updateMirrorHover(minecraft, event.getCamera());
            if (STATE.isRotationAnimating() || STATE.hoverHit().isPresent()) event.setCanceled(true);
            return;
        }

        SelectionModeHandler handler = activeHandler();
        STATE.updateEmptyHand(minecraft.player.getMainHandItem().isEmpty());
        boolean modeConsumesHighlight = handler.handleBlockHighlight(minecraft, event.getCamera());

        if (!STATE.isEmptyHand() && !STATE.isDrawMode()) {
            return;
        }

        if (STATE.isDrawMode()) {
            SelectionInteractionController.updateHover(minecraft, event.getCamera(), STATE);
            STATE.updateDrawState(STATE.hoverHit().isPresent());
            if (STATE.hoverHit().isPresent()) {
                event.setCanceled(true);
            }
            return;
        }

        if (modeConsumesHighlight) {
            event.setCanceled(true);
        }
    }

    private static SelectionModeHandler activeHandler() {
        EditSubMode requestedMode = EditSubModeState.current();
        if (requestedMode != activeMode) {
            MODE_HANDLERS.get(activeMode).deactivate();
            GIZMO.reset();
            STATE.resetInteraction();
            STATE.setSuppressDrawMode(false);
            STATE.setFaceMasksHidden(requestedMode != EditSubMode.STRETCH);
            activeMode = requestedMode;
        }
        return MODE_HANDLERS.get(activeMode);
    }

    private static Map<EditSubMode, SelectionModeHandler> createModeHandlers() {
        EnumMap<EditSubMode, SelectionModeHandler> handlers = new EnumMap<>(EditSubMode.class);
        register(handlers, new MoveSelectionModeHandler(STATE, GIZMO, EDITS));
        register(handlers, new StretchSelectionModeHandler(STATE, GIZMO));
        register(handlers, new RotateSelectionModeHandler(STATE, GIZMO, EDITS));
        return Map.copyOf(handlers);
    }

    private static void register(
            EnumMap<EditSubMode, SelectionModeHandler> handlers,
            SelectionModeHandler handler
    ) {
        handlers.put(handler.mode(), handler);
    }

    private static boolean isAltDown(Minecraft minecraft) {
        return ClientKeys.isAltDown(minecraft);
    }

    private SelectionClientController() {
    }
}
