package com.redawn.common.selection;

public record SelectionBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

    public SelectionBox withFace(SelectionFace face, double newPlane) {
        return switch (face) {
            case WEST -> new SelectionBox(newPlane, minY, minZ, maxX, maxY, maxZ);
            case EAST -> new SelectionBox(minX, minY, minZ, newPlane, maxY, maxZ);
            case DOWN -> new SelectionBox(minX, newPlane, minZ, maxX, maxY, maxZ);
            case UP -> new SelectionBox(minX, minY, minZ, maxX, newPlane, maxZ);
            case NORTH -> new SelectionBox(minX, minY, newPlane, maxX, maxY, maxZ);
            case SOUTH -> new SelectionBox(minX, minY, minZ, maxX, maxY, newPlane);
        };
    }
}
