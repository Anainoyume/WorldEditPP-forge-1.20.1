package com.redawn.worldeditpp.bridge.minecraft.client.item;

import com.redawn.worldeditpp.bridge.minecraft.api.client.ClientPlatformBridge;
import com.redawn.worldeditpp.bridge.minecraft.item.BlockStateItemData;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Per-item-renderer cache, invalidated with the resource manager. */
public final class StoredBlockStateModels {
    private final Map<BlockState, Map<BakedModel, BakedModel>> models = new HashMap<>();

    public BakedModel resolve(ItemStack stack, BakedModel original) {
        var stored = BlockStateItemData.read(stack);
        if (stored.isEmpty()) return original;
        BlockState state = stored.get().state();
        var renderer = Minecraft.getInstance().getBlockRenderer();
        BakedModel geometry = renderer.getBlockModel(state);
        BakedModel display = renderer.getBlockModel(Blocks.STONE.defaultBlockState());
        return models.computeIfAbsent(state, ignored -> new IdentityHashMap<>())
                .computeIfAbsent(display, ignored -> ClientPlatformBridge.get().storedModel(state, geometry, display));
    }

    public void clear() {
        models.clear();
    }
}
