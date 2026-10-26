package com.redawn.common.selection;

public final class SelectionOrientation {

    public static boolean isFrontFace(SelectionFace face, SelectionBox box, SelectionVector playerEye, SelectionVector lookDirection) {
        if (isInside(box, playerEye)) {
            return false;
        }
        double dot = lookDirection.x() * face.normalX()
                + lookDirection.y() * face.normalY()
                + lookDirection.z() * face.normalZ();
        return dot <= 0.0D;
    }

    public static boolean isInside(SelectionBox box, SelectionVector playerEye) {
        return playerEye.x() > box.minX() && playerEye.x() < box.maxX()
                && playerEye.y() > box.minY() && playerEye.y() < box.maxY()
                && playerEye.z() > box.minZ() && playerEye.z() < box.maxZ();
    }

    public static boolean isFaceBack(SelectionFace face, SelectionBox box, SelectionVector playerEye, SelectionVector lookDirection) {
        return !isFrontFace(face, box, playerEye, lookDirection);
    }

    private SelectionOrientation() {
    }
}
