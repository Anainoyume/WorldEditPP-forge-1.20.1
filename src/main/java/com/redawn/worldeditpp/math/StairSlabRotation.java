package com.redawn.worldeditpp.math;

import com.redawn.worldeditpp.math.SlabStateTransform.Type;
import com.redawn.worldeditpp.math.StairStateTransform.Facing;
import com.redawn.worldeditpp.math.StairStateTransform.Half;
import com.redawn.worldeditpp.math.StairStateTransform.Shape;
import com.redawn.worldeditpp.math.StairStateTransform.State;
import java.util.Locale;

/**
 * Shared stair/slab rotation core. Two adapters (native Minecraft states and WorldEdit states)
 * supply their own property lookup and value writing, so the transform plumbing exists once.
 *
 * <p>Both adapters keep their existing name normalization: only {@link Properties#valueName} and
 * {@link Properties#withNamedValue} decide lower-casing and failure handling.</p>
 */
public final class StairSlabRotation {
    /** Host block-state property access; {@code S} is the state type and {@code P} the property type. */
    public interface Properties<S, P> {
        P property(S state, String name);

        String valueName(S state, P property);

        S withNamedValue(S state, P property, String valueName);
    }

    public static <S, P> S rotateStair(S original, DiscreteRotation finalRotation, Properties<S, P> properties) {
        P facingProperty = properties.property(original, "facing");
        P halfProperty = properties.property(original, "half");
        P shapeProperty = properties.property(original, "shape");
        if (facingProperty == null || halfProperty == null || shapeProperty == null) return original;

        Facing facing = Facing.fromName(properties.valueName(original, facingProperty));
        Half half = Half.fromName(properties.valueName(original, halfProperty));
        Shape shape = Shape.fromName(properties.valueName(original, shapeProperty));
        if (facing == null || half == null || shape == null) return original;

        StairStateTransform.Result transformed = StairStateTransform.transform(
                new State(facing, half, shape), finalRotation);
        if (!transformed.legal()) return original;

        State result = transformed.state();
        S state = properties.withNamedValue(
                original, facingProperty, result.facing().name().toLowerCase(Locale.ROOT));
        state = properties.withNamedValue(
                state, halfProperty, result.half().name().toLowerCase(Locale.ROOT));
        return properties.withNamedValue(
                state, shapeProperty, result.shape().name().toLowerCase(Locale.ROOT));
    }

    public static <S, P> S rotateSlab(S original, DiscreteRotation finalRotation, Properties<S, P> properties) {
        P typeProperty = properties.property(original, "type");
        if (typeProperty == null) return original;

        Type type = Type.fromName(properties.valueName(original, typeProperty));
        if (type == null) return original;
        SlabStateTransform.Result transformed = SlabStateTransform.transform(type, finalRotation);
        if (!transformed.legal()) return original;
        return properties.withNamedValue(
                original, typeProperty, transformed.type().name().toLowerCase(Locale.ROOT));
    }

    private StairSlabRotation() { }
}
