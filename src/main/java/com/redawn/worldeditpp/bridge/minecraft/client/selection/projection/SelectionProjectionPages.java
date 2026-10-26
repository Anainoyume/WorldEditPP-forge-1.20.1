package com.redawn.worldeditpp.bridge.minecraft.client.selection.projection;

import com.redawn.worldeditpp.client.selection.projection.ProjectionPage;
import com.redawn.worldeditpp.operation.task.TickBudget;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;

/** Plans, bakes and retires pages incrementally. Interior VBOs survive repeated resizing. */
final class SelectionProjectionPages implements AutoCloseable {
    private final Supplier<ProjectionClipboardCache> source;
    private final IntSupplier originX, originY, originZ;
    private final Map<Long, SelectionProjectionMesh> pages = new LinkedHashMap<>();
    private final ProjectionMeshBuffers scratch = new ProjectionMeshBuffers();
    private Iterator<SelectionProjectionMesh> cleanup;
    private SelectionProjectionMesh pending;
    private boolean resetContent, planning;
    private int x, y, z;

    SelectionProjectionPages(Supplier<ProjectionClipboardCache> source,
                             IntSupplier originX, IntSupplier originY, IntSupplier originZ) {
        this.source = source;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
    }

    void invalidate(boolean contentChanged) {
        resetContent |= contentChanged;
        cleanup = pages.values().iterator();
        // A wheel burst must not continually discard an unchanged interior page.
        var cache = source.get();
        if (pending != null && (contentChanged || !pending.page().matches(cache.sizeX(), cache.sizeY(), cache.sizeZ()))) {
            pending.close(); pending = null;
        }
        x = y = z = 0;
        planning = true;
    }

    boolean isReady() { return source.get().ready() && !planning && cleanup == null && pending == null; }

    Iterable<SelectionProjectionMesh> meshes() {
        var cache = source.get();
        return () -> pages.values().stream().filter(mesh -> !resetContent
                && mesh.page().matches(cache.sizeX(), cache.sizeY(), cache.sizeZ())).iterator();
    }

    void tick(Minecraft minecraft, TickBudget budget) {
        var cache = source.get();
        if (cleanup != null) {
            while (cleanup.hasNext() && budget.take()) {
                var mesh = cleanup.next();
                if (resetContent || !mesh.page().matches(cache.sizeX(), cache.sizeY(), cache.sizeZ())) {
                    mesh.close(); cleanup.remove();
                }
            }
            if (cleanup.hasNext()) return;
            cleanup = null; resetContent = false;
        }
        while (planning && budget.take()) {
            // Pending may survive a resize while the planner restarts from the origin.
            if (pending != null) {
                pending.tickBake(minecraft, budget);
                if (!pending.isReady()) return;
                pages.put(pending.page().key(), pending);
                pending = null;
                continue;
            }
            if (pending == null) {
                var page = ProjectionPage.at(x, y, z, cache.sizeX(), cache.sizeY(), cache.sizeZ());
                if (pages.containsKey(page.key())) { advance(cache); continue; }
                pending = new SelectionProjectionMesh(scratch);
                pending.replace(cache, page, minecraft.level,
                        originX.getAsInt(), originY.getAsInt(), originZ.getAsInt());
            }
            pending.tickBake(minecraft, budget);
            if (!pending.isReady()) return;
            pages.put(pending.page().key(), pending);
            pending = null;
            advance(cache);
            // Continue while the time budget permits; cheap/empty pages need no artificial delay.
        }
    }

    private void advance(ProjectionClipboardCache cache) {
        x += ProjectionPage.EDGE;
        if (x >= cache.sizeX()) {
            x = 0; y += ProjectionPage.EDGE;
            if (y >= cache.sizeY()) {
                y = 0; z += ProjectionPage.EDGE;
                if (z >= cache.sizeZ()) planning = false;
            }
        }
    }

    @Override public void close() {
        if (pending != null) pending.close();
        pending = null;
        pages.values().forEach(SelectionProjectionMesh::close);
        scratch.close();
        pages.clear(); cleanup = null; resetContent = false; planning = false;
    }
}

