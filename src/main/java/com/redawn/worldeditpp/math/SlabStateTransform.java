package com.redawn.worldeditpp.math;

import java.util.Locale;

/** Exact, history-independent mapping of a slab's vertical type. */
public final class SlabStateTransform {
    public static Result transform(Type original, DiscreteRotation rotation) {
        if (original == Type.DOUBLE) return new Result(original, true);

        int localUp = original == Type.BOTTOM ? 1 : -1;
        int transformedUp = rotation.transformVerticalSign(localUp);
        if (transformedUp == 0) return new Result(original, false);
        return new Result(transformedUp > 0 ? Type.BOTTOM : Type.TOP, true);
    }

    public enum Type {
        BOTTOM,
        TOP,
        DOUBLE;

        public static Type fromName(String name) {
            try {
                return valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }

    public record Result(Type type, boolean legal) { }

    private SlabStateTransform() { }
}
