package com.redawn.worldeditpp.bridge.worldedit;

import com.redawn.worldeditpp.network.SelectionProjectionLimits;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;

/** Materializes a holder transform through the WorldEdit 7.2 public paste API. */
final class ClipboardTransforms {
    private ClipboardTransforms() {}

    static Clipboard bake(ClipboardHolder holder) throws WorldEditException {
        Clipboard source = holder.getClipboard();
        if (holder.getTransform().isIdentity()) return source;
        BlockVector3 low = source.getMinimumPoint();
        BlockVector3 high = source.getMaximumPoint();
        Vector3 origin = source.getOrigin().toVector3();
        Vector3 min = null;
        Vector3 max = null;
        for (int mask = 0; mask < 8; mask++) {
            Vector3 corner = Vector3.at((mask & 1) == 0 ? low.getX() : high.getX(),
                    (mask & 2) == 0 ? low.getY() : high.getY(),
                    (mask & 4) == 0 ? low.getZ() : high.getZ());
            Vector3 point = holder.getTransform().apply(corner.subtract(origin)).add(origin);
            min = min == null ? point : min.getMinimum(point);
            max = max == null ? point : max.getMaximum(point);
        }
        CuboidRegion bounds = new CuboidRegion(min.floor().toBlockPoint(), max.ceil().toBlockPoint());
        if (!SelectionProjectionLimits.hasValidVolume(bounds.getWidth(), bounds.getHeight(), bounds.getLength())) {
            throw new IllegalArgumentException("Transformed clipboard exceeds projection limits");
        }
        BlockArrayClipboard result = new BlockArrayClipboard(bounds);
        result.setOrigin(source.getOrigin());
        Operations.complete(holder.createPaste(result).to(source.getOrigin())
                .copyEntities(false).copyBiomes(false).build());
        return result;
    }
}
