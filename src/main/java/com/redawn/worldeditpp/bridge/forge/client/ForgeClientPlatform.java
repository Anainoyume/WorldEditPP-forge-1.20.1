package com.redawn.worldeditpp.bridge.forge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.redawn.worldeditpp.bridge.minecraft.api.client.ClientPlatformBridge;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.settings.KeyConflictContext;

public final class ForgeClientPlatform implements ClientPlatformBridge.Backend {
    @Override
    public KeyMapping key(String name, InputConstants.Type type, int key, String category) {
        return new KeyMapping(name, KeyConflictContext.IN_GAME, type, key, category) {
            @Override
            public boolean isActiveAndMatches(InputConstants.Key pressed) {
                // All editor actions use EditModeKeyInterceptor before vanilla dispatch.
                // Forge 47.1.3 clicks only the FIRST active mapping, unlike NeoForge 1.21.
                // Never reserve that fallback slot: inactive Alt+2/3 (or remapped keys)
                // must reach vanilla. Keep the normal context/isDown for held draw keys.
                return false;
            }
        };
    }
    @Override
    public InputConstants.Key boundKey(KeyMapping mapping) {
        return mapping.getKey();
    }
    @Override
    public boolean hasStencil(RenderTarget target) {
        return target.isStencilEnabled();
    }
    @Override
    public void enableStencil(RenderTarget target) {
        target.enableStencil();
    }
    @Override
    public BakedModel storedModel(BlockState state, BakedModel geometry, BakedModel display) {
        return new StoredBlockStateBakedModel(state, geometry, display);
    }
    @Override
    public ClientPlatformBridge.BlockModelView blockModel(BlockState state, BakedModel model, BlockEntity entity) {
        ModelData data = entity == null ? ModelData.EMPTY : entity.getModelData();
        return new ClientPlatformBridge.BlockModelView() {
            public Iterable<RenderType> layers() {
                return ItemBlockRenderTypes.getRenderLayers(state);
            }
            public List<BakedQuad> quads(Direction face, RandomSource random, RenderType layer) {
                return model.getQuads(state, face, random, data, layer);
            }
        };
    }
}
