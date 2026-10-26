package com.redawn.worldeditpp.bridge.minecraft.client.gizmo;

import com.redawn.common.gizmo.RotationHandleAnimator;
import com.redawn.common.selection.SelectionBox;
import com.redawn.common.selection.SelectionFace;
import com.redawn.common.selection.SelectionFaceHit;
import com.redawn.common.selection.SelectionInteractionState;
import com.redawn.common.selection.SelectionTransforms;

import com.redawn.worldeditpp.bridge.minecraft.network.CuboidSelectionPayload;
import java.util.Optional;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SelectionOverlayState {
    public SelectionOverlayState() { }
    private static final double ANIMATION_SPEED = 0.30D;
    private static final double ANIMATION_SNAP_THRESHOLD = 0.01D;
    private static final long ROTATION_DURATION_NANOS = 200_000_000L;

    private CuboidSelectionPayload cuboidSelection = CuboidSelectionPayload.hidden();
    private CuboidSelectionPayload animationTarget;
    private long nextRequestId;
    private long pendingRequestId;
    private long acknowledgedRequestId;
    private final SelectionInteractionState interaction = new SelectionInteractionState();
    private final RotationHandleAnimator rotationHandles = new RotationHandleAnimator();

    /*
     * 绘制模式瞬态状态。
     *
     * 这是暂时态,不是子模式。激活条件:按住 R 键 + 编辑模式。
     * drawModeActive   = R键是否按下（在 updateDrawState 中每帧实时读取 KeyMapping.isDown()）
     * drawTargetProgress = 颜色混合动画进度 [0,1]，准星击中选区时趋向1，否则趋向0
     *
     * 绘制模式会禁用:面选择、推面、缩放、悬停遮罩、原版方块描边。
     * 选区颜色从淡黄过渡到品红（通过 drawColorBlend() 在各渲染层中插值）。
     * 此状态由每帧的 render() 循环维护，不通过 EditSubModeState。
     */
    private boolean drawModeActive;
    private float drawTargetProgress;
    private boolean faceMasksHidden;
    private boolean suppressDrawMode;
    private static final float DRAW_COLOR_BLEND_SPEED = 0.08F;
    private static final float SELECTION_STYLE_BLEND_SPEED = 0.08F;
    private float selectionStyleProgress = 1.0F;

    private boolean animating;
    private double animMinX, animMinY, animMinZ, animMaxX, animMaxY, animMaxZ;
    private double animPos1X, animPos1Y, animPos1Z, animPos2X, animPos2Y, animPos2Z;

    private boolean rotationAnimating;
    private CuboidSelectionPayload rotationSource;
    private CuboidSelectionPayload rotationTarget;
    private long rotationStartNanos;
    private float rotationProgress;
    private double rotationPivotX;
    private double rotationPivotY;
    private double rotationPivotZ;
    private double rotationTranslationX;
    private double rotationTranslationY;
    private double rotationTranslationZ;
    private int rotationAxis;
    private int rotationQuarterTurn;
    private int mirrorAxis = -1;

    public void updateCuboidSelection(CuboidSelectionPayload payload) {
        if (payload.acknowledgedRequestId() < acknowledgedRequestId
                || (pendingRequestId != 0 && payload.acknowledgedRequestId() < pendingRequestId)) {
            return;
        }
        acknowledgedRequestId = payload.acknowledgedRequestId();
        pendingRequestId = 0;
        boolean changed = !sameSelectionAndCorners(cuboidSelection, payload);
        this.cuboidSelection = payload;
        // A matching acknowledgement never interrupts interpolation or clears the selected face.
        // A different final result is an authoritative correction or an external selection edit.
        if (changed || !payload.visible()) {
            resetInteraction();
            snapAnimation();
            cancelRotationAnimation();
        }
    }

    public long beginSelectionRequest() {
        pendingRequestId = ++nextRequestId;
        return pendingRequestId;
    }

    public void replaceCuboidSelection(CuboidSelectionPayload payload) {
        cuboidSelection = payload;
        animating = false;
        animationTarget = null;
        pendingRequestId = 0;
        acknowledgedRequestId = payload.acknowledgedRequestId();
        nextRequestId = Math.max(nextRequestId, acknowledgedRequestId);
        cancelRotationAnimation();
        resetInteraction();
        snapAnimation();
    }

    public boolean rotateTo(SelectionTransforms.RotatedSelection rotation, long nowNanos) {
        if (!hasCuboidSelection() || animating || rotationAnimating) {
            return false;
        }
        SelectionBox box = rotation.box();
        CuboidSelectionPayload target = new CuboidSelectionPayload(
                true,
                (int) box.minX(), (int) box.minY(), (int) box.minZ(),
                (int) box.maxX(), (int) box.maxY(), (int) box.maxZ(),
                rotation.pos1X(), rotation.pos1Y(), rotation.pos1Z(),
                rotation.pos2X(), rotation.pos2Y(), rotation.pos2Z()
        );
        rotationSource = cuboidSelection;
        mirrorAxis = -1;
        rotationTarget = target;
        rotationStartNanos = nowNanos;
        rotationProgress = 0.0F;
        rotationPivotX = (rotationSource.minX() + rotationSource.maxX() + 1.0D) * 0.5D;
        rotationPivotY = (rotationSource.minY() + rotationSource.maxY() + 1.0D) * 0.5D;
        rotationPivotZ = (rotationSource.minZ() + rotationSource.maxZ() + 1.0D) * 0.5D;
        rotationTranslationX = (target.minX() + target.maxX() + 1.0D) * 0.5D - rotationPivotX;
        rotationTranslationY = (target.minY() + target.maxY() + 1.0D) * 0.5D - rotationPivotY;
        rotationTranslationZ = (target.minZ() + target.maxZ() + 1.0D) * 0.5D - rotationPivotZ;
        rotationAxis = rotation.axis();
        rotationQuarterTurn = rotation.quarterTurn();
        rotationAnimating = true;
        cuboidSelection = target;
        resetInteraction();
        return true;
    }

    public boolean mirrorTo(SelectionFace face, long nowNanos) {
        SelectionBox source = currentSelectionBox();
        SelectionBox target = SelectionTransforms.mirror(source, face);
        int axis = face.ordinal() / 2;
        if (!rotateTo(new SelectionTransforms.RotatedSelection(target,
                (int) target.minX(), (int) target.minY(), (int) target.minZ(),
                (int) target.maxX(), (int) target.maxY(), (int) target.maxZ(), axis, 0), nowNanos)) {
            return false;
        }
        mirrorAxis = axis;
        if (axis == 0) rotationPivotX = face == SelectionFace.WEST ? source.minX() : source.maxX() + 1;
        if (axis == 1) rotationPivotY = face == SelectionFace.DOWN ? source.minY() : source.maxY() + 1;
        if (axis == 2) rotationPivotZ = face == SelectionFace.NORTH ? source.minZ() : source.maxZ() + 1;
        rotationTranslationX = rotationTranslationY = rotationTranslationZ = 0;
        return true;
    }

    public boolean isMirrorAnimating() {
        return rotationAnimating && mirrorAxis >= 0;
    }

    public float mirrorScale() {
        float scale = 1.0F - 2.0F * rotationProgress;
        // PoseStack's normal matrix divides by scale. Never pass a singular transform.
        return Math.abs(scale) < 0.001F ? Math.copySign(0.001F, scale) : scale;
    }

    public void nudgeTo(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        CuboidSelectionPayload oldRef;
        if (animating && animationTarget != null) {
            oldRef = animationTarget;
        } else if (hasCuboidSelection()) {
            oldRef = cuboidSelection;
        } else {
            return;
        }

        if (!animating) {
            animMinX = oldRef.minX();
            animMinY = oldRef.minY();
            animMinZ = oldRef.minZ();
            animMaxX = oldRef.maxX();
            animMaxY = oldRef.maxY();
            animMaxZ = oldRef.maxZ();
            animPos1X = oldRef.pos1X();
            animPos1Y = oldRef.pos1Y();
            animPos1Z = oldRef.pos1Z();
            animPos2X = oldRef.pos2X();
            animPos2Y = oldRef.pos2Y();
            animPos2Z = oldRef.pos2Z();
        }

        int newPos1X = trackCorner(oldRef.pos1X(), oldRef.minX(), oldRef.maxX(), minX, maxX);
        int newPos1Y = trackCorner(oldRef.pos1Y(), oldRef.minY(), oldRef.maxY(), minY, maxY);
        int newPos1Z = trackCorner(oldRef.pos1Z(), oldRef.minZ(), oldRef.maxZ(), minZ, maxZ);
        int newPos2X = trackCorner(oldRef.pos2X(), oldRef.minX(), oldRef.maxX(), minX, maxX);
        int newPos2Y = trackCorner(oldRef.pos2Y(), oldRef.minY(), oldRef.maxY(), minY, maxY);
        int newPos2Z = trackCorner(oldRef.pos2Z(), oldRef.minZ(), oldRef.maxZ(), minZ, maxZ);

        if (oldRef.minX() == oldRef.maxX() && newPos1X == newPos2X) {
            newPos1X = minX;
            newPos2X = maxX;
        }
        if (oldRef.minY() == oldRef.maxY() && newPos1Y == newPos2Y) {
            newPos1Y = minY;
            newPos2Y = maxY;
        }
        if (oldRef.minZ() == oldRef.maxZ() && newPos1Z == newPos2Z) {
            newPos1Z = minZ;
            newPos2Z = maxZ;
        }

        this.animationTarget = new CuboidSelectionPayload(true,
                minX, minY, minZ, maxX, maxY, maxZ,
                newPos1X, newPos1Y, newPos1Z, newPos2X, newPos2Y, newPos2Z);
        // Logical bounds outlive display interpolation and remain the base for the next edit,
        // even if a remote server has not acknowledged this target yet.
        this.cuboidSelection = this.animationTarget;
        this.animating = true;
    }

    public void nudgeLocalTo(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        nudgeTo(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static int trackCorner(int cornerValue, int oldMin, int oldMax, int newMin, int newMax) {
        if (cornerValue == oldMin) {
            return newMin;
        }
        if (cornerValue == oldMax) {
            return newMax;
        }
        return cornerValue;
    }

    public void tickDisplayAnimation() {
        tickDisplayAnimation(System.nanoTime());
    }

    public void tickDisplayAnimation(long nowNanos) {
        if (rotationAnimating) {
            tickRotationAnimation(nowNanos);
            return;
        }
        if (!animating || animationTarget == null || !hasCuboidSelection()) {
            return;
        }

        animMinX = lerp(animMinX, animationTarget.minX(), ANIMATION_SPEED);
        animMinY = lerp(animMinY, animationTarget.minY(), ANIMATION_SPEED);
        animMinZ = lerp(animMinZ, animationTarget.minZ(), ANIMATION_SPEED);
        animMaxX = lerp(animMaxX, animationTarget.maxX(), ANIMATION_SPEED);
        animMaxY = lerp(animMaxY, animationTarget.maxY(), ANIMATION_SPEED);
        animMaxZ = lerp(animMaxZ, animationTarget.maxZ(), ANIMATION_SPEED);
        animPos1X = lerp(animPos1X, animationTarget.pos1X(), ANIMATION_SPEED);
        animPos1Y = lerp(animPos1Y, animationTarget.pos1Y(), ANIMATION_SPEED);
        animPos1Z = lerp(animPos1Z, animationTarget.pos1Z(), ANIMATION_SPEED);
        animPos2X = lerp(animPos2X, animationTarget.pos2X(), ANIMATION_SPEED);
        animPos2Y = lerp(animPos2Y, animationTarget.pos2Y(), ANIMATION_SPEED);
        animPos2Z = lerp(animPos2Z, animationTarget.pos2Z(), ANIMATION_SPEED);

        if (distanceToTarget() < ANIMATION_SNAP_THRESHOLD) {
            snapAnimation();
        }
    }

    public void snapAnimation() {
        animating = false;
        animationTarget = null;
        if (hasCuboidSelection()) {
            animMinX = cuboidSelection.minX();
            animMinY = cuboidSelection.minY();
            animMinZ = cuboidSelection.minZ();
            animMaxX = cuboidSelection.maxX();
            animMaxY = cuboidSelection.maxY();
            animMaxZ = cuboidSelection.maxZ();
            animPos1X = cuboidSelection.pos1X();
            animPos1Y = cuboidSelection.pos1Y();
            animPos1Z = cuboidSelection.pos1Z();
            animPos2X = cuboidSelection.pos2X();
            animPos2Y = cuboidSelection.pos2Y();
            animPos2Z = cuboidSelection.pos2Z();
        }
    }

    private void tickRotationAnimation(long nowNanos) {
        float linear = Math.min(1.0F, Math.max(0.0F,
                (float) (nowNanos - rotationStartNanos) / ROTATION_DURATION_NANOS));
        float remaining = 1.0F - linear;
        rotationProgress = 1.0F - remaining * remaining * remaining;
        if (linear >= 1.0F) {
            if (rotationTarget != null) {
                cuboidSelection = rotationTarget;
            }
            rotationAnimating = false;
            rotationSource = null;
            rotationTarget = null;
            rotationProgress = 0.0F;
        }
    }

    private void cancelRotationAnimation() {
        rotationAnimating = false;
        rotationSource = null;
        rotationTarget = null;
        rotationProgress = 0.0F;
    }

    public CuboidSelectionPayload animationTarget() {
        return animationTarget;
    }

    public int animPos1X() {
        return animationTarget != null ? animationTarget.pos1X() : cuboidSelection.pos1X();
    }

    public int animPos1Y() {
        return animationTarget != null ? animationTarget.pos1Y() : cuboidSelection.pos1Y();
    }

    public int animPos1Z() {
        return animationTarget != null ? animationTarget.pos1Z() : cuboidSelection.pos1Z();
    }

    public int animPos2X() {
        return animationTarget != null ? animationTarget.pos2X() : cuboidSelection.pos2X();
    }

    public int animPos2Y() {
        return animationTarget != null ? animationTarget.pos2Y() : cuboidSelection.pos2Y();
    }

    public int animPos2Z() {
        return animationTarget != null ? animationTarget.pos2Z() : cuboidSelection.pos2Z();
    }

    public boolean hasCuboidSelection() {
        return cuboidSelection.visible();
    }

    public int pos1X() {
        if (rotationAnimating && rotationSource != null) {
            return rotationSource.pos1X();
        }
        return animating ? (int) Math.round(animPos1X) : cuboidSelection.pos1X();
    }

    public int pos1Y() {
        if (rotationAnimating && rotationSource != null) {
            return rotationSource.pos1Y();
        }
        return animating ? (int) Math.round(animPos1Y) : cuboidSelection.pos1Y();
    }

    public int pos1Z() {
        if (rotationAnimating && rotationSource != null) {
            return rotationSource.pos1Z();
        }
        return animating ? (int) Math.round(animPos1Z) : cuboidSelection.pos1Z();
    }

    public double smoothPos1X() {
        if (rotationAnimating && rotationSource != null) {
            return rotationSource.pos1X();
        }
        return animating ? animPos1X : cuboidSelection.pos1X();
    }

    public double smoothPos1Y() {
        if (rotationAnimating && rotationSource != null) {
            return rotationSource.pos1Y();
        }
        return animating ? animPos1Y : cuboidSelection.pos1Y();
    }

    public double smoothPos1Z() {
        if (rotationAnimating && rotationSource != null) {
            return rotationSource.pos1Z();
        }
        return animating ? animPos1Z : cuboidSelection.pos1Z();
    }

    public int pos2X() {
        if (rotationAnimating && rotationSource != null) {
            return rotationSource.pos2X();
        }
        return animating ? (int) Math.round(animPos2X) : cuboidSelection.pos2X();
    }

    public int pos2Y() {
        if (rotationAnimating && rotationSource != null) {
            return rotationSource.pos2Y();
        }
        return animating ? (int) Math.round(animPos2Y) : cuboidSelection.pos2Y();
    }

    public int pos2Z() {
        if (rotationAnimating && rotationSource != null) {
            return rotationSource.pos2Z();
        }
        return animating ? (int) Math.round(animPos2Z) : cuboidSelection.pos2Z();
    }

    public double smoothPos2X() {
        if (rotationAnimating && rotationSource != null) {
            return rotationSource.pos2X();
        }
        return animating ? animPos2X : cuboidSelection.pos2X();
    }

    public double smoothPos2Y() {
        if (rotationAnimating && rotationSource != null) {
            return rotationSource.pos2Y();
        }
        return animating ? animPos2Y : cuboidSelection.pos2Y();
    }

    public double smoothPos2Z() {
        if (rotationAnimating && rotationSource != null) {
            return rotationSource.pos2Z();
        }
        return animating ? animPos2Z : cuboidSelection.pos2Z();
    }

    public AABB cuboidSelectionBox(Vec3 cameraPosition) {
        AABB box = cuboidSelectionBox();
        return box.move(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
    }

    public AABB cuboidSelectionBox() {
        double minX, minY, minZ, maxX, maxY, maxZ;
        if (rotationAnimating && rotationSource != null) {
            minX = rotationSource.minX();
            minY = rotationSource.minY();
            minZ = rotationSource.minZ();
            maxX = rotationSource.maxX();
            maxY = rotationSource.maxY();
            maxZ = rotationSource.maxZ();
        } else if (animating) {
            minX = animMinX;
            minY = animMinY;
            minZ = animMinZ;
            maxX = animMaxX;
            maxY = animMaxY;
            maxZ = animMaxZ;
        } else {
            minX = cuboidSelection.minX();
            minY = cuboidSelection.minY();
            minZ = cuboidSelection.minZ();
            maxX = cuboidSelection.maxX();
            maxY = cuboidSelection.maxY();
            maxZ = cuboidSelection.maxZ();
        }
        return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
    }

    public SelectionBox currentSelectionBox() {
        if (rotationTarget != null) {
            return new SelectionBox(
                    rotationTarget.minX(), rotationTarget.minY(), rotationTarget.minZ(),
                    rotationTarget.maxX(), rotationTarget.maxY(), rotationTarget.maxZ()
            );
        }
        if (animationTarget != null) {
            return new SelectionBox(
                    animationTarget.minX(),
                    animationTarget.minY(),
                    animationTarget.minZ(),
                    animationTarget.maxX(),
                    animationTarget.maxY(),
                    animationTarget.maxZ()
            );
        }
        return new SelectionBox(
                cuboidSelection.minX(),
                cuboidSelection.minY(),
                cuboidSelection.minZ(),
                cuboidSelection.maxX(),
                cuboidSelection.maxY(),
                cuboidSelection.maxZ()
        );
    }

    public Optional<SelectionFaceHit> hoverHit() {
        return interaction.hoverHit();
    }

    public SelectionFace selectedFace() {
        return interaction.selectedFace();
    }

    public boolean isEmptyHand() {
        return interaction.isEmptyHand();
    }

    public void updateEmptyHand(boolean emptyHand) {
        interaction.updateEmptyHand(emptyHand);
    }

    public void updateHover(Optional<SelectionFaceHit> hoverHit) {
        interaction.updateHover(hoverHit);
    }

    public void selectFace(SelectionFace face) {
        interaction.selectFace(face);
    }

    public float hoverProgress(SelectionFace face) {
        return interaction.hoverProgress(face);
    }

    public float selectedProgress(SelectionFace face) {
        return interaction.selectedProgress(face);
    }

    public void tickInteractionAnimations() {
        interaction.tickAnimations();
    }

    public void resetInteraction() {
        interaction.reset();
    }

    public boolean isRotationAnimating() {
        return rotationAnimating;
    }

    public boolean isSelectionTransformInProgress() {
        return animating || rotationAnimating;
    }

    public void updateRotationHandle(SelectionFace face, long nowNanos) {
        rotationHandles.update(face, nowNanos);
    }

    public float rotationHandleProgress(SelectionFace face) {
        return rotationHandles.progress(face);
    }

    public void resetRotationHandles() {
        rotationHandles.reset();
    }

    public double rotationPivotX() {
        return rotationPivotX;
    }

    public double rotationPivotY() {
        return rotationPivotY;
    }

    public double rotationPivotZ() {
        return rotationPivotZ;
    }

    public double rotationTranslationX() {
        return rotationTranslationX * rotationProgress;
    }

    public double rotationTranslationY() {
        return rotationTranslationY * rotationProgress;
    }

    public double rotationTranslationZ() {
        return rotationTranslationZ * rotationProgress;
    }

    public int rotationAxis() {
        return rotationAxis;
    }

    public float rotationAngleDegrees() {
        return 90.0F * rotationQuarterTurn * rotationProgress;
    }

    public void updateDrawState(boolean aimingAtSelection) {
        if (suppressDrawMode) {
            drawModeActive = false;
            drawTargetProgress = approach(drawTargetProgress, 0.0F, DRAW_COLOR_BLEND_SPEED);
            return;
        }
        boolean keyDown = com.redawn.worldeditpp.bridge.minecraft.client.mode.DrawModeController.isDrawing();
        drawModeActive = keyDown;
        float target = (keyDown && aimingAtSelection) ? 1.0F : 0.0F;
        drawTargetProgress = approach(drawTargetProgress, target, DRAW_COLOR_BLEND_SPEED);
    }

    public void setSuppressDrawMode(boolean suppress) {
        this.suppressDrawMode = suppress;
    }

    public boolean isDrawMode() {
        return drawModeActive;
    }

    public void setFaceMasksHidden(boolean hidden) {
        this.faceMasksHidden = hidden;
    }

    public boolean shouldRenderFaceMasks() {
        return hasCuboidSelection() && !drawModeActive && !faceMasksHidden;
    }

    public float drawColorBlend() {
        return drawTargetProgress;
    }

    public void updateSelectionStyle(boolean emphasized) {
        selectionStyleProgress = approach(
                selectionStyleProgress,
                emphasized ? 1.0F : 0.0F,
                SELECTION_STYLE_BLEND_SPEED
        );
    }

    public float selectionStyleProgress() {
        return selectionStyleProgress;
    }

    public void resetOverlayStyle() {
        drawModeActive = false;
        drawTargetProgress = 0.0F;
        selectionStyleProgress = 1.0F;
    }

    private static float approach(float value, float target, float speed) {
        if (value < target) {
            return Math.min(target, value + speed);
        }
        return Math.max(target, value - speed);
    }

    private double distanceToTarget() {
        if (animationTarget == null) {
            return 0.0D;
        }
        double dx = animMinX - animationTarget.minX();
        double dy = animMinY - animationTarget.minY();
        double dz = animMinZ - animationTarget.minZ();
        double dx2 = animMaxX - animationTarget.maxX();
        double dy2 = animMaxY - animationTarget.maxY();
        double dz2 = animMaxZ - animationTarget.maxZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz + dx2 * dx2 + dy2 * dy2 + dz2 * dz2);
    }

    private static double lerp(double current, double target, double speed) {
        if (Math.abs(target - current) < ANIMATION_SNAP_THRESHOLD) {
            return target;
        }
        return current + (target - current) * speed;
    }

    private static boolean sameSelection(CuboidSelectionPayload first, CuboidSelectionPayload second) {
        return first.visible() == second.visible()
                && first.minX() == second.minX()
                && first.minY() == second.minY()
                && first.minZ() == second.minZ()
                && first.maxX() == second.maxX()
                && first.maxY() == second.maxY()
                && first.maxZ() == second.maxZ();
    }

    private static boolean sameSelectionAndCorners(CuboidSelectionPayload first, CuboidSelectionPayload second) {
        return sameSelection(first, second)
                && first.pos1X() == second.pos1X()
                && first.pos1Y() == second.pos1Y()
                && first.pos1Z() == second.pos1Z()
                && first.pos2X() == second.pos2X()
                && first.pos2Y() == second.pos2Y()
                && first.pos2Z() == second.pos2Z();
    }
}
