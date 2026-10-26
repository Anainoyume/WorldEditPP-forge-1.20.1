package com.redawn.common.selection;

import java.util.Comparator;
import java.util.Optional;

public final class SelectionFaceRaycaster {
    private static final double EPSILON = 1.0E-7D;

    public Optional<SelectionFaceHit> pick(SelectionBox box, SelectionVector origin, SelectionVector direction, boolean backFaces) {
        SelectionVector normalized = direction.normalize();
        boolean inside = contains(origin.x(), box.minX(), box.maxX())
                && contains(origin.y(), box.minY(), box.maxY())
                && contains(origin.z(), box.minZ(), box.maxZ());
        return java.util.Arrays.stream(SelectionFace.values())
                .map(face -> hitFace(box, origin, normalized, face, backFaces, inside))
                .flatMap(Optional::stream)
                .min(Comparator.comparingDouble(SelectionFaceHit::distance));
    }

    private static Optional<SelectionFaceHit> hitFace(SelectionBox box, SelectionVector origin, SelectionVector direction, SelectionFace face, boolean backFaces, boolean inside) {
        double facing = face.normalX() * direction.x() + face.normalY() * direction.y() + face.normalZ() * direction.z();
        if (!inside && (backFaces ? facing <= EPSILON : facing >= -EPSILON)) {
            return Optional.empty();
        }

        double axisDirection = switch (face) {
            case WEST, EAST -> direction.x();
            case DOWN, UP -> direction.y();
            case NORTH, SOUTH -> direction.z();
        };
        if (Math.abs(axisDirection) < EPSILON) {
            return Optional.empty();
        }

        double axisOrigin = switch (face) {
            case WEST, EAST -> origin.x();
            case DOWN, UP -> origin.y();
            case NORTH, SOUTH -> origin.z();
        };
        double distance = (face.plane(box) - axisOrigin) / axisDirection;
        if (distance < 0.0D) {
            return Optional.empty();
        }

        SelectionVector hit = origin.add(direction.scale(distance));
        if (!containsOnFace(box, face, hit)) {
            return Optional.empty();
        }

        return Optional.of(new SelectionFaceHit(face, hit, distance));
    }

    private static boolean containsOnFace(SelectionBox box, SelectionFace face, SelectionVector hit) {
        return switch (face) {
            case WEST, EAST -> contains(hit.y(), box.minY(), box.maxY()) && contains(hit.z(), box.minZ(), box.maxZ());
            case DOWN, UP -> contains(hit.x(), box.minX(), box.maxX()) && contains(hit.z(), box.minZ(), box.maxZ());
            case NORTH, SOUTH -> contains(hit.x(), box.minX(), box.maxX()) && contains(hit.y(), box.minY(), box.maxY());
        };
    }

    private static boolean contains(double value, double min, double max) {
        return value >= min - EPSILON && value <= max + EPSILON;
    }
}
