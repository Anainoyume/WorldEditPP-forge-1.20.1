package com.redawn.common.gizmo;

public enum GizmoElement {
    X_ARROW,
    Y_ARROW,
    Z_ARROW,
    X_FACE,
    Y_FACE,
    Z_FACE;

    public boolean isArrow() {
        return this == X_ARROW || this == Y_ARROW || this == Z_ARROW;
    }

    public int axisIndex() {
        return switch (this) {
            case X_ARROW -> 0;
            case Y_ARROW -> 1;
            case Z_ARROW -> 2;
            default -> -1;
        };
    }

    /**
     * 面元素的法线轴：X_FACE 位于 yOz 面，Y_FACE 位于 xOz 面，Z_FACE 位于 xOy 面。
     */
    public int normalAxis() {
        return switch (this) {
            case X_FACE -> 0;
            case Y_FACE -> 1;
            case Z_FACE -> 2;
            default -> -1;
        };
    }
}
