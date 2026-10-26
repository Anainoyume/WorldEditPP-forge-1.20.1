package com.redawn.worldeditpp.bridge.worldedit.operation;

import com.redawn.worldeditpp.bridge.minecraft.operation.ChunkLease;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.ConvexPolyhedralRegion;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.polyhedron.Triangle;
import java.util.Set;
import net.minecraft.core.BlockPos;

/**
 * Chunk-major cursor for region fills. Unlike {@link Region#iterator()}, which walks the whole
 * selection Y-layer by Y-layer, this visits all 16³ sections of one chunk before moving on, so
 * the chunk lease and the underlying block writes stay cache-local. It never materializes a
 * two-million-position array; only the current section bounds and cell are kept.
 */
final class RegionSectionCursor {
    private final Region region;
    private final boolean cuboid;
    private final ChunkLease chunks;
    private final Set<Long> skippedSections;
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private final int minSectionX, minSectionY, minSectionZ;
    private final int maxSectionX, maxSectionY, maxSectionZ;
    private final long total;
    private int sectionX, sectionY, sectionZ;
    private int cellX, cellY, cellZ, cellMaxX, cellMaxY, cellMaxZ;
    private boolean sectionReady, finished, waiting;
    private long processed;

    RegionSectionCursor(Region region, ChunkLease chunks, Set<Long> skippedSections) {
        this.region = region;
        this.cuboid = region instanceof CuboidRegion;
        this.chunks = chunks;
        this.skippedSections = skippedSections;
        minX = region.getMinimumPoint().getX();
        minY = region.getMinimumPoint().getY();
        minZ = region.getMinimumPoint().getZ();
        maxX = region.getMaximumPoint().getX();
        maxY = region.getMaximumPoint().getY();
        maxZ = region.getMaximumPoint().getZ();
        minSectionX = minX >> 4;
        minSectionY = minY >> 4;
        minSectionZ = minZ >> 4;
        maxSectionX = maxX >> 4;
        maxSectionY = maxY >> 4;
        maxSectionZ = maxZ >> 4;
        total = region.getVolume();
        sectionX = minSectionX;
        sectionY = minSectionY;
        sectionZ = minSectionZ;
    }

    /** @return the next block to write, or null when waiting for a chunk or finished */
    BlockVector3 next() {
        if (finished) return null;
        while (true) {
            if (!sectionReady) {
                if (!prepareSection()) return null;
            }
            if (cellX > cellMaxX) {
                if (!nextCellLayer()) {
                    advanceSection();
                    continue;
                }
            }
            if (!contains(cellX, cellY, cellZ)) {
                advanceCell();
                continue;
            }
            BlockVector3 position = BlockVector3.at(cellX, cellY, cellZ);
            advanceCell();
            processed++;
            return position;
        }
    }

    boolean waiting() { return waiting; }
    boolean finished() { return finished; }
    long processed() { return processed; }
    long total() { return total; }

    private boolean prepareSection() {
        waiting = false;
        while (!finished) {
            int sx0 = sectionX << 4;
            int sy0 = sectionY << 4;
            int sz0 = sectionZ << 4;
            int x0 = Math.max(minX, sx0), x1 = Math.min(maxX, sx0 + 15);
            int y0 = Math.max(minY, sy0), y1 = Math.min(maxY, sy0 + 15);
            int z0 = Math.max(minZ, sz0), z1 = Math.min(maxZ, sz0 + 15);
            if (x0 > x1 || y0 > y1 || z0 > z1) {
                advanceSection();
                continue;
            }
            long volume = (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
            if (cuboid && skippedSections.contains(sectionKey(sectionX, sectionY, sectionZ))) {
                processed += volume;
                advanceSection();
                continue;
            }
            if (!chunks.ready(sx0, sz0)) {
                waiting = true;
                return false;
            }
            cellX = x0;
            cellY = y0;
            cellZ = z0;
            cellMaxX = x1;
            cellMaxY = y1;
            cellMaxZ = z1;
            sectionReady = true;
            return true;
        }
        return false;
    }

    /** Returns false when the current X row is exhausted and the cursor moved to the next Z row. */
    private boolean nextCellLayer() {
        if (cellX > cellMaxX) {
            cellX = Math.max(minX, sectionX << 4);
            if (++cellZ > cellMaxZ) {
                cellZ = Math.max(minZ, sectionZ << 4);
                if (++cellY > cellMaxY) return false;
            }
        }
        return true;
    }

    private void advanceCell() {
        cellX++;
    }

    private boolean contains(int x, int y, int z) {
        return accepts(region, cuboid, BlockVector3.at(x, y, z));
    }

    static boolean accepts(Region region, boolean cuboid, BlockVector3 position) {
        // A cuboid's bounding box is already exact. Every other Region must be
        // queried cell-by-cell; returning true here silently turned polyhedral,
        // cylindrical and 2D polygon selections into their bounding cuboids.
        if (region instanceof ConvexPolyhedralRegion convex) {
            // WorldEdit 7.2.15's ConvexPolyhedralRegion.contains keeps a
            // lastTriangle cache that can survive the final addVertex() call and
            // reject an inside point. Querying the triangles directly avoids that
            // stale cache without mutating WorldEdit state.
            Vector3 point = position.toVector3();
            for (Triangle triangle : convex.getTriangles()) {
                if (triangle.above(point)) return false;
            }
            return true;
        }
        return cuboid || region.contains(position);
    }

    private void advanceSection() {
        sectionReady = false;
        if (++sectionY > maxSectionY) {
            sectionY = minSectionY;
            if (++sectionX > maxSectionX) {
                sectionX = minSectionX;
                if (++sectionZ > maxSectionZ) finished = true;
            }
        }
    }

    private static long sectionKey(int sectionX, int sectionY, int sectionZ) {
        return BlockPos.asLong(sectionX, sectionY, sectionZ);
    }
}
