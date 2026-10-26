package com.redawn.worldeditpp.bridge.worldedit;

import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;

/**
 * Shared WorldEdit 7.2 block-state property access. Both the server-side draw service and the
 * projection rotation need the same lookup/parse/write rules; keeping them here avoids two
 * slightly diverging copies. Does not change any value normalization by itself.
 */
final class WorldEditBlockStates {
    static Property<?> property(BlockType type, String name) {
        return type.getProperties().stream()
                .filter(property -> property.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    static Property<?> property(BlockState state, String name) {
        return property(state.getBlockType(), name);
    }

    /** Current value of a property, or null when the state does not carry it. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static Object value(BlockState state, Property<?> property) {
        return state.getState((Property) property);
    }

    static String name(BlockState state, Property<?> property) {
        Object value = value(state, property);
        return value == null ? "" : value.toString();
    }

    /** Parses a serialized value name, or null when the property rejects it. */
    static Object parse(Property<?> property, String name) {
        try {
            return property.getValueFor(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static BlockState with(BlockState state, Property property, Object value) {
        return state.with(property, value);
    }

    static BlockState withNamed(BlockState state, Property<?> property, String name) {
        Object value = parse(property, name);
        return value == null ? state : with(state, property, value);
    }

    private WorldEditBlockStates() { }
}
