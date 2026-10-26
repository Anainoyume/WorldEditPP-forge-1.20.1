package com.redawn.worldeditpp.client.draw3d;

import java.util.List;
import java.util.Optional;

public final class DrawRayCaster {
    private static final double EPSILON = 1.0E-7;
    private final double tolerance;

    public DrawRayCaster() {
        this(1.0);
    }

    public DrawRayCaster(double tolerance) {
        this.tolerance = tolerance;
    }

    public Optional<DrawHitResult> pick(List<DrawPrimitive> primitives, double ox, double oy, double oz,
                                         double dx, double dy, double dz) {
        DrawHitResult best = null;
        double bestDist = Double.MAX_VALUE;
        for (DrawPrimitive p : primitives) {
            double dist = hitDistance(p, ox, oy, oz, dx, dy, dz);
            if (dist > 0.0 && dist < bestDist) {
                bestDist = dist;
                double hx = ox + dx * dist;
                double hy = oy + dy * dist;
                double hz = oz + dz * dist;
                best = new DrawHitResult(p, hx, hy, hz, dist);
            }
        }
        return Optional.ofNullable(best);
    }

    private double hitDistance(DrawPrimitive p, double ox, double oy, double oz, double dx, double dy, double dz) {
                if (p instanceof DrawPrimitive.Line l) return hitLine(l, ox, oy, oz, dx, dy, dz);
        if (p instanceof DrawPrimitive.Cone c) return hitCone(c, ox, oy, oz, dx, dy, dz);
        if (p instanceof DrawPrimitive.Cylinder c) return hitCylinder(c, ox, oy, oz, dx, dy, dz);
        if (p instanceof DrawPrimitive.Arrow a) return hitArrow(a, ox, oy, oz, dx, dy, dz);
        if (p instanceof DrawPrimitive.SquareFace f) return hitSquareFace(f, ox, oy, oz, dx, dy, dz);
        throw new IllegalArgumentException("Unknown primitive");
    }

    // ---- Line: cylinder-like segment intersection ----

    private double hitLine(DrawPrimitive.Line l, double ox, double oy, double oz, double dx, double dy, double dz) {
        DrawDirection dir = l.direction();
        double endX = l.sx() + dir.normalX() * l.length();
        double endY = l.sy() + dir.normalY() * l.length();
        double endZ = l.sz() + dir.normalZ() * l.length();
        return intersectInfiniteCylinder(
                l.sx(), l.sy(), l.sz(), endX, endY, endZ, l.thickness() * 0.5 * tolerance,
                ox, oy, oz, dx, dy, dz
        );
    }

    // ---- Cone intersection (approximate as bounding cylinder of same radius) ----

    private double hitCone(DrawPrimitive.Cone c, double ox, double oy, double oz, double dx, double dy, double dz) {
        DrawDirection dir = c.direction();
        double tipX = c.bx() + dir.normalX() * c.height();
        double tipY = c.by() + dir.normalY() * c.height();
        double tipZ = c.bz() + dir.normalZ() * c.height();
        return intersectInfiniteCylinder(
                c.bx(), c.by(), c.bz(), tipX, tipY, tipZ, c.radius() * tolerance,
                ox, oy, oz, dx, dy, dz
        );
    }

    // ---- Cylinder intersection ----

    private double hitCylinder(DrawPrimitive.Cylinder c, double ox, double oy, double oz, double dx, double dy, double dz) {
        DrawDirection dir = c.direction();
        double topX = c.bx() + dir.normalX() * c.height();
        double topY = c.by() + dir.normalY() * c.height();
        double topZ = c.bz() + dir.normalZ() * c.height();
        return intersectInfiniteCylinder(
                c.bx(), c.by(), c.bz(), topX, topY, topZ, c.radius() * tolerance,
                ox, oy, oz, dx, dy, dz
        );
    }

    // ---- Arrow: union of shaft cylinder + head cone ----

