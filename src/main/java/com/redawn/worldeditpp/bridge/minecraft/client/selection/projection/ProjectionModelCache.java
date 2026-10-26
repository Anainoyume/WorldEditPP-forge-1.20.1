package com.redawn.worldeditpp.bridge.minecraft.client.selection.projection;

import com.redawn.worldeditpp.bridge.minecraft.api.client.ClientPlatformBridge;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Only vanilla deterministic models qualify; weighted, multipart and modded models use their callbacks. */
final class ProjectionModelCache {
    private final IdentityHashMap<BlockState, Entry> cache = new IdentityHashMap<>();
    private final RandomSource random = RandomSource.create(0);
    record Layer(RenderType type, List<List<BakedQuad>> faces, List<BakedQuad> unculled) {}
    record Model(List<Layer> layers, boolean hasUnculledQuads) {}
    private record Entry(BakedModel model, Model cached) {}

    Model get(BlockState state, BakedModel model) {
        if (model.getClass() != SimpleBakedModel.class || state.hasBlockEntity()) return null;
        Entry entry = cache.get(state);
        if (entry != null && entry.model == model) return entry.cached;
        var view = ClientPlatformBridge.get().blockModel(state, model, null);
        var layers = new ArrayList<Layer>();
        for (RenderType type : view.layers()) {
            var faces = new ArrayList<List<BakedQuad>>(6);
            for (Direction face : Direction.values()) faces.add(view.quads(face, random, type));
            layers.add(new Layer(type, List.copyOf(faces), view.quads(null, random, type)));
        }
        List<Layer> immutableLayers = List.copyOf(layers);
        boolean hasUnculledQuads = immutableLayers.stream().anyMatch(layer -> !layer.unculled().isEmpty());
        Model cached = new Model(immutableLayers, hasUnculledQuads);
        if (cache.size() >= 4096) cache.clear();
        cache.put(state, new Entry(model, cached));
        return cached;
    }
    void clear() { cache.clear(); }
}
