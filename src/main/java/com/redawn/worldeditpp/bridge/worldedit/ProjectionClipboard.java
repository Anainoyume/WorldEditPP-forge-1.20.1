package com.redawn.worldeditpp.bridge.worldedit;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import it.unimi.dsi.fastutil.ints.IntArrayList;

/**
 * Block array plus the exact set of non-air cells. Building the projection payload from this
 * list avoids re-scanning millions of empty cells after the initial copy; the copy itself already
 * visits every source cell, so no extra full-volume pass is introduced.
 */
final class ProjectionClipboard extends BlockArrayClipboard {
    private final IntArrayList nonAirCells = new IntArrayList();

    ProjectionClipboard(CuboidRegion region) {
        super(region);
    }

    @Override
    public <B extends BlockStateHolder<B>> boolean setBlock(BlockVector3 position, B block) throws WorldEditException {
        boolean stored = super.setBlock(position, block);
        if (stored && !block.getBlockType().getMaterial().isAir()) {
            BlockVector3 dimensions = getDimensions();
            BlockVector3 local = position.subtract(getMinimumPoint());
            int cell = local.getX() + dimensions.getX() * (local.getY() + dimensions.getY() * local.getZ());
            nonAirCells.add(cell);
        }
        return stored;
    }

    int[] nonAirCells() {
        return nonAirCells.toIntArray();
    }
}
