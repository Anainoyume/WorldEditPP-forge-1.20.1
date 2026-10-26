package com.redawn.worldeditpp.bridge.minecraft.api.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Client-only loader extensions; installed before client registrations run. */
public final class ClientPlatformBridge {
    public interface BlockModelView {
        Iterable<RenderType> layers();
        List<BakedQuad> quads(Direction face, RandomSource random, RenderType layer);
    }
    public interface Backend {
        KeyMapping key(String name, InputConstants.Type type, int key, String category);
        InputConstants.Key boundKey(KeyMapping mapping);
        boolean hasStencil(RenderTarget target);
        void enableStencil(RenderTarget target);
        BakedModel storedModel(BlockState state, BakedModel geometry, BakedModel display);
        BlockModelView blockModel(BlockState state, BakedModel model, BlockEntity entity);
    }
    private static Backend backend;
    public static void install(Backend value) {
        if (backend != null) throw new IllegalStateException("Client platform already installed");
        backend = Objects.requireNonNull(value);
    }
    public static Backend get() {
        return Objects.requireNonNull(backend, "Client platform not installed");
    }
    private ClientPlatformBridge() {}
}
