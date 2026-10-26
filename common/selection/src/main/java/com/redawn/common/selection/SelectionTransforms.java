package com.redawn.common.selection;

import java.util.Optional;

/** Inclusive block bounds, independent of rendering, input devices and networking. */
public final class SelectionTransforms {
    public record RotatedSelection(
            SelectionBox box,
            int pos1X, int pos1Y, int pos1Z,
            int pos2X, int pos2Y, int pos2Z,
            int axis, int quarterTurn
    ) { }

    public static Optional<SelectionBox> nudge(SelectionBox box, SelectionFace face, double scrollDelta,
                                                SelectionVector eye, SelectionVector look) {
        if (scrollDelta == 0) {
            return Optional.empty();
        }
        boolean front = SelectionOrientation.isFrontFace(face, box, eye, look);
        int sign = switch (face) {
            case WEST, DOWN, NORTH -> -1;
            case EAST, UP, SOUTH -> 1;
        };
        int step = (front != (scrollDelta > 0)) ? sign : -sign;
        SelectionBox result = box.withFace(face, face.plane(box) + step);
        return valid(result) ? Optional.of(result) : Optional.empty();
    }

    public static Optional<SelectionBox> resize(SelectionBox box, double scrollDelta) {
        if (scrollDelta == 0) {
            return Optional.empty();
        }
        boolean expand = scrollDelta > 0;
        SelectionBox result = new SelectionBox(
                resizeAxis(box.minX(), box.maxX(), expand, true),
                resizeAxis(box.minY(), box.maxY(), expand, true),
                resizeAxis(box.minZ(), box.maxZ(), expand, true),
                resizeAxis(box.maxX(), box.minX(), expand, false),
                resizeAxis(box.maxY(), box.minY(), expand, false),
                resizeAxis(box.maxZ(), box.minZ(), expand, false));
        return valid(result) && !result.equals(box) ? Optional.of(result) : Optional.empty();
    }

    public static SelectionBox move(SelectionBox box, int dx, int dy, int dz) {
        return new SelectionBox(box.minX() + dx, box.minY() + dy, box.minZ() + dz,
                box.maxX() + dx, box.maxY() + dy, box.maxZ() + dz);
    }

    /** Reflect inclusive cells across the outer boundary of the chosen face, not its cell center. */
    public static SelectionBox mirror(SelectionBox box, SelectionFace face) {
        int axis = axis(face);
        int sign = (int) (face.normalX() + face.normalY() + face.normalZ());
        int distance = exactCoordinate(axis == 0 ? box.maxX() - box.minX() + 1
                : axis == 1 ? box.maxY() - box.minY() + 1 : box.maxZ() - box.minZ() + 1);
        return move(box, axis == 0 ? sign * distance : 0,
                axis == 1 ? sign * distance : 0, axis == 2 ? sign * distance : 0);
    }

    public static RotatedSelection rotate(
            SelectionBox box,
            int pos1X, int pos1Y, int pos1Z,
            int pos2X, int pos2Y, int pos2Z,
            SelectionFace face,
            boolean counterClockwise
    ) {
        int minX = exactCoordinate(box.minX());
        int minY = exactCoordinate(box.minY());
        int minZ = exactCoordinate(box.minZ());
        int maxX = exactCoordinate(box.maxX());
        int maxY = exactCoordinate(box.maxY());
        int maxZ = exactCoordinate(box.maxZ());
        int axis = axis(face);
        int normalSign = (int) (face.normalX() + face.normalY() + face.normalZ());
        int quarterTurn = (counterClockwise ? 1 : -1) * normalSign;
        RotationGrid grid = RotationGrid.create(
                minX, minY, minZ, maxX, maxY, maxZ, axis, quarterTurn);
        int[] pos1 = grid.rotate(pos1X, pos1Y, pos1Z);
        int[] pos2 = grid.rotate(pos2X, pos2Y, pos2Z);
        return new RotatedSelection(
                grid.box(),
                pos1[0], pos1[1], pos1[2], pos2[0], pos2[1], pos2[2],
                axis, quarterTurn
        );
    }

