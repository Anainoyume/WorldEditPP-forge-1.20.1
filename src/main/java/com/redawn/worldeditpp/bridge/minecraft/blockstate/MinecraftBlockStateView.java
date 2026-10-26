package com.redawn.worldeditpp.bridge.minecraft.blockstate;

import com.redawn.worldeditpp.bridge.api.BlockStateView;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Owns native property enumeration/parsing; consumers use the stable string schema. */
public final class MinecraftBlockStateView implements BlockStateView {
    private final BlockState state;

    private MinecraftBlockStateView(BlockState state) {
        this.state = Objects.requireNonNull(state);
    }

    public static BlockStateView of(BlockState state) {
        return new MinecraftBlockStateView(state);
    }

    public static BlockState unwrap(BlockStateView view) {
        if (view instanceof MinecraftBlockStateView nativeView) return nativeView.state;
        throw new IllegalArgumentException("Block state belongs to a different runtime");
    }

    @Override public String blockId() {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    @Override public List<PropertyValue> properties() {
        return state.getProperties().stream().map(property ->
                new PropertyValue(property.getName(), value(state, property), values(property))).toList();
    }

    @Override public Optional<BlockStateView> withProperty(String name, String value) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
        return property == null ? Optional.empty() : apply(property, value);
    }

    private <T extends Comparable<T>> Optional<BlockStateView> apply(Property<T> property, String value) {
        return property.getValue(value).map(parsed -> of(state.setValue(property, parsed)));
    }

    public static <T extends Comparable<T>> String value(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    public static <T extends Comparable<T>> List<String> values(Property<T> property) {
        return property.getPossibleValues().stream().map(property::getName).toList();
    }
}
