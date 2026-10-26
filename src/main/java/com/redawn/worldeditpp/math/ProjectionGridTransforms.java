package com.redawn.worldeditpp.math;

/** Integer grid mappings shared by client projection previews and server clipboard buffers. */
public final class ProjectionGridTransforms {
    public static int[] mirrorCoordinates(int[] source, int axis, int sizeX, int sizeY, int sizeZ) {
        if (source.length != 3 || axis < 0 || axis > 2) {
            throw new IllegalArgumentException("Invalid projection grid reflection");
        }
        int[] result = source.clone();
        int size = axis == 0 ? sizeX : axis == 1 ? sizeY : sizeZ;
        result[axis] = size - 1 - source[axis];
        return result;
    }

    public static int stretchSourceCoordinate(
            int targetCoordinate, int oldSize, int unitSize, boolean positive, boolean expand
    ) {
        if (expand) {
            return positive
                    ? (targetCoordinate < oldSize ? targetCoordinate : targetCoordinate - oldSize)
                    : (targetCoordinate < unitSize ? targetCoordinate : targetCoordinate - unitSize);
        }
        return positive ? targetCoordinate : targetCoordinate + unitSize;
    }

    public static int[] rotateCoordinates(int[] source, int axis, int quarterTurn,
                                          int sizeX, int sizeY, int sizeZ) {
        if (source.length != 3 || axis < 0 || axis > 2 || Math.abs(quarterTurn) != 1) {
            throw new IllegalArgumentException("Invalid projection grid rotation");
        }
        int x = source[0], y = source[1], z = source[2];
        return switch (axis) {
            case 0 -> quarterTurn > 0
                    ? new int[]{x, sizeZ - 1 - z, y}
                    : new int[]{x, z, sizeY - 1 - y};
            case 1 -> quarterTurn < 0
                    ? new int[]{sizeZ - 1 - z, y, x}
                    : new int[]{z, y, sizeX - 1 - x};
            case 2 -> quarterTurn > 0
                    ? new int[]{sizeY - 1 - y, x, z}
                    : new int[]{y, sizeX - 1 - x, z};
            default -> throw new IllegalStateException();
        };
    }

    private ProjectionGridTransforms() { }
}
