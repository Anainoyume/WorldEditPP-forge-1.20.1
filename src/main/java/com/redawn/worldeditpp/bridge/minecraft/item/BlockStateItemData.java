package com.redawn.worldeditpp.bridge.minecraft.item;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import com.redawn.worldeditpp.bridge.minecraft.blockstate.BlockStatePropertiesEditor;
import com.redawn.worldeditpp.bridge.minecraft.blockstate.MinecraftBlockStateView;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import net.minecraft.world.level.block.state.BlockState;

/** Stores portable block-state metadata inside vanilla {@code ItemStack NBT}. */
public final class BlockStateItemData {
    public static final String ROOT_KEY = "worldeditpp";

    public static void write(ItemStack stack, BlockState state) {
        CompoundTag properties = new CompoundTag();
        for (var property : MinecraftBlockStateView.of(state).properties()) {
            properties.putString(property.name(), property.value());
        }
        CompoundTag customData = stack.getOrCreateTag();
        if (properties.isEmpty()) {
            customData.remove(ROOT_KEY);
        } else {
            customData.put(ROOT_KEY, properties);
        }
    }

    public static Optional<StoredState> read(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) return Optional.empty();
        CompoundTag customData = stack.getTag();
        if (!hasStoredProperties(customData)) return Optional.empty();
        CompoundTag outer = customData;

        CompoundTag stored = outer.getCompound(ROOT_KEY);
        BlockState state = blockItem.getBlock().defaultBlockState();
        Map<String, String> applied = new LinkedHashMap<>();
        for (String name : stored.getAllKeys().stream().sorted().toList()) {
            if (!stored.contains(name, Tag.TAG_STRING)) continue;
            String value = stored.getString(name);
            var updated = BlockStatePropertiesEditor.apply(state, name, value);
            if (updated.isPresent()) {
                state = updated.get();
                applied.put(name, value);
            }
        }
        return Optional.of(new StoredState(state, Collections.unmodifiableMap(applied)));
    }

    public static boolean hasStoredProperties(ItemStack stack) {
        return stack.getItem() instanceof BlockItem
                && hasStoredProperties(stack.getTag());
    }

    private static boolean hasStoredProperties(CompoundTag customData) {
        return customData != null
                && customData.contains(ROOT_KEY, Tag.TAG_COMPOUND);
    }

    public record StoredState(BlockState state, Map<String, String> properties) { }

    private BlockStateItemData() { }
}
