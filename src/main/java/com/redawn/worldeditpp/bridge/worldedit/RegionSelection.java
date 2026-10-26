package com.redawn.worldeditpp.bridge.worldedit;

import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;

/** A native WorldEdit region plus the capabilities WorldEdit++ may safely offer for it. */
public record RegionSelection(Region region, SelectionKind kind) {
    public RegionSelection {
        if (region == null) throw new IllegalArgumentException("region");
        if (kind == null) throw new IllegalArgumentException("kind");
    }

    public boolean isCuboid() {
        return kind == SelectionKind.CUBOID;
    }

    public CuboidRegion cuboid() {
        if (!(region instanceof CuboidRegion cuboid)) {
            throw new IllegalStateException("not a cuboid selection: " + kind);
        }
        return cuboid;
    }
}