    private double hitArrow(DrawPrimitive.Arrow a, double ox, double oy, double oz, double dx, double dy, double dz) {
        DrawDirection dir = a.direction();
        double headLen = Math.min(DrawPrimitive.ARROW_HEAD_HEIGHT, a.totalLength());
        double shaftLen = Math.max(0.0, a.totalLength() - headLen);
        double best = Double.MAX_VALUE;

        if (shaftLen > 0.0) {
            double d1 = intersectInfiniteCylinder(
                    a.sx(), a.sy(), a.sz(),
                    a.sx() + dir.normalX() * shaftLen,
                    a.sy() + dir.normalY() * shaftLen,
                    a.sz() + dir.normalZ() * shaftLen,
                    DrawPrimitive.ARROW_SHAFT_RADIUS * tolerance, ox, oy, oz, dx, dy, dz);
            if (d1 > 0.0 && d1 < best) best = d1;
        }

        double hx = a.sx() + dir.normalX() * shaftLen;
        double hy = a.sy() + dir.normalY() * shaftLen;
        double hz = a.sz() + dir.normalZ() * shaftLen;
        double d2 = intersectInfiniteCylinder(
                hx, hy, hz,
                hx + dir.normalX() * headLen,
                hy + dir.normalY() * headLen,
                hz + dir.normalZ() * headLen,
                DrawPrimitive.ARROW_HEAD_RADIUS * tolerance, ox, oy, oz, dx, dy, dz);
        if (d2 > 0.0 && d2 < best) best = d2;

        return best == Double.MAX_VALUE ? -1.0 : best;
    }

    // ---- Square face: plane intersection within bounds ----

    private double hitSquareFace(DrawPrimitive.SquareFace f, double ox, double oy, double oz, double dx, double dy, double dz) {
        DrawDirection n = f.direction();
        double dot = n.normalX() * dx + n.normalY() * dy + n.normalZ() * dz;
        if (Math.abs(dot) < EPSILON) return -1.0;

        double axisOrigin = n.normalX() * ox + n.normalY() * oy + n.normalZ() * oz;
        double axisPlane = n.normalX() * f.cx() + n.normalY() * f.cy() + n.normalZ() * f.cz();
        double dist = (axisPlane - axisOrigin) / dot;
        if (dist < 0.0) return -1.0;

        double hx = ox + dx * dist;
        double hy = oy + dy * dist;
        double hz = oz + dz * dist;
        double half = f.sideLength() * 0.5 + 0.05 * tolerance;

        if (n.isXAxis()) {
            if (hy < f.cy() - half || hy > f.cy() + half) return -1.0;
            if (hz < f.cz() - half || hz > f.cz() + half) return -1.0;
        } else if (n.isYAxis()) {
            if (hx < f.cx() - half || hx > f.cx() + half) return -1.0;
            if (hz < f.cz() - half || hz > f.cz() + half) return -1.0;
        } else {
            if (hx < f.cx() - half || hx > f.cx() + half) return -1.0;
            if (hy < f.cy() - half || hy > f.cy() + half) return -1.0;
        }
        return dist;
    }

    // ---- Infinite cylinder ray intersection ----

    private double intersectInfiniteCylinder(double ax, double ay, double az,
                                              double bx, double by, double bz,
                                              double radius,
                                              double ox, double oy, double oz,
                                              double dx, double dy, double dz) {
        double abX = bx - ax, abY = by - ay, abZ = bz - az;
        double abLen2 = abX * abX + abY * abY + abZ * abZ;
        if (abLen2 < EPSILON) return -1.0;

        double invLen = 1.0 / Math.sqrt(abLen2);
        double ux = abX * invLen, uy = abY * invLen, uz = abZ * invLen;

        double oxRel = ox - ax, oyRel = oy - ay, ozRel = oz - az;

        double dotD = dx * ux + dy * uy + dz * uz;
        double dotO = oxRel * ux + oyRel * uy + ozRel * uz;

        double dxPerpX = dx - dotD * ux;
        double dxPerpY = dy - dotD * uy;
        double dxPerpZ = dz - dotD * uz;
        double oxPerpX = oxRel - dotO * ux;
        double oxPerpY = oyRel - dotO * uy;
        double oxPerpZ = ozRel - dotO * uz;

        double aC = dxPerpX * dxPerpX + dxPerpY * dxPerpY + dxPerpZ * dxPerpZ;
        double bC = 2.0 * (oxPerpX * dxPerpX + oxPerpY * dxPerpY + oxPerpZ * dxPerpZ);
        double cC = oxPerpX * oxPerpX + oxPerpY * oxPerpY + oxPerpZ * oxPerpZ - radius * radius;

        if (aC < EPSILON) return -1.0;

        double disc = bC * bC - 4.0 * aC * cC;
        if (disc < 0.0) return -1.0;

        double sqrtD = Math.sqrt(disc);
        double t1 = (-bC - sqrtD) / (2.0 * aC);
        double t2 = (-bC + sqrtD) / (2.0 * aC);

        double t = t1 > EPSILON ? t1 : (t2 > EPSILON ? t2 : -1.0);
        if (t < EPSILON) return -1.0;

        double hx = ox + dx * t;
        double hy = oy + dy * t;
        double hz = oz + dz * t;
        double proj = (hx - ax) * ux + (hy - ay) * uy + (hz - az) * uz;
        if (proj < 0.0 || proj > Math.sqrt(abLen2)) return -1.0;

        return t;
    }
}
