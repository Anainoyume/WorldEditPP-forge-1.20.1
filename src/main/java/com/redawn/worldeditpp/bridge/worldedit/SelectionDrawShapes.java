package com.redawn.worldeditpp.bridge.worldedit;

import com.redawn.worldeditpp.mode.DrawMode;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionIntersection;
import java.util.ArrayList;

/** Candidate geometry only: independent of materials, matching and block state transforms. */
public final class SelectionDrawShapes {
    public static Region region(Region selection, DrawMode mode) {
        return switch (mode) {
            case SET -> selection;
            case WALLS -> walls(selection);
        };
    }

    private static Region walls(Region selection) {
        var min = selection.getMinimumPoint();
        var max = selection.getMaximumPoint();
        var world = selection.getWorld();
        var faces = new ArrayList<Region>();
        // WorldEdit getWalls() concatenates four full faces, repeating their shared edges.
        // Partition the identical boundary into disjoint faces to avoid duplicate writes/history.
        faces.add(new CuboidRegion(world, min, max.withX(min.getX())));
        if (max.getX() > min.getX()) faces.add(new CuboidRegion(world, min.withX(max.getX()), max));
        if ((long) max.getX() - min.getX() > 1) {
            var innerMin = min.withX(min.getX() + 1);
            var innerMax = max.withX(max.getX() - 1);
            faces.add(new CuboidRegion(world, innerMin, innerMax.withZ(min.getZ())));
            if (max.getZ() > min.getZ()) {
                faces.add(new CuboidRegion(world, innerMin.withZ(max.getZ()), innerMax));
            }
        }
        return new RegionIntersection(faces);
    }

    private SelectionDrawShapes() { }
}
