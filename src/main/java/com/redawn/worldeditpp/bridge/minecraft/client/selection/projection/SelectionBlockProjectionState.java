package com.redawn.worldeditpp.bridge.minecraft.client.selection.projection;

import com.redawn.worldeditpp.client.selection.projection.SelectionProjectionAssembler;

import com.redawn.worldeditpp.WorldEditPlusPlus;
import com.redawn.worldeditpp.bridge.minecraft.client.ui.ClientFeedback;
import com.redawn.worldeditpp.bridge.minecraft.network.SelectionBlocksPayload;
import com.redawn.worldeditpp.network.SelectionProjectionLimits;
import net.minecraft.client.Minecraft;

/** Coordinates activation and complete snapshots; mesh resources have a single owner. */
public final class SelectionBlockProjectionState {
    public static final float PROJECTION_ALPHA = 0.6F;
    private final SelectionProjectionAssembler assembler = new SelectionProjectionAssembler();
    private final ProjectionClipboardCache cache = new ProjectionClipboardCache();
    private boolean active;
    private boolean hasSnapshot;
    private int snapshotMinX, snapshotMinY, snapshotMinZ;
    private final SelectionProjectionPages mesh = new SelectionProjectionPages(
            () -> cache, () -> snapshotMinX, () -> snapshotMinY, () -> snapshotMinZ);

    public boolean isActive() { return active; }
    public boolean isReady() { return mesh.isReady(); }
    public boolean hasSnapshot() { return hasSnapshot; }
    public double snapshotMinX() { return snapshotMinX; }
    public double snapshotMinY() { return snapshotMinY; }
    public double snapshotMinZ() { return snapshotMinZ; }
    public int sizeX() { return cache.sizeX(); }
    public int sizeY() { return cache.sizeY(); }
    public int sizeZ() { return cache.sizeZ(); }
    public int tileSize(int axis) { return cache.tile(axis); }
    Iterable<SelectionProjectionMesh> meshes() { return mesh.meshes(); }

    public void setActive(boolean active) {
        if (this.active != active) {
            reset();
            this.active = active;
        }
    }

    public void beginReceiving() {
        reset();
        active = true;
    }

    public void reset() {
        active = false;
        assembler.reset();
        clearSnapshot();
    }

    public boolean onSelectionBlocks(SelectionBlocksPayload payload) {
        SelectionProjectionAssembler.AssemblyResult result = assembler.accept(payload.asChunk());
        if (result.type() == SelectionProjectionAssembler.ResultType.COMPLETE) {
            return applySnapshot(result.snapshot());
        }
        if (result.type() == SelectionProjectionAssembler.ResultType.CLEARED) {
            clearSnapshot();
        } else if (result.type() == SelectionProjectionAssembler.ResultType.REJECTED) {
            WorldEditPlusPlus.LOGGER.warn("Rejected projection chunk: generation={} chunk={}/{}",
                    payload.generation(), payload.chunkIndex(), payload.chunkCount());
            ClientFeedback.error("message.worldeditpp.projection.rejected");
        }
        return false;
    }

    private boolean applySnapshot(SelectionProjectionAssembler.SnapshotData snapshot) {
        long volume = (long) snapshot.sizeX() * snapshot.sizeY() * snapshot.sizeZ();
        if (volume <= 0 || volume > SelectionProjectionLimits.MAX_VOLUME
                || snapshot.entries().length > SelectionProjectionLimits.MAX_TOTAL_ENTRY_INTS) {
            clearSnapshot();
            ClientFeedback.error("message.worldeditpp.projection.rejected");
            return false;
        }
        try {
            cache.replace(snapshot);
            mesh.invalidate(true);
        } catch (RuntimeException exception) {
            WorldEditPlusPlus.LOGGER.warn("Rejected invalid projection block entity snapshot", exception);
            clearSnapshot();
            ClientFeedback.error("message.worldeditpp.projection.rejected");
            return false;
        }
        snapshotMinX = snapshot.minX();
        snapshotMinY = snapshot.minY();
        snapshotMinZ = snapshot.minZ();
        hasSnapshot = true;
        return true;
    }

    public boolean stretchFace(int face, boolean expand) {
        if (!hasSnapshot || !cache.stretchFace(face, expand)) return false;
        mesh.invalidate(false);
        return true;
    }

    public boolean resize(boolean expand) {
        if (!hasSnapshot || !cache.resize(expand)) return false;
        mesh.invalidate(false);
        return true;
    }

    public boolean rotate(int axis, int quarterTurn) {
        if (!hasSnapshot || !cache.rotate(axis, quarterTurn)) return false;
        rebuildMesh();
        return true;
    }

    public boolean mirror(int axis) {
        if (!hasSnapshot || !cache.mirror(axis)) return false;
        rebuildMesh();
        return true;
    }

    public void tickBake(Minecraft minecraft) {
        var budget = new com.redawn.worldeditpp.operation.task.TickBudget(262144, 4_000_000L);
        try {
            cache.tick(budget);
            if (cache.ready()) mesh.tick(minecraft, budget);
        } catch (RuntimeException exception) {
            WorldEditPlusPlus.LOGGER.warn("Projection preparation failed", exception);
            clearSnapshot();
            ClientFeedback.error("message.worldeditpp.projection.rejected");
        }
    }

    private void rebuildMesh() {
        mesh.invalidate(true);
        snapshotMinX = snapshotMinY = snapshotMinZ = 0;
    }

    private void clearSnapshot() {
        mesh.close();
        cache.clear();
        hasSnapshot = false;
        snapshotMinX = snapshotMinY = snapshotMinZ = 0;
    }
}
