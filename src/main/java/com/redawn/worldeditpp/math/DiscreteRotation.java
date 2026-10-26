package com.redawn.worldeditpp.math;

import java.util.Objects;

/** Exact signed-permutation transform: 24 rotations, or 48 orientations including reflections. */
public final class DiscreteRotation {
    public static final DiscreteRotation IDENTITY = new DiscreteRotation(
            1, 0, 0,
            0, 1, 0,
            0, 0, 1);

    private static final DiscreteRotation QUARTER_X = new DiscreteRotation(
            1, 0, 0,
            0, 0, -1,
            0, 1, 0);
    private static final DiscreteRotation QUARTER_Y = new DiscreteRotation(
            0, 0, 1,
            0, 1, 0,
            -1, 0, 0);
    private static final DiscreteRotation QUARTER_Z = new DiscreteRotation(
            0, -1, 0,
            1, 0, 0,
            0, 0, 1);

    private final int m00;
    private final int m01;
    private final int m02;
    private final int m10;
    private final int m11;
    private final int m12;
    private final int m20;
    private final int m21;
    private final int m22;

    private DiscreteRotation(
            int m00, int m01, int m02,
            int m10, int m11, int m12,
            int m20, int m21, int m22
    ) {
        this.m00 = m00;
        this.m01 = m01;
        this.m02 = m02;
        this.m10 = m10;
        this.m11 = m11;
        this.m12 = m12;
        this.m20 = m20;
        this.m21 = m21;
        this.m22 = m22;
    }

    /** Applies a new quarter turn around a fixed world axis after this rotation. */
    public DiscreteRotation thenWorldQuarterTurn(int axis, int quarterTurns) {
        return quarterTurn(axis, quarterTurns).multiply(this);
    }

    /** Reflects a fixed world axis after the accumulated transform. */
    public DiscreteRotation thenWorldMirror(int axis) {
        if (axis < 0 || axis > 2) throw new IllegalArgumentException("Unknown mirror axis: " + axis);
        return new DiscreteRotation(
                axis == 0 ? -1 : 1, 0, 0,
                0, axis == 1 ? -1 : 1, 0,
                0, 0, axis == 2 ? -1 : 1).multiply(this);
    }

    public Vector apply(Vector vector) {
        return apply(vector.x(), vector.y(), vector.z());
    }

    public Vector apply(int x, int y, int z) {
        return new Vector(
                m00 * x + m01 * y + m02 * z,
                m10 * x + m11 * y + m12 * z,
                m20 * x + m21 * y + m22 * z);
    }

    /** Returns the transformed world-Y sign, or zero when the vector becomes horizontal. */
    public int transformVerticalSign(int localYSign) {
        if (Math.abs(localYSign) != 1) {
            throw new IllegalArgumentException("Vertical sign must be +1 or -1");
        }
        Vector transformed = apply(0, localYSign, 0);
        return transformed.x() == 0 && transformed.z() == 0 ? transformed.y() : 0;
    }

    public int determinant() {
        return m00 * (m11 * m22 - m12 * m21)
                - m01 * (m10 * m22 - m12 * m20)
                + m02 * (m10 * m21 - m11 * m20);
    }

    private DiscreteRotation multiply(DiscreteRotation right) {
        return new DiscreteRotation(
                m00 * right.m00 + m01 * right.m10 + m02 * right.m20,
                m00 * right.m01 + m01 * right.m11 + m02 * right.m21,
                m00 * right.m02 + m01 * right.m12 + m02 * right.m22,
                m10 * right.m00 + m11 * right.m10 + m12 * right.m20,
                m10 * right.m01 + m11 * right.m11 + m12 * right.m21,
                m10 * right.m02 + m11 * right.m12 + m12 * right.m22,
                m20 * right.m00 + m21 * right.m10 + m22 * right.m20,
                m20 * right.m01 + m21 * right.m11 + m22 * right.m21,
                m20 * right.m02 + m21 * right.m12 + m22 * right.m22);
    }

    private static DiscreteRotation quarterTurn(int axis, int quarterTurns) {
        DiscreteRotation positive = switch (axis) {
            case 0 -> QUARTER_X;
            case 1 -> QUARTER_Y;
            case 2 -> QUARTER_Z;
            default -> throw new IllegalArgumentException("Unknown rotation axis: " + axis);
        };
        int normalized = Math.floorMod(quarterTurns, 4);
        DiscreteRotation result = IDENTITY;
        for (int index = 0; index < normalized; index++) {
            result = positive.multiply(result);
        }
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DiscreteRotation rotation)) return false;
        return m00 == rotation.m00 && m01 == rotation.m01 && m02 == rotation.m02
                && m10 == rotation.m10 && m11 == rotation.m11 && m12 == rotation.m12
                && m20 == rotation.m20 && m21 == rotation.m21 && m22 == rotation.m22;
    }

    @Override
    public int hashCode() {
        return Objects.hash(m00, m01, m02, m10, m11, m12, m20, m21, m22);
    }

    @Override
    public String toString() {
        return "[[" + m00 + ',' + m01 + ',' + m02 + "],["
                + m10 + ',' + m11 + ',' + m12 + "],["
                + m20 + ',' + m21 + ',' + m22 + "]]";
    }

    public record Vector(int x, int y, int z) {
        public Vector cross(Vector other) {
            return new Vector(
                    y * other.z - z * other.y,
                    z * other.x - x * other.z,
                    x * other.y - y * other.x);
        }

        public Vector negate() {
            return new Vector(-x, -y, -z);
        }
    }
}
