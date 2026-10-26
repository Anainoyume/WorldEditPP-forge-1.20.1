package com.redawn.common.selection;

public record SelectionVector(double x, double y, double z) {
    public static final SelectionVector ZERO = new SelectionVector(0.0D, 0.0D, 0.0D);

    public SelectionVector add(SelectionVector other) {
        return new SelectionVector(x + other.x, y + other.y, z + other.z);
    }

    public SelectionVector scale(double scale) {
        return new SelectionVector(x * scale, y * scale, z * scale);
    }

    public SelectionVector normalize() {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0E-7D) {
            return ZERO;
        }
        return new SelectionVector(x / length, y / length, z / length);
    }
}
