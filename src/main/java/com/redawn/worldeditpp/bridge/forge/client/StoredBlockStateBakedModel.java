package com.redawn.worldeditpp.bridge.forge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;

/** Renders stored-state geometry with the standard full-block display presentation. */
@SuppressWarnings("deprecation")
public final class StoredBlockStateBakedModel extends BakedModelWrapper<BakedModel> {
    private final BlockState storedState;
    private final BakedModel displayModel;

    public StoredBlockStateBakedModel(
            BlockState storedState,
            BakedModel stateModel,
            BakedModel displayModel
    ) {
        super(stateModel);
        this.storedState = storedState;
        this.displayModel = displayModel;
    }

    @Override
    public List<BakedQuad> getQuads(
            @Nullable BlockState ignoredState,
            @Nullable Direction side,
            RandomSource random
    ) {
        return originalModel.getQuads(storedState, side, random);
    }

    @Override
    public List<BakedQuad> getQuads(
            @Nullable BlockState ignoredState,
            @Nullable Direction side,
            RandomSource random,
            ModelData modelData,
            @Nullable RenderType renderType
    ) {
        return originalModel.getQuads(storedState, side, random, modelData, renderType);
    }

    @Override
    public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
        return List.of(this);
    }

    @Override
    public boolean isGui3d() {
        return displayModel.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return displayModel.usesBlockLight();
    }

    @Override
    public BakedModel applyTransform(
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            boolean leftHand
    ) {
        displayModel.applyTransform(displayContext, poseStack, leftHand);
        return this;
    }
}
