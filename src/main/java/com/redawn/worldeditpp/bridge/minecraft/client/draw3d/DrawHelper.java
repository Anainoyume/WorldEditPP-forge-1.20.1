package com.redawn.worldeditpp.bridge.minecraft.client.draw3d;

import com.redawn.worldeditpp.client.draw3d.DrawDirection;
import com.redawn.worldeditpp.client.draw3d.DrawPrimitive;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public final class DrawHelper {

    private DrawHelper() {}

    // ---- 16-hedron unit circle precomputed (k * PI/8) ----

    private static final int CIRCLE_SEGMENTS = 16;

    private static final double[][] CIRCLE_16 = {
            { +1.0000000000000000, +0.0000000000000000 },
            { +0.9238795325112867, +0.3826834323650898 },
            { +0.7071067811865476, +0.7071067811865476 },
            { +0.3826834323650898, +0.9238795325112867 },
            { +0.0000000000000001, +1.0000000000000000 },
            { -0.3826834323650897, +0.9238795325112867 },
            { -0.7071067811865475, +0.7071067811865476 },
            { -0.9238795325112867, +0.3826834323650899 },
            { -1.0000000000000000, +0.0000000000000001 },
            { -0.9238795325112868, -0.3826834323650897 },
            { -0.7071067811865477, -0.7071067811865475 },
            { -0.3826834323650903, -0.9238795325112865 },
            { -0.0000000000000002, -1.0000000000000000 },
            { +0.3826834323650900, -0.9238795325112866 },
            { +0.7071067811865474, -0.7071067811865477 },
            { +0.9238795325112865, -0.3826834323650904 },
    };

    // ---- Box edges ----

    /** Draws the twelve edges of an axis-aligned box as square-section bars. */
    public static void renderBoxEdges(PoseStack ps, VertexConsumer vc,
                                      double minX, double minY, double minZ,
                                      double maxX, double maxY, double maxZ,
                                      float thickness, float r, float g, float b, float a) {
        double radius = thickness * 0.5D;
        renderEdgeX(ps, vc, minX, maxX, minY, minZ, radius, r, g, b, a);
        renderEdgeX(ps, vc, minX, maxX, minY, maxZ, radius, r, g, b, a);
        renderEdgeX(ps, vc, minX, maxX, maxY, minZ, radius, r, g, b, a);
        renderEdgeX(ps, vc, minX, maxX, maxY, maxZ, radius, r, g, b, a);

        renderEdgeY(ps, vc, minX, minY, maxY, minZ, radius, r, g, b, a);
        renderEdgeY(ps, vc, minX, minY, maxY, maxZ, radius, r, g, b, a);
        renderEdgeY(ps, vc, maxX, minY, maxY, minZ, radius, r, g, b, a);
        renderEdgeY(ps, vc, maxX, minY, maxY, maxZ, radius, r, g, b, a);

        renderEdgeZ(ps, vc, minX, minY, minZ, maxZ, radius, r, g, b, a);
        renderEdgeZ(ps, vc, minX, maxY, minZ, maxZ, radius, r, g, b, a);
        renderEdgeZ(ps, vc, maxX, minY, minZ, maxZ, radius, r, g, b, a);
        renderEdgeZ(ps, vc, maxX, maxY, minZ, maxZ, radius, r, g, b, a);
    }

    public static void renderEdgeX(PoseStack ps, VertexConsumer vc, double minX, double maxX, double y, double z, double radius,
                                   float r, float g, float b, float a) {
        renderBox(ps, vc, minX - radius, y - radius, z - radius, maxX + radius, y + radius, z + radius, r, g, b, a);
    }

    public static void renderEdgeY(PoseStack ps, VertexConsumer vc, double x, double minY, double maxY, double z, double radius,
                                   float r, float g, float b, float a) {
        renderBox(ps, vc, x - radius, minY - radius, z - radius, x + radius, maxY + radius, z + radius, r, g, b, a);
    }

    public static void renderEdgeZ(PoseStack ps, VertexConsumer vc, double x, double y, double minZ, double maxZ, double radius,
                                   float r, float g, float b, float a) {
        renderBox(ps, vc, x - radius, y - radius, minZ - radius, x + radius, y + radius, maxZ + radius, r, g, b, a);
    }

    public static void renderBox(PoseStack ps, VertexConsumer vc,
                                 double x1, double y1, double z1, double x2, double y2, double z2,
                                 float r, float g, float b, float a) {
        vc.vertex(ps.last().pose(), (float) x1, (float) y1, (float) z1).color(r, g, b, a).endVertex();
        vc.vertex(ps.last().pose(), (float) x2, (float) y1, (float) z1).color(r, g, b, a).endVertex();
        vc.vertex(ps.last().pose(), (float) x2, (float) y2, (float) z1).color(r, g, b, a).endVertex();
        vc.vertex(ps.last().pose(), (float) x1, (float) y2, (float) z1).color(r, g, b, a).endVertex();

        vc.vertex(ps.last().pose(), (float) x1, (float) y1, (float) z2).color(r, g, b, a).endVertex();
        vc.vertex(ps.last().pose(), (float) x1, (float) y2, (float) z2).color(r, g, b, a).endVertex();
        vc.vertex(ps.last().pose(), (float) x2, (float) y2, (float) z2).color(r, g, b, a).endVertex();
        vc.vertex(ps.last().pose(), (float) x2, (float) y1, (float) z2).color(r, g, b, a).endVertex();

        vc.vertex(ps.last().pose(), (float) x1, (float) y1, (float) z1).color(r, g, b, a).endVertex();
        vc.vertex(ps.last().pose(), (float) x1, (float) y1, (float) z2).color(r, g, b, a).endVertex();
        vc.vertex(ps.last().pose(), (float) x2, (float) y1, (float) z2).color(r, g, b, a).endVertex();
        vc.vertex(ps.last().pose(), (float) x2, (float) y1, (float) z1).color(r, g, b, a).endVertex();

        vc.vertex(ps.last().pose(), (float) x1, (float) y2, (float) z1).color(r, g, b, a).endVertex();
        vc.vertex(ps.last().pose(), (float) x2, (float) y2, (float) z1).color(r, g, b, a).endVertex();
        vc.vertex(ps.last().pose(), (float) x2, (float) y2, (float) z2).color(r, g, b, a).endVertex();
        vc.vertex(ps.last().pose(), (float) x1, (float) y2, (float) z2).color(r, g, b, a).endVertex();

        vc.vertex(ps.last().pose(), (float) x1, (float) y1, (float) z1).color(r, g, b, a).endVertex();
        vc.vertex(ps.last().pose(), (float) x1, (float) y2, (float) z1).color(r, g, b, a).endVertex();
        vc.vertex(ps.last().pose(), (float) x1, (float) y2, (float) z2).color(r, g, b, a).endVertex();
        vc.vertex(ps.last().pose(), (float) x1, (float) y1, (float) z2).color(r, g, b, a).endVertex();

        vc.vertex(ps.last().pose(), (float) x2, (float) y1, (float) z1).color(r, g, b, a).endVertex();
        vc.vertex(ps.last().pose(), (float) x2, (float) y1, (float) z2).color(r, g, b, a).endVertex();
        vc.vertex(ps.last().pose(), (float) x2, (float) y2, (float) z2).color(r, g, b, a).endVertex();
        vc.vertex(ps.last().pose(), (float) x2, (float) y2, (float) z1).color(r, g, b, a).endVertex();
    }

    // ---- 1. Line (single axis-aligned edge) ----

    public static void renderLine(PoseStack ps, VertexConsumer vc,
                                  double sx, double sy, double sz,
                                  DrawDirection dir, double length, double thickness,
                                  float r, float g, float b, float a) {
        double radius = thickness * 0.5;
        double ex = sx + dir.normalX() * length;
        double ey = sy + dir.normalY() * length;
        double ez = sz + dir.normalZ() * length;
        double minX = Math.min(sx, ex), maxX = Math.max(sx, ex);
        double minY = Math.min(sy, ey), maxY = Math.max(sy, ey);
        double minZ = Math.min(sz, ez), maxZ = Math.max(sz, ez);
        if (dir.isXAxis()) {
            renderEdgeX(ps, vc, minX, maxX, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5, radius, r, g, b, a);
        } else if (dir.isYAxis()) {
            renderEdgeY(ps, vc, (minX + maxX) * 0.5, minY, maxY, (minZ + maxZ) * 0.5, radius, r, g, b, a);
        } else {
            renderEdgeZ(ps, vc, (minX + maxX) * 0.5, (minY + maxY) * 0.5, minZ, maxZ, radius, r, g, b, a);
        }
    }

    // ---- 2. Cone (16-gon base, single tip) ----

    public static void renderCone(PoseStack ps, VertexConsumer vc,
                                  double bx, double by, double bz,
                                  DrawDirection dir, double radius, double height,
                                  float r, float g, float b, float a) {
        double tipX = bx + dir.normalX() * height;
        double tipY = by + dir.normalY() * height;
        double tipZ = bz + dir.normalZ() * height;

        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            double[] p0 = circlePoint3D(i, radius, dir, bx, by, bz);
            double[] p1 = circlePoint3D(i + 1, radius, dir, bx, by, bz);

            vc.vertex(ps.last().pose(), (float) p0[0], (float) p0[1], (float) p0[2]).color(r, g, b, a).endVertex();
            vc.vertex(ps.last().pose(), (float) p1[0], (float) p1[1], (float) p1[2]).color(r, g, b, a).endVertex();
            vc.vertex(ps.last().pose(), (float) tipX, (float) tipY, (float) tipZ).color(r, g, b, a).endVertex();
            vc.vertex(ps.last().pose(), (float) tipX, (float) tipY, (float) tipZ).color(r, g, b, a).endVertex();
        }

        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            double[] p0 = circlePoint3D(i, radius, dir, bx, by, bz);
            double[] p1 = circlePoint3D(i + 1, radius, dir, bx, by, bz);

            vc.vertex(ps.last().pose(), (float) bx, (float) by, (float) bz).color(r, g, b, a).endVertex();
            vc.vertex(ps.last().pose(), (float) p0[0], (float) p0[1], (float) p0[2]).color(r, g, b, a).endVertex();
            vc.vertex(ps.last().pose(), (float) p1[0], (float) p1[1], (float) p1[2]).color(r, g, b, a).endVertex();
            vc.vertex(ps.last().pose(), (float) bx, (float) by, (float) bz).color(r, g, b, a).endVertex();
        }
    }

    // ---- 3. Cylinder (16-gon base + top, side quads, caps) ----

    public static void renderCylinder(PoseStack ps, VertexConsumer vc,
                                      double bx, double by, double bz,
                                      DrawDirection dir, double radius, double height,
                                      float r, float g, float b, float a) {
        double topX = bx + dir.normalX() * height;
        double topY = by + dir.normalY() * height;
        double topZ = bz + dir.normalZ() * height;

        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            double[] b0 = circlePoint3D(i, radius, dir, bx, by, bz);
            double[] b1 = circlePoint3D(i + 1, radius, dir, bx, by, bz);
            double[] t0 = circlePoint3D(i, radius, dir, topX, topY, topZ);
            double[] t1 = circlePoint3D(i + 1, radius, dir, topX, topY, topZ);

            vc.vertex(ps.last().pose(), (float) b0[0], (float) b0[1], (float) b0[2]).color(r, g, b, a).endVertex();
            vc.vertex(ps.last().pose(), (float) b1[0], (float) b1[1], (float) b1[2]).color(r, g, b, a).endVertex();
            vc.vertex(ps.last().pose(), (float) t1[0], (float) t1[1], (float) t1[2]).color(r, g, b, a).endVertex();
            vc.vertex(ps.last().pose(), (float) t0[0], (float) t0[1], (float) t0[2]).color(r, g, b, a).endVertex();
        }

        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            double[] p0 = circlePoint3D(i, radius, dir, bx, by, bz);
            double[] p1 = circlePoint3D(i + 1, radius, dir, bx, by, bz);
            vc.vertex(ps.last().pose(), (float) bx, (float) by, (float) bz).color(r, g, b, a).endVertex();
            vc.vertex(ps.last().pose(), (float) p0[0], (float) p0[1], (float) p0[2]).color(r, g, b, a).endVertex();
            vc.vertex(ps.last().pose(), (float) p1[0], (float) p1[1], (float) p1[2]).color(r, g, b, a).endVertex();
            vc.vertex(ps.last().pose(), (float) bx, (float) by, (float) bz).color(r, g, b, a).endVertex();
        }

        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            double[] p0 = circlePoint3D(i, radius, dir, topX, topY, topZ);
            double[] p1 = circlePoint3D(i + 1, radius, dir, topX, topY, topZ);
            vc.vertex(ps.last().pose(), (float) topX, (float) topY, (float) topZ).color(r, g, b, a).endVertex();
            vc.vertex(ps.last().pose(), (float) p0[0], (float) p0[1], (float) p0[2]).color(r, g, b, a).endVertex();
            vc.vertex(ps.last().pose(), (float) p1[0], (float) p1[1], (float) p1[2]).color(r, g, b, a).endVertex();
            vc.vertex(ps.last().pose(), (float) topX, (float) topY, (float) topZ).color(r, g, b, a).endVertex();
        }
    }

    // ---- 4. Arrow (shaft cylinder + head cone) ----

    private static final double ARROW_SHAFT_RADIUS = DrawPrimitive.ARROW_SHAFT_RADIUS;
    private static final double ARROW_HEAD_RADIUS = DrawPrimitive.ARROW_HEAD_RADIUS;
    private static final double ARROW_HEAD_HEIGHT = DrawPrimitive.ARROW_HEAD_HEIGHT;

    public static void renderArrow(PoseStack ps, VertexConsumer vc,
                                   double sx, double sy, double sz,
                                   DrawDirection dir, double totalLength,
                                   float r, float g, float b, float a) {
        renderArrow(ps, vc, sx, sy, sz, dir, totalLength,
                ARROW_SHAFT_RADIUS, ARROW_HEAD_RADIUS, ARROW_HEAD_HEIGHT, r, g, b, a);
    }

    public static void renderArrow(PoseStack ps, VertexConsumer vc,
                                   double sx, double sy, double sz,
                                   DrawDirection dir, double totalLength,
                                   double shaftRadius, double headRadius, double headHeight,
                                   float r, float g, float b, float a) {
        if (totalLength <= 0.0) {
            return;
        }
        double actualHeadHeight = Math.min(headHeight, totalLength);
        double actualHeadRadius = headRadius * actualHeadHeight / headHeight;
        double shaftLen = Math.max(0.0, totalLength - actualHeadHeight);
        if (shaftLen > 0.0) {
            renderCylinder(ps, vc, sx, sy, sz, dir, shaftRadius, shaftLen, r, g, b, a);
        }
        double headBaseX = sx + dir.normalX() * shaftLen;
        double headBaseY = sy + dir.normalY() * shaftLen;
        double headBaseZ = sz + dir.normalZ() * shaftLen;
        renderCone(ps, vc, headBaseX, headBaseY, headBaseZ, dir,
                actualHeadRadius, actualHeadHeight, r, g, b, a);
    }

    // ---- 5. Square face (4 edges + translucent fill) ----

    public static void renderSquareFace(PoseStack ps, VertexConsumer vc,
                                        double cx, double cy, double cz,
                                        DrawDirection normal, double sideLength,
                                        float edgeThickness,
                                        float r, float g, float b, float edgeAlpha, float fillAlpha) {
        double half = sideLength * 0.5;
        double eR = edgeThickness * 0.5;
        double min1, max1, min2, max2;
        double axisVal;

        if (normal.isXAxis()) {
            axisVal = cx;
            min1 = cy - half; max1 = cy + half;
            min2 = cz - half; max2 = cz + half;

            renderEdgeY(ps, vc, axisVal, min1, max1, min2, eR, r, g, b, edgeAlpha);
            renderEdgeY(ps, vc, axisVal, min1, max1, max2, eR, r, g, b, edgeAlpha);
            renderEdgeZ(ps, vc, axisVal, min1, min2, max2, eR, r, g, b, edgeAlpha);
            renderEdgeZ(ps, vc, axisVal, max1, min2, max2, eR, r, g, b, edgeAlpha);

            if (fillAlpha > 0.0F) {
                quadX(ps, vc, axisVal, min1, min2, max1, max2, r, g, b, fillAlpha);
            }
        } else if (normal.isYAxis()) {
            axisVal = cy;
            min1 = cx - half; max1 = cx + half;
            min2 = cz - half; max2 = cz + half;

            renderEdgeX(ps, vc, min1, max1, axisVal, min2, eR, r, g, b, edgeAlpha);
            renderEdgeX(ps, vc, min1, max1, axisVal, max2, eR, r, g, b, edgeAlpha);
            renderEdgeZ(ps, vc, min1, axisVal, min2, max2, eR, r, g, b, edgeAlpha);
            renderEdgeZ(ps, vc, max1, axisVal, min2, max2, eR, r, g, b, edgeAlpha);

            if (fillAlpha > 0.0F) {
                quadY(ps, vc, axisVal, min1, min2, max1, max2, r, g, b, fillAlpha);
            }
        } else {
            axisVal = cz;
            min1 = cx - half; max1 = cx + half;
            min2 = cy - half; max2 = cy + half;

            renderEdgeX(ps, vc, min1, max1, min2, axisVal, eR, r, g, b, edgeAlpha);
            renderEdgeX(ps, vc, min1, max1, max2, axisVal, eR, r, g, b, edgeAlpha);
            renderEdgeY(ps, vc, min1, min2, max2, axisVal, eR, r, g, b, edgeAlpha);
            renderEdgeY(ps, vc, max1, min2, max2, axisVal, eR, r, g, b, edgeAlpha);

            if (fillAlpha > 0.0F) {
                quadZ(ps, vc, axisVal, min1, min2, max1, max2, r, g, b, fillAlpha);
            }
        }
    }

    // ---- Vertex helpers for quads on axis-aligned planes ----

    private static void quadX(PoseStack ps, VertexConsumer vc, double x, double minY, double minZ, double maxY, double maxZ,
                              float r, float g, float b, float a) {
        vc.vertex(ps.last().pose(), (float) x, (float) minY, (float) minZ).color(r, g, b, a).endVertex();
        vc.vertex(ps.last().pose(), (float) x, (float) minY, (float) maxZ).color(r, g, b, a).endVertex();
        vc.vertex(ps.last().pose(), (float) x, (float) maxY, (float) maxZ).color(r, g, b, a).endVertex();
        vc.vertex(ps.last().pose(), (float) x, (float) maxY, (float) minZ).color(r, g, b, a).endVertex();
    }

    private static void quadY(PoseStack ps, VertexConsumer vc, double y, double minX, double minZ, double maxX, double maxZ,
                              float r, float g, float b, float a) {
        vc.vertex(ps.last().pose(), (float) minX, (float) y, (float) minZ).color(r, g, b, a).endVertex();
        vc.vertex(ps.last().pose(), (float) maxX, (float) y, (float) minZ).color(r, g, b, a).endVertex();
        vc.vertex(ps.last().pose(), (float) maxX, (float) y, (float) maxZ).color(r, g, b, a).endVertex();
        vc.vertex(ps.last().pose(), (float) minX, (float) y, (float) maxZ).color(r, g, b, a).endVertex();
    }

    private static void quadZ(PoseStack ps, VertexConsumer vc, double z, double minX, double minY, double maxX, double maxY,
                              float r, float g, float b, float a) {
        vc.vertex(ps.last().pose(), (float) minX, (float) minY, (float) z).color(r, g, b, a).endVertex();
        vc.vertex(ps.last().pose(), (float) maxX, (float) minY, (float) z).color(r, g, b, a).endVertex();
        vc.vertex(ps.last().pose(), (float) maxX, (float) maxY, (float) z).color(r, g, b, a).endVertex();
        vc.vertex(ps.last().pose(), (float) minX, (float) maxY, (float) z).color(r, g, b, a).endVertex();
    }

    // ---- Circle projection helpers ----

    private static double[] circlePoint3D(int seg, double radius, DrawDirection dir,
                                          double cx, double cy, double cz) {
        int idx = ((seg % CIRCLE_SEGMENTS) + CIRCLE_SEGMENTS) % CIRCLE_SEGMENTS;
        double cos = CIRCLE_16[idx][0];
        double sin = CIRCLE_16[idx][1];
        double u = radius * cos;
        double v = radius * sin;
        return switch (dir) {
            case EAST, WEST -> new double[]{cx, cy + u, cz + v};
            case UP, DOWN -> new double[]{cx + u, cy, cz + v};
            case NORTH, SOUTH -> new double[]{cx + u, cy + v, cz};
        };
    }
}
