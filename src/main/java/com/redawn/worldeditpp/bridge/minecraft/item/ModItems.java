package com.redawn.worldeditpp.bridge.minecraft.item;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;

/** Stable item handles; registration and creative-tab placement belong to the loader. */
public final class ModItems {
    private static Supplier<Item> air, packed, wand;
    public static final Supplier<Item> AIR_BLOCK = () -> Objects.requireNonNull(air, "Items not installed").get();
    public static final Supplier<Item> PACKED_BLOCK = () -> Objects.requireNonNull(packed, "Items not installed").get();
    public static final Supplier<Item> WORLD_EDIT_WAND = () -> Objects.requireNonNull(wand, "Items not installed").get();
    public static void install(Supplier<Item> airItem, Supplier<Item> packedItem, Supplier<Item> wandItem) {
        if (air != null) throw new IllegalStateException("Items already installed");
        air = Objects.requireNonNull(airItem);
        packed = Objects.requireNonNull(packedItem);
        wand = Objects.requireNonNull(wandItem);
    }
    private ModItems() { }
}
