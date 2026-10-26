package com.redawn.common.gizmo;

import com.redawn.common.selection.SelectionFace;
import java.util.EnumMap;
import java.util.Map;

/** Time-based cross-fade between rotation handles on selection faces. */
public final class RotationHandleAnimator {
    public static final long FADE_DURATION_NANOS = 200_000_000L;

    private final Map<SelectionFace, Float> progress = new EnumMap<>(SelectionFace.class);
    private SelectionFace targetFace;
    private long lastNanos;

    public void update(SelectionFace face, long nowNanos) {
        targetFace = face;
        if (lastNanos == 0L) {
            lastNanos = nowNanos;
        }
        long elapsed = Math.max(0L, nowNanos - lastNanos);
        lastNanos = nowNanos;
        float step = Math.min(1.0F, (float) elapsed / FADE_DURATION_NANOS);
        for (SelectionFace candidate : SelectionFace.values()) {
            float target = candidate == targetFace ? 1.0F : 0.0F;
            progress.put(candidate, approach(progress(candidate), target, step));
        }
    }

    public float progress(SelectionFace face) {
        return progress.getOrDefault(face, 0.0F);
    }

    public void reset() {
        targetFace = null;
        lastNanos = 0L;
        progress.clear();
    }

    private static float approach(float value, float target, float step) {
        if (value < target) {
            return Math.min(target, value + step);
        }
        return Math.max(target, value - step);
    }
}
