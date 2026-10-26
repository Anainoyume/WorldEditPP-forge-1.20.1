package com.redawn.worldeditpp.bridge.api;

import java.util.List;
import java.util.Optional;

/** Immutable block state access without Minecraft registry or Property types. */
public interface BlockStateView {
    record PropertyValue(String name, String value, List<String> allowedValues) {
        public PropertyValue {
            allowedValues = List.copyOf(allowedValues);
        }
    }

    String blockId();
    List<PropertyValue> properties();
    Optional<BlockStateView> withProperty(String name, String value);
}
