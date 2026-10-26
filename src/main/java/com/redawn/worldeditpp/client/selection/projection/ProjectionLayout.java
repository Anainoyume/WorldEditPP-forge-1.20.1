package com.redawn.worldeditpp.client.selection.projection;

import com.redawn.worldeditpp.math.DiscreteRotation;
import com.redawn.worldeditpp.network.SelectionProjectionLimits;

/** Constant-space repeated clipboard geometry, independent of block data and graphics. */
public final class ProjectionLayout {
    private final int[] original;
    private final int[] size;
    private final int[] sourceAxis = {0, 1, 2};
    private final int[] sign = {1, 1, 1};
    private DiscreteRotation orientation = DiscreteRotation.IDENTITY;

    public ProjectionLayout(int x, int y, int z) {
        if (!SelectionProjectionLimits.hasValidVolume(x, y, z)) throw new IllegalArgumentException("Invalid volume");
        original = new int[]{x, y, z};
        size = original.clone();
    }

    public int size(int axis) { return size[axis]; }
    public int tile(int axis) { return original[sourceAxis[axis]]; }
    public long copies() { return (long) (size[0] / tile(0)) * (size[1] / tile(1)) * (size[2] / tile(2)); }
    public DiscreteRotation orientation() { return orientation; }

    public long copiesAfterStretch(int axis, boolean expand) {
        int next = size[axis] + (expand ? tile(axis) : -tile(axis));
        if (next < tile(axis) || !SelectionProjectionLimits.hasValidVolume(
                axis == 0 ? next : size[0], axis == 1 ? next : size[1], axis == 2 ? next : size[2])) return -1;
        return copies() / (size[axis] / tile(axis)) * (next / tile(axis));
    }

    public void stretch(int axis, boolean expand) {
        if (copiesAfterStretch(axis, expand) < 0) throw new IllegalArgumentException("Invalid stretch");
        size[axis] += expand ? tile(axis) : -tile(axis);
    }

    public void transform(int axis, int turn, boolean mirror) {
        if (axis < 0 || axis > 2 || (!mirror && Math.abs(turn) != 1)) throw new IllegalArgumentException("Invalid transform");
        int[] oldSize = size.clone();
        DiscreteRotation step = mirror ? DiscreteRotation.IDENTITY.thenWorldMirror(axis)
                : DiscreteRotation.IDENTITY.thenWorldQuarterTurn(axis, turn);
        var extent = step.apply(oldSize[0], oldSize[1], oldSize[2]);
        size[0] = Math.abs(extent.x()); size[1] = Math.abs(extent.y()); size[2] = Math.abs(extent.z());
        orientation = mirror ? orientation.thenWorldMirror(axis) : orientation.thenWorldQuarterTurn(axis, turn);
        updateAxes();
    }

    private void updateAxes() {
        for (int source = 0; source < 3; source++) {
            var v = orientation.apply(source == 0 ? 1 : 0, source == 1 ? 1 : 0, source == 2 ? 1 : 0);
            int[] components = {v.x(), v.y(), v.z()};
            for (int target = 0; target < 3; target++) if (components[target] != 0) {
                sourceAxis[target] = source; sign[target] = components[target];
            }
        }
    }

    public int sourceCell(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= size[0] || y >= size[1] || z >= size[2]) return -1;
        int a = x % tile(0), b = y % tile(1), c = z % tile(2);
        if (sign[0] < 0) a = tile(0) - 1 - a;
        if (sign[1] < 0) b = tile(1) - 1 - b;
        if (sign[2] < 0) c = tile(2) - 1 - c;
        int sx = sourceAxis[0] == 0 ? a : sourceAxis[1] == 0 ? b : c;
        int sy = sourceAxis[0] == 1 ? a : sourceAxis[1] == 1 ? b : c;
        int sz = sourceAxis[0] == 2 ? a : sourceAxis[1] == 2 ? b : c;
        return sx + original[0] * (sy + original[1] * sz);
    }
}
