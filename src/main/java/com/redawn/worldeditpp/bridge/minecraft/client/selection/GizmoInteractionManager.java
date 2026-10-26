package com.redawn.worldeditpp.bridge.minecraft.client.selection;

import com.redawn.common.selection.AxisDragMath;
import com.redawn.common.gizmo.GizmoElement;
import com.redawn.common.gizmo.GizmoOrientationAnimator;

import com.redawn.worldeditpp.client.draw3d.DrawDirection;
import com.redawn.worldeditpp.client.draw3d.DrawHitResult;
import com.redawn.worldeditpp.client.draw3d.DrawPrimitive;
import com.redawn.worldeditpp.client.draw3d.DrawRayCaster;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public final class GizmoInteractionManager {
    private static final float ARROW_TOTAL = 1.50F;
    private static final float FACE_SIDE = 0.50F;
    private static final float FACE_HALF = FACE_SIDE * 0.5F;
    private static final float COLOR_BLEND_SPEED = 0.10F;

    private static final float[] DEFAULT_X = {0.784F, 0.0F, 0.0F};
    private static final float[] DEFAULT_Y = {0.0F, 0.784F, 0.0F};
    private static final float[] DEFAULT_Z = {0.0F, 0.0F, 0.784F};
    private static final float[] FACE_DEFAULT = {0.88F, 0.88F, 0.92F};
    private static final float[] HOVER_COLOR = {0.88F, 0.82F, 0.46F};
    private static final float[] HOLDED_COLOR = {0.88F, 0.46F, 0.88F};

    private enum State { NO_HIT, HOVER, HOLDED }

    private static final class ElementState {
        State state = State.NO_HIT;
        float colorR, colorG, colorB;
        float targetR, targetG, targetB;
        float blend;

        ElementState(float r, float g, float b) {
            this.colorR = r; this.colorG = g; this.colorB = b;
            this.targetR = r; this.targetG = g; this.targetB = b;
            this.blend = 1.0F;
        }
    }

    private final Map<GizmoElement, ElementState> states = new EnumMap<>(GizmoElement.class);
    private final DrawRayCaster rayCaster = new DrawRayCaster(1.5);
    private final GizmoOrientationAnimator orientation = new GizmoOrientationAnimator();

    private GizmoElement hoveredElement;
    private GizmoElement holdedElement;
    private double gizmoCx, gizmoCy, gizmoCz;
    private float gizmoScale;

    private boolean dragActive;
    private double dragRefX, dragRefY, dragRefZ;
    private int dragPlane1Axis, dragPlane2Axis = -1;
    private double dragPlane1Constant, dragPlane2Constant;
    private int dragLastDx, dragLastDy, dragLastDz;
    private boolean dragCancelledByDistance;

    public GizmoInteractionManager() {
        states.put(GizmoElement.X_ARROW, new ElementState(DEFAULT_X[0], DEFAULT_X[1], DEFAULT_X[2]));
        states.put(GizmoElement.Y_ARROW, new ElementState(DEFAULT_Y[0], DEFAULT_Y[1], DEFAULT_Y[2]));
        states.put(GizmoElement.Z_ARROW, new ElementState(DEFAULT_Z[0], DEFAULT_Z[1], DEFAULT_Z[2]));
        states.put(GizmoElement.X_FACE, new ElementState(FACE_DEFAULT[0], FACE_DEFAULT[1], FACE_DEFAULT[2]));
        states.put(GizmoElement.Y_FACE, new ElementState(FACE_DEFAULT[0], FACE_DEFAULT[1], FACE_DEFAULT[2]));
        states.put(GizmoElement.Z_FACE, new ElementState(FACE_DEFAULT[0], FACE_DEFAULT[1], FACE_DEFAULT[2]));
    }

    public GizmoElement hoveredElement() { return hoveredElement; }
    public GizmoElement holdedElement() { return holdedElement; }
    public boolean isHovered() { return hoveredElement != null; }
    public boolean isHolded() { return holdedElement != null; }
    public boolean isOrientationTransitioning() { return orientation.isTransitioning(); }

    public float axisOrientation(int axis) {
        return switch (axis) {
            case 0 -> orientation.x();
            case 1 -> orientation.y();
            case 2 -> orientation.z();
            default -> throw new IllegalArgumentException("Unknown axis: " + axis);
        };
    }

    public DrawDirection axisDirection(int axis) {
        return axisDirection(axis, axisOrientation(axis));
    }

    public float[] getElementColor(GizmoElement e) {
        ElementState s = states.get(e);
        return new float[]{s.colorR, s.colorG, s.colorB};
    }

    public void updateHover(Camera camera, double cx, double cy, double cz, float scale) {
        this.gizmoCx = cx;
        this.gizmoCy = cy;
        this.gizmoCz = cz;
        this.gizmoScale = scale;

        Vec3 eye = camera.getPosition();
        if (holdedElement != null) {
            return;
        }
        updateOrientation(eye);
        if (orientation.isTransitioning()) {
            clearHover();
            return;
        }

        Vec3 look = new Vec3(camera.getLookVector());
        GizmoElement newHover = pick(eye, look);

        if (newHover != hoveredElement) {
            if (hoveredElement != null) transitionTo(hoveredElement, State.NO_HIT);
            hoveredElement = newHover;
            if (hoveredElement != null) transitionTo(hoveredElement, State.HOVER);
        }
    }

    public void pressRight(Camera camera) {
        if (orientation.isTransitioning() || hoveredElement == null || holdedElement != null) return;
        holdedElement = hoveredElement;
        transitionTo(holdedElement, State.HOLDED);
        hoveredElement = null;
        beginDrag(camera.getPosition(), new Vec3(camera.getLookVector()));
    }

    public void releaseRight(Camera camera) {
        if (holdedElement == null) return;
        GizmoElement released = holdedElement;
        holdedElement = null;
        dragActive = false;

        Vec3 eye = camera.getPosition();
        updateOrientation(eye);
        if (orientation.isTransitioning()) {
            transitionTo(released, State.NO_HIT);
            clearHover();
            return;
        }
        Vec3 look = new Vec3(camera.getLookVector());
        GizmoElement currentHit = pick(eye, look);

        if (currentHit == released) {
            transitionTo(released, State.HOVER);
            hoveredElement = released;
        } else {
            transitionTo(released, State.NO_HIT);
            if (currentHit != null) {
                hoveredElement = currentHit;
                transitionTo(currentHit, State.HOVER);
            }
        }
    }

    public void reset() {
        clearHover();
        if (holdedElement != null) {
            transitionTo(holdedElement, State.NO_HIT);
            holdedElement = null;
        }
        dragActive = false;
    }

    private static double dragDistanceLimit() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null) {
            return AxisDragMath.MAX_PLAYER_DISTANCE;
        }
        // Match the wand's middle-click reach: the effective client render distance, not
        // a fixed 100-block sphere. A chunk is 16 blocks; the server render cap is already
        // included by getEffectiveRenderDistance().
        int chunks = Math.max(1, minecraft.options.getEffectiveRenderDistance());
        return chunks * 16.0D;
    }

    /**
     * 拖拽期间每帧调用，返回要应用的三轴整数格位移；无变化/已自动取消时返回 null。
     */
    public DragMove updateDrag(Camera camera, double centerX, double centerY, double centerZ) {
        if (!dragActive || holdedElement == null) return null;
        Vec3 eye = camera.getPosition();
        if (AxisDragMath.exceedsPlayerDistance(eye.x, eye.y, eye.z,
                centerX, centerY, centerZ, dragDistanceLimit())) {
            cancelHold();
            dragCancelledByDistance = true;
            return null;
        }
        Vec3 look = new Vec3(camera.getLookVector());
        double[] hit = dragPlane2Axis >= 0
                ? AxisDragMath.firstHit(dragPlane1Axis, dragPlane1Constant, dragPlane2Axis, dragPlane2Constant,
                        eye.x, eye.y, eye.z, look.x, look.y, look.z)
                : AxisDragMath.firstHit(dragPlane1Axis, dragPlane1Constant,
                        eye.x, eye.y, eye.z, look.x, look.y, look.z);
        if (hit == null) return null;

        int dx = 0, dy = 0, dz = 0;
        if (holdedElement.isArrow()) {
            int axis = holdedElement.axisIndex();
            int delta = (int) Math.round(AxisDragMath.projectedDelta(axis, hit[0], hit[1], hit[2], dragRefX, dragRefY, dragRefZ));
            if (axis == 0) {
                dx = delta;
            } else if (axis == 1) {
                dy = delta;
            } else {
                dz = delta;
            }
        } else {
            for (int axis : AxisDragMath.faceMoveAxes(holdedElement.normalAxis())) {
                int delta = (int) Math.round(AxisDragMath.projectedDelta(axis, hit[0], hit[1], hit[2], dragRefX, dragRefY, dragRefZ));
                if (axis == 0) {
                    dx = delta;
                } else if (axis == 1) {
                    dy = delta;
                } else {
                    dz = delta;
                }
            }
        }

        if (dx == dragLastDx && dy == dragLastDy && dz == dragLastDz) return null;
        if (AxisDragMath.exceedsPerUpdateDistance(dx, dy, dz, dragLastDx, dragLastDy, dragLastDz)) return null;
        dragLastDx = dx;
        dragLastDy = dy;
        dragLastDz = dz;
        return new DragMove(dx, dy, dz);
    }

    public record DragMove(int dx, int dy, int dz) {}

    public boolean consumeDistanceCancel() {
        boolean cancelled = dragCancelledByDistance;
        dragCancelledByDistance = false;
        return cancelled;
    }

    private void beginDrag(Vec3 eye, Vec3 look) {
        if (holdedElement.isArrow()) {
            int axis = holdedElement.axisIndex();
            dragPlane1Axis = AxisDragMath.plane1ConstantAxis(axis);
            dragPlane2Axis = AxisDragMath.plane2ConstantAxis(axis);
            dragPlane1Constant = AxisDragMath.axisComponent(dragPlane1Axis, gizmoCx, gizmoCy, gizmoCz);
            dragPlane2Constant = AxisDragMath.axisComponent(dragPlane2Axis, gizmoCx, gizmoCy, gizmoCz);
        } else {
            dragPlane1Axis = holdedElement.normalAxis();
            dragPlane2Axis = -1;
            dragPlane1Constant = AxisDragMath.axisComponent(dragPlane1Axis, gizmoCx, gizmoCy, gizmoCz);
        }

        double[] hit = dragPlane2Axis >= 0
                ? AxisDragMath.firstHit(dragPlane1Axis, dragPlane1Constant, dragPlane2Axis, dragPlane2Constant,
                        eye.x, eye.y, eye.z, look.x, look.y, look.z)
                : AxisDragMath.firstHit(dragPlane1Axis, dragPlane1Constant,
                        eye.x, eye.y, eye.z, look.x, look.y, look.z);
        dragActive = hit != null;
        if (!dragActive) return;

        dragRefX = hit[0];
        dragRefY = hit[1];
        dragRefZ = hit[2];
        dragLastDx = 0;
        dragLastDy = 0;
        dragLastDz = 0;
    }

    private void cancelHold() {
        dragActive = false;
        if (holdedElement != null) {
            transitionTo(holdedElement, State.NO_HIT);
            holdedElement = null;
        }
    }

    public void tickAnimations() {
        for (Map.Entry<GizmoElement, ElementState> entry : states.entrySet()) {
            ElementState s = entry.getValue();
            if (s.blend >= 1.0F) continue;
            s.blend = Math.min(1.0F, s.blend + COLOR_BLEND_SPEED);
            s.colorR = lerp(s.colorR, s.targetR, COLOR_BLEND_SPEED);
            s.colorG = lerp(s.colorG, s.targetG, COLOR_BLEND_SPEED);
            s.colorB = lerp(s.colorB, s.targetB, COLOR_BLEND_SPEED);
            if (s.blend >= 1.0F) {
                s.colorR = s.targetR;
                s.colorG = s.targetG;
                s.colorB = s.targetB;
            }
        }
    }

    private void transitionTo(GizmoElement e, State targetState) {
        ElementState s = states.get(e);
        s.state = targetState;
        s.blend = 0.0F;
        s.targetR = targetColor(targetState, e)[0];
        s.targetG = targetColor(targetState, e)[1];
        s.targetB = targetColor(targetState, e)[2];
    }

    private static float[] targetColor(State state, GizmoElement e) {
        return switch (state) {
            case HOVER -> HOVER_COLOR;
            case HOLDED -> HOLDED_COLOR;
            case NO_HIT -> defaultColor(e);
        };
    }

    private static float[] defaultColor(GizmoElement e) {
        return switch (e) {
            case X_ARROW -> DEFAULT_X;
            case Y_ARROW -> DEFAULT_Y;
            case Z_ARROW -> DEFAULT_Z;
            default -> FACE_DEFAULT;
        };
    }

    private GizmoElement pick(Vec3 eye, Vec3 look) {
        float x = orientation.x();
        float y = orientation.y();
        float z = orientation.z();
        List<DrawPrimitive> arrowPrimitives = List.of(
                new DrawPrimitive.Arrow(0, 0, 0, axisDirection(0, x), ARROW_TOTAL * Math.abs(x)),
                new DrawPrimitive.Arrow(0, 0, 0, axisDirection(1, y), ARROW_TOTAL * Math.abs(y)),
                new DrawPrimitive.Arrow(0, 0, 0, axisDirection(2, z), ARROW_TOTAL * Math.abs(z))
        );
        List<DrawPrimitive> facePrimitives = List.of(
                new DrawPrimitive.SquareFace(0, FACE_HALF * y, FACE_HALF * z, DrawDirection.EAST, FACE_SIDE),
                new DrawPrimitive.SquareFace(FACE_HALF * x, 0, FACE_HALF * z, DrawDirection.UP, FACE_SIDE),
                new DrawPrimitive.SquareFace(FACE_HALF * x, FACE_HALF * y, 0, DrawDirection.SOUTH, FACE_SIDE)
        );
        double s = gizmoScale > 0.0F ? gizmoScale : 1.0F;
        double localEx = (eye.x - gizmoCx) / s;
        double localEy = (eye.y - gizmoCy) / s;
        double localEz = (eye.z - gizmoCz) / s;
        double localDx = look.x / s;
        double localDy = look.y / s;
        double localDz = look.z / s;

        Optional<DrawHitResult> arrowHit = rayCaster.pick(arrowPrimitives, localEx, localEy, localEz, localDx, localDy, localDz);
        if (arrowHit.isPresent()) {
            int idx = arrowPrimitives.indexOf(arrowHit.get().primitive());
            if (idx >= 0) return GizmoElement.values()[idx];
        }
        Optional<DrawHitResult> faceHit = rayCaster.pick(facePrimitives, localEx, localEy, localEz, localDx, localDy, localDz);
        if (faceHit.isPresent()) {
            int idx = facePrimitives.indexOf(faceHit.get().primitive());
            if (idx >= 0) return GizmoElement.values()[idx + 3];
        }
        return null;
    }

    private void clearHover() {
        if (hoveredElement != null) {
            transitionTo(hoveredElement, State.NO_HIT);
            hoveredElement = null;
        }
    }

    private void updateOrientation(Vec3 eye) {
        orientation.update(
                eye.x - gizmoCx,
                eye.y - gizmoCy,
                eye.z - gizmoCz,
                System.nanoTime()
        );
    }

    private static DrawDirection axisDirection(int axis, float orientation) {
        boolean positive = orientation >= 0.0F;
        return switch (axis) {
            case 0 -> positive ? DrawDirection.EAST : DrawDirection.WEST;
            case 1 -> positive ? DrawDirection.UP : DrawDirection.DOWN;
            case 2 -> positive ? DrawDirection.SOUTH : DrawDirection.NORTH;
            default -> throw new IllegalArgumentException("Unknown axis: " + axis);
        };
    }

    private static float lerp(float current, float target, float speed) {
        if (Math.abs(target - current) < 0.001F) return target;
        return current + (target - current) * Math.min(1.0F, speed * 2.0F);
    }
}
