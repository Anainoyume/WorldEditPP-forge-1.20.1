package com.redawn.common.selection;

public final class AxisDragMath {
    public static final int AXIS_X = 0;
    public static final int AXIS_Y = 1;
    public static final int AXIS_Z = 2;

    public static final double MAX_PLAYER_DISTANCE = 100.0;
    public static final double MAX_PER_UPDATE_DISTANCE = 20.0;

    private static final double EPSILON = 1.0E-9;

    private AxisDragMath() {}

    /**
     * 平面1常量轴：x轴拖拽用 xOz 面(y 常量)，y轴用 xOy 面(z 常量)，z轴用 xOz 面(y 常量)。
     */
    public static int plane1ConstantAxis(int axis) {
        return switch (axis) {
            case AXIS_X, AXIS_Z -> 1;
            case AXIS_Y -> 2;
            default -> throw new IllegalArgumentException("unknown axis " + axis);
        };
    }

    /**
     * 平面2常量轴：x轴拖拽用 xOy 面(z 常量)，y轴用 yOz 面(x 常量)，z轴用 yOz 面(x 常量)。
     */
    public static int plane2ConstantAxis(int axis) {
        return switch (axis) {
            case AXIS_X -> 2;
            case AXIS_Y, AXIS_Z -> 0;
            default -> throw new IllegalArgumentException("unknown axis " + axis);
        };
    }

    public static double axisComponent(int worldAxis, double x, double y, double z) {
        return switch (worldAxis) {
            case 0 -> x;
            case 1 -> y;
            default -> z;
        };
    }

    /**
     * 射线与两个逻辑平面求交，返回先碰到的交点（世界坐标），无交点返回 null。
     */
    public static double[] firstHit(int plane1Axis, double plane1Constant, int plane2Axis, double plane2Constant,
                                    double ox, double oy, double oz, double dx, double dy, double dz) {
        double t1 = planeHitDistance(plane1Constant, axisComponent(plane1Axis, ox, oy, oz), axisComponent(plane1Axis, dx, dy, dz));
        double t2 = planeHitDistance(plane2Constant, axisComponent(plane2Axis, ox, oy, oz), axisComponent(plane2Axis, dx, dy, dz));
        double t = -1.0;
        if (t1 > 0.0 && (t < 0.0 || t1 < t)) {
            t = t1;
        }
        if (t2 > 0.0 && (t < 0.0 || t2 < t)) {
            t = t2;
        }
        if (t < 0.0) {
            return null;
        }
        return new double[]{ox + dx * t, oy + dy * t, oz + dz * t};
    }

    /**
     * 射线与单个逻辑平面求交，无交点返回 null。
     */
    public static double[] firstHit(int planeAxis, double planeConstant,
                                    double ox, double oy, double oz, double dx, double dy, double dz) {
        double t = planeHitDistance(planeConstant, axisComponent(planeAxis, ox, oy, oz), axisComponent(planeAxis, dx, dy, dz));
        if (t < 0.0) {
            return null;
        }
        return new double[]{ox + dx * t, oy + dy * t, oz + dz * t};
    }

    private static double planeHitDistance(double planeConstant, double origin, double direction) {
        if (Math.abs(direction) < EPSILON) {
            return -1.0;
        }
        double t = (planeConstant - origin) / direction;
        return t > 0.0 ? t : -1.0;
    }

    /**
     * 面拖拽的可移动轴：法线轴以外的两个世界轴（Y_FACE 法线 y → x/z 可移动）。
     */
    public static int[] faceMoveAxes(int normalAxis) {
        return new int[]{(normalAxis + 1) % 3, (normalAxis + 2) % 3};
    }

    /**
     * 交点相对基准点在拖拽轴上的带符号投影长度（世界方块单位）。
     */
    public static double projectedDelta(int axis, double hitX, double hitY, double hitZ, double refX, double refY, double refZ) {
        return switch (axis) {
            case AXIS_X -> hitX - refX;
            case AXIS_Y -> hitY - refY;
            default -> hitZ - refZ;
        };
    }

    public static boolean exceedsPlayerDistance(double px, double py, double pz, double cx, double cy, double cz) {
        return exceedsPlayerDistance(px, py, pz, cx, cy, cz, MAX_PLAYER_DISTANCE);
    }

    /**
     * @param maxDistance caller-supplied world-space limit; client callers use the effective
     *                    render distance so a visible selection remains draggable.
     */
    public static boolean exceedsPlayerDistance(double px, double py, double pz,
                                                double cx, double cy, double cz,
                                                double maxDistance) {
        if (!Double.isFinite(maxDistance) || maxDistance <= 0.0D) return true;
        double dx = cx - px;
        double dy = cy - py;
        double dz = cz - pz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz) > maxDistance;
    }

    /**
     * 相邻两 tick 选区目标位置的位移距离检查：箭头退化为单轴差，面为两轴欧几里得距离。
     */
    public static boolean exceedsPerUpdateDistance(int dx, int dy, int dz, int lastDx, int lastDy, int lastDz) {
        double ddx = dx - lastDx;
        double ddy = dy - lastDy;
        double ddz = dz - lastDz;
        return Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz) > MAX_PER_UPDATE_DISTANCE;
    }
}
