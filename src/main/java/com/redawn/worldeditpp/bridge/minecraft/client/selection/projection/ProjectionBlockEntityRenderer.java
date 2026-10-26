package com.redawn.worldeditpp.bridge.minecraft.client.selection.projection;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Snapshot entities use selection-local positions, never the dispatcher's world-distance test. */
final class ProjectionBlockEntityRenderer {
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void render(BlockEntityRenderDispatcher dispatcher, BlockEntity entity, float partialTick,
                       PoseStack pose, MultiBufferSource buffers) {
        BlockEntityRenderer renderer = dispatcher.getRenderer(entity);
        if (renderer != null) {
            // Visibility belongs to the destination-space page, not the camera at bake time.
            // Match the live preview's lighting instead of sampling real chunks at local coords.
            renderer.render(entity, partialTick, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
    }

    private ProjectionBlockEntityRenderer() {}
}
