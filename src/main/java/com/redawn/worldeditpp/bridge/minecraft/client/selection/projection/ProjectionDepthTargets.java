package com.redawn.worldeditpp.bridge.minecraft.client.selection.projection;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.redawn.worldeditpp.bridge.minecraft.api.client.ClientPlatformBridge;

/** Saves world depth while the projection temporarily adds its own depth. */
final class ProjectionDepthTargets implements AutoCloseable {
    private RenderTarget worldDepth;

    boolean begin(RenderTarget mainTarget) {
        if (!mainTarget.useDepth || mainTarget.width <= 0 || mainTarget.height <= 0) {
            return false;
        }
        var platform = ClientPlatformBridge.get();
        if (worldDepth == null || platform.hasStencil(worldDepth) != platform.hasStencil(mainTarget)) {
            close();
            worldDepth = new TextureTarget(mainTarget.width, mainTarget.height, true, false);
            if (platform.hasStencil(mainTarget)) {
                platform.enableStencil(worldDepth);
            }
        }
        if (worldDepth.width != mainTarget.width || worldDepth.height != mainTarget.height) {
            worldDepth.resize(mainTarget.width, mainTarget.height, false);
        }
        try {
            worldDepth.copyDepthFrom(mainTarget);
        } finally {
            mainTarget.bindWrite(false);
        }
        // Keep terrain depth: both projection passes must test against the world.
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        return true;
    }

    void restoreWorld(RenderTarget mainTarget) {
        if (worldDepth != null) {
            try {
                mainTarget.copyDepthFrom(worldDepth);
            } finally {
                mainTarget.bindWrite(false);
            }
        }
    }

    @Override
    public void close() {
        if (worldDepth != null) {
            worldDepth.destroyBuffers();
            worldDepth = null;
        }
    }
}
