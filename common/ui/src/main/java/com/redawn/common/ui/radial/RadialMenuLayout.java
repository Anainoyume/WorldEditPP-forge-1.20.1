package com.redawn.common.ui.radial;

/** Pure geometry shared by rendering and hit testing. Angles follow screen coordinates. */
public record RadialMenuLayout(int count, double innerRadius, double outerRadius, double pushDistance) {
    public RadialMenuLayout {
        if (count < 1 || !Double.isFinite(innerRadius) || !Double.isFinite(outerRadius)
                || !Double.isFinite(pushDistance) || innerRadius < 0 || outerRadius <= innerRadius || pushDistance < 0) {
            throw new IllegalArgumentException("Invalid radial menu dimensions");
        }
    }

    public double step() { return Math.PI * 2 / count; }
    public double centerAngle(int index) { return -Math.PI / 2 + index * step(); }
    public double startAngle(int index) { return centerAngle(index) - step() / 2; }

    public int hit(double dx, double dy) {
        double radius = Math.hypot(dx, dy);
        if (radius <= innerRadius || radius > outerRadius + pushDistance) return -1;
        double angle = Math.atan2(dy, dx) - startAngle(0);
        angle = (angle % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2);
        return Math.min(count - 1, (int) (angle / step()));
    }

    public static double animate(double value, boolean selected, double seconds) {
        return value + ((selected ? 1 : 0) - value) * (1 - Math.exp(-Math.max(0, seconds) / 0.055));
    }
}
