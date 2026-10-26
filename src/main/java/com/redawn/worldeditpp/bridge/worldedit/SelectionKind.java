package com.redawn.worldeditpp.bridge.worldedit;

import com.sk89q.worldedit.regions.ConvexPolyhedralRegion;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.CylinderRegion;
import com.sk89q.worldedit.regions.EllipsoidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;

/**
 * Shape-aware classification for a native WorldEdit selection.
 *
 * <p>WorldEdit++'s cuboid gizmo, projection and clipboard-adjustment features are cuboid
 * only. They must use {@link #supportsCuboidOverlay()} instead of assuming every native
 * selection is a cuboid. Other native WorldEdit commands operate on {@link Region} directly.
 */
public enum SelectionKind {
    CUBOID(true),
    POLYGONAL_2D(false),
    CONVEX_POLYHEDRAL(false),
    CYLINDER(false),
    ELLIPSOID(false),
    UNKNOWN(false);

    private final boolean cuboidOverlay;

    SelectionKind(boolean cuboidOverlay) {
        this.cuboidOverlay = cuboidOverlay;
    }

    public boolean supportsCuboidOverlay() {
        return cuboidOverlay;
    }

    public static SelectionKind of(Region region) {
        if (region instanceof CuboidRegion) return CUBOID;
        if (region instanceof Polygonal2DRegion) return POLYGONAL_2D;
        if (region instanceof ConvexPolyhedralRegion) return CONVEX_POLYHEDRAL;
        if (region instanceof CylinderRegion) return CYLINDER;
        if (region instanceof EllipsoidRegion) return ELLIPSOID;
        return UNKNOWN;
    }
}
