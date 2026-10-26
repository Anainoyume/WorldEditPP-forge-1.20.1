package com.redawn.worldeditpp.bridge.worldedit;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;

/** Shared allocation and copying rules for origin-centered projection clipboards. */
final class ClipboardBuffers {
    static ProjectionClipboard empty(BlockVector3 dimensions) {
        ProjectionClipboard clipboard = new ProjectionClipboard(
                new CuboidRegion(BlockVector3.ZERO, dimensions.subtract(1, 1, 1)));
        clipboard.setOrigin(BlockVector3.at(
                (dimensions.getX() - 1) / 2,
                (dimensions.getY() - 1) / 2,
                (dimensions.getZ() - 1) / 2));
        return clipboard;
    }

    static ProjectionClipboard copyOf(Clipboard source) throws WorldEditException {
        ProjectionClipboard destination = empty(source.getDimensions());
        copyRegion(source, source.getRegion(), destination, BlockVector3.ZERO);
        return destination;
    }

    /** Copies one region without entities or biomes; {@code source} may be an EditSession. */
    static void copyRegion(Extent source, Region region, Clipboard destination, BlockVector3 offset)
            throws WorldEditException {
        ForwardExtentCopy copy = new ForwardExtentCopy(source, region, destination, offset);
        copy.setCopyingEntities(false);
        copy.setCopyingBiomes(false);
        Operations.completeLegacy(copy);
    }

    private ClipboardBuffers() { }
}
