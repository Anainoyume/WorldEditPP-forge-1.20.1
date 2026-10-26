package com.redawn.worldeditpp.bridge.minecraft.client.ui;

import com.redawn.worldeditpp.bridge.minecraft.client.selection.projection.SelectionBlockProjectionController;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

/** Drops baked projection pages when models, textures or shader resources change. */
public final class ProjectionResourceReloadListener implements PreparableReloadListener {
    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resources,
                                           ProfilerFiller preparationsProfiler,
                                           ProfilerFiller reloadProfiler,
                                           Executor backgroundExecutor, Executor gameExecutor) {
        return barrier.wait(null).thenRunAsync(SelectionBlockProjectionController::onResourceReload, gameExecutor);
    }

    @Override
    public String getName() {
        return "worldeditpp_projection_reload";
    }
}
