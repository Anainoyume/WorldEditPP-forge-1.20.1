package com.redawn.worldeditpp.bridge.forge;

import com.redawn.worldeditpp.bridge.minecraft.item.RainbowWandItem;
import com.redawn.worldeditpp.WorldEditPlusPlus;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ForgeItems
{
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, WorldEditPlusPlus.MOD_ID);

    public static final Supplier<Item> AIR_BLOCK = ITEMS.register("air_block",
            () -> new Item(new Item.Properties()));

    public static final Supplier<Item> PACKED_BLOCK = ITEMS.register("packed_block",
            () -> new Item(new Item.Properties()));

    public static final Supplier<Item> WORLD_EDIT_WAND = ITEMS.register("worldeditpp_wand",
            () -> new RainbowWandItem(Tiers.WOOD, new Item.Properties()));

    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.getEntries().putAfter(new ItemStack(Items.WOODEN_AXE), new ItemStack(WORLD_EDIT_WAND.get()),
                    CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.getEntries().putFirst(new ItemStack(PACKED_BLOCK.get()),
                    CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            event.getEntries().putFirst(new ItemStack(AIR_BLOCK.get()),
                    CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    private ForgeItems() {}
}
