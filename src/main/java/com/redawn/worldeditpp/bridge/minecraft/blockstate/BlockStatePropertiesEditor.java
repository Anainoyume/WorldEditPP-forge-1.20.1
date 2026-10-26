package com.redawn.worldeditpp.bridge.minecraft.blockstate;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Native state parsing shared by the inspector and the authoritative edit service. */
public final class BlockStatePropertiesEditor {
    public static Optional<BlockState> apply(BlockState state, String name, String value) {
        return MinecraftBlockStateView.of(state).withProperty(name, value).map(MinecraftBlockStateView::unwrap);
    }

    public static <T extends Comparable<T>> String value(BlockState state, Property<T> property) {
        return MinecraftBlockStateView.value(state, property);
    }

    public static <T extends Comparable<T>> List<String> values(Property<T> property) {
        return MinecraftBlockStateView.values(property);
    }

    private BlockStatePropertiesEditor() { }
}
