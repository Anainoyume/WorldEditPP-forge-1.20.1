package com.redawn.common.gizmo;

/** Continuous three-axis orientation animation with interruption-safe retargeting. */
public final class GizmoOrientationAnimator {
    public static final long DURATION_NANOS = 200_000_000L;

    private final AxisTween x = new AxisTween();
    private final AxisTween y = new AxisTween();
    private final AxisTween z = new AxisTween();
    private boolean initialized;

    public void update(double relativeX, double relativeY, double relativeZ, long nowNanos) {
        int targetX = sign(relativeX, x.targetSign());
        int targetY = sign(relativeY, y.targetSign());
        int targetZ = sign(relativeZ, z.targetSign());
        if (!initialized) {
            x.snap(targetX);
            y.snap(targetY);
            z.snap(targetZ);
            initialized = true;
            return;
        }
        x.retarget(targetX, nowNanos);
        y.retarget(targetY, nowNanos);
        z.retarget(targetZ, nowNanos);
    }

    public float x() {
        return x.value();
    }

    public float y() {
        return y.value();
    }

    public float z() {
        return z.value();
    }

    public boolean isTransitioning() {
        return x.isTransitioning() || y.isTransitioning() || z.isTransitioning();
    }

    private static int sign(double relativePosition, int fallback) {
        if (relativePosition > 0.0) {
            return 1;
        }
        if (relativePosition < 0.0) {
            return -1;
        }
        return fallback;
    }

    private static final class AxisTween {
        private float start = 1.0F;
        private float value = 1.0F;
        private int target = 1;
        private long startNanos;
        private boolean transitioning;

        void snap(int sign) {
            start = sign;
            value = sign;
            target = sign;
            transitioning = false;
        }

        void retarget(int sign, long nowNanos) {
            sample(nowNanos);
            if (sign == target) {
                return;
            }
            start = value;
            target = sign;
            startNanos = nowNanos;
            transitioning = Math.abs(value - target) > 0.0001F;
        }

        float value() {
            return value;
        }

        int targetSign() {
            return target;
        }

        boolean isTransitioning() {
            return transitioning;
        }

        private void sample(long nowNanos) {
            if (!transitioning) {
                return;
            }
            float progress = Math.min(1.0F, Math.max(0.0F,
                    (float) (nowNanos - startNanos) / DURATION_NANOS));
            float remaining = 1.0F - progress;
            float eased = 1.0F - remaining * remaining * remaining;
            value = start + (target - start) * eased;
            if (progress >= 1.0F) {
                value = target;
                transitioning = false;
            }
        }
    }
}
