package com.redawn.common.gizmo;

/** Shared geometric style for the axis gizmo. Platform renderers own colours and GPU state. */
public final class GizmoStyle {
    public static final float ARROW_LENGTH = 1.50F;
    public static final float FACE_SIZE = 0.50F;
    public static final float FACE_HALF = FACE_SIZE * 0.5F;

    public static final float VISIBLE_EDGE_ALPHA = 0.72F;
    public static final float VISIBLE_FILL_ALPHA = 0.14F;
    public static final float OCCLUDED_EDGE_ALPHA = 0.30F;
    public static final float OCCLUDED_FILL_ALPHA = 0.06F;

    private GizmoStyle() { }
}
