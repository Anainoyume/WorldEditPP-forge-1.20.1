package com.redawn.worldeditpp.client.draw3d;

public sealed interface DrawPrimitive {

    float DEFAULT_EDGE_THICKNESS = 0.05F;
    float[] DEFAULT_COLOR = {0.88F, 0.82F, 0.46F, 0.90F};

    // Shared by rendering and ray picking so the visible arrow and its hit shape stay identical.
    double ARROW_SHAFT_RADIUS = 0.05;
    double ARROW_HEAD_RADIUS = 0.17;
    double ARROW_HEAD_HEIGHT = 0.30;

    enum Type { LINE, CONE, CYLINDER, ARROW, SQUARE_FACE }

    DrawDirection direction();
    Type type();

    default float[] color() { return DEFAULT_COLOR; }

    record Line(double sx, double sy, double sz, DrawDirection direction, double length, double thickness)
            implements DrawPrimitive {
        @Override public Type type() { return Type.LINE; }
    }

    record Cone(double bx, double by, double bz, DrawDirection direction, double radius, double height)
            implements DrawPrimitive {
        @Override public Type type() { return Type.CONE; }
    }

    record Cylinder(double bx, double by, double bz, DrawDirection direction, double radius, double height)
            implements DrawPrimitive {
        @Override public Type type() { return Type.CYLINDER; }
    }

    record Arrow(double sx, double sy, double sz, DrawDirection direction, double totalLength)
            implements DrawPrimitive {
        @Override public Type type() { return Type.ARROW; }
    }

    record SquareFace(double cx, double cy, double cz, DrawDirection direction, double sideLength)
            implements DrawPrimitive {
        @Override public Type type() { return Type.SQUARE_FACE; }
    }
}
