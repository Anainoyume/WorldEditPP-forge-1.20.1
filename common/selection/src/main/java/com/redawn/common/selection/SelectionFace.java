package com.redawn.common.selection;

public enum SelectionFace {
    WEST(-1, 0, 0),
    EAST(1, 0, 0),
    DOWN(0, -1, 0),
    UP(0, 1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1);

    private final int normalX;
    private final int normalY;
    private final int normalZ;

    SelectionFace(int normalX, int normalY, int normalZ) {
        this.normalX = normalX;
        this.normalY = normalY;
        this.normalZ = normalZ;
    }

    public double plane(SelectionBox box) {
        return switch (this) {
            case WEST -> box.minX();
            case EAST -> box.maxX();
            case DOWN -> box.minY();
            case UP -> box.maxY();
            case NORTH -> box.minZ();
            case SOUTH -> box.maxZ();
        };
    }

    public double normalX() {
        return normalX;
    }

    public double normalY() {
        return normalY;
    }

    public double normalZ() {
        return normalZ;
    }
}
