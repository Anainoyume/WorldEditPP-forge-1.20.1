package com.redawn.worldeditpp.bridge.minecraft.client.selection.projection;

/** Shared projection caches; layer vertex builders are owned by each individual page mesh. */
final class ProjectionMeshBuffers implements AutoCloseable {
    final ProjectionModelCache models = new ProjectionModelCache();
    final AlphaBlendBufferSource entities = new AlphaBlendBufferSource();

    OwnedBufferBuilder newLayer() {
        return new OwnedBufferBuilder(8192);
    }

    long bytes() { return entities.bytes(); }

    @Override public void close() {
        models.clear();
        entities.close();
    }
}