    private record RotationGrid(
            int minX, int minY, int minZ,
            int targetMinX, int targetMinY, int targetMinZ,
            int targetMaxX, int targetMaxY, int targetMaxZ,
            int lengthA, int lengthB,
            int axis, boolean standardPositive
    ) {
        static RotationGrid create(
                int minX, int minY, int minZ,
                int maxX, int maxY, int maxZ,
                int axis, int quarterTurn
        ) {
            int minA;
            int minB;
            int maxA;
            int maxB;
            boolean standardPositive;
            switch (axis) {
                case 0 -> {
                    minA = minY; maxA = maxY; minB = minZ; maxB = maxZ;
                    standardPositive = quarterTurn > 0;
                }
                case 1 -> {
                    minA = minX; maxA = maxX; minB = minZ; maxB = maxZ;
                    standardPositive = quarterTurn < 0;
                }
                case 2 -> {
                    minA = minX; maxA = maxX; minB = minY; maxB = maxY;
                    standardPositive = quarterTurn > 0;
                }
                default -> throw new IllegalArgumentException("Unknown rotation axis: " + axis);
            }
            int lengthA = maxA - minA + 1;
            int lengthB = maxB - minB + 1;
            double centerA = (minA + maxA) * 0.5D;
            double centerB = (minB + maxB) * 0.5D;
            int targetMinA = centeredMin(centerA, lengthB, standardPositive);
            int targetMinB = centeredMin(centerB, lengthA, !standardPositive);
            int targetMaxA = targetMinA + lengthB - 1;
            int targetMaxB = targetMinB + lengthA - 1;

            return switch (axis) {
                case 0 -> new RotationGrid(
                        minX, minY, minZ,
                        minX, targetMinA, targetMinB,
                        maxX, targetMaxA, targetMaxB,
                        lengthA, lengthB, axis, standardPositive);
                case 1 -> new RotationGrid(
                        minX, minY, minZ,
                        targetMinA, minY, targetMinB,
                        targetMaxA, maxY, targetMaxB,
                        lengthA, lengthB, axis, standardPositive);
                case 2 -> new RotationGrid(
                        minX, minY, minZ,
                        targetMinA, targetMinB, minZ,
                        targetMaxA, targetMaxB, maxZ,
                        lengthA, lengthB, axis, standardPositive);
                default -> throw new IllegalArgumentException("Unknown rotation axis: " + axis);
            };
        }

        SelectionBox box() {
            return new SelectionBox(
                    targetMinX, targetMinY, targetMinZ,
                    targetMaxX, targetMaxY, targetMaxZ);
        }

        int[] rotate(int x, int y, int z) {
            int a;
            int b;
            switch (axis) {
                case 0 -> { a = y - minY; b = z - minZ; }
                case 1 -> { a = x - minX; b = z - minZ; }
                case 2 -> { a = x - minX; b = y - minY; }
                default -> throw new IllegalArgumentException("Unknown rotation axis: " + axis);
            }
            int rotatedA = standardPositive ? lengthB - 1 - b : b;
            int rotatedB = standardPositive ? a : lengthA - 1 - a;
            return switch (axis) {
                case 0 -> new int[]{x, targetMinY + rotatedA, targetMinZ + rotatedB};
                case 1 -> new int[]{targetMinX + rotatedA, y, targetMinZ + rotatedB};
                case 2 -> new int[]{targetMinX + rotatedA, targetMinY + rotatedB, z};
                default -> throw new IllegalArgumentException("Unknown rotation axis: " + axis);
            };
        }

        private static int centeredMin(double center, int length, boolean roundDown) {
            double exact = center - (length - 1) * 0.5D;
            return (int) (roundDown ? Math.floor(exact) : Math.ceil(exact));
        }
    }

    private static int axis(SelectionFace face) {
        return switch (face) {
            case WEST, EAST -> 0;
            case DOWN, UP -> 1;
            case NORTH, SOUTH -> 2;
        };
    }

    private static int exactCoordinate(double coordinate) {
        if (!Double.isFinite(coordinate) || coordinate != Math.rint(coordinate)
                || coordinate < Integer.MIN_VALUE || coordinate > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Selection coordinate must be an integer: " + coordinate);
        }
        return (int) coordinate;
    }

    private static double resizeAxis(double value, double opposite, boolean expand, boolean min) {
        if (expand) {
            return min ? value - 1 : value + 1;
        }
        double span = min ? opposite - value : value - opposite;
        if (span == 0) {
            return value;
        }
        if (span == 1) {
            return min ? value : value - 1;
        }
        return min ? value + 1 : value - 1;
    }

    private static boolean valid(SelectionBox box) {
        return box.minX() <= box.maxX() && box.minY() <= box.maxY() && box.minZ() <= box.maxZ();
    }

    private SelectionTransforms() { }
}
