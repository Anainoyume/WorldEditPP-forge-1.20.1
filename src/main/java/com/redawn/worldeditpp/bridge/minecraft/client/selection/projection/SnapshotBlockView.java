package com.redawn.worldeditpp.bridge.minecraft.client.selection.projection;

import com.redawn.worldeditpp.bridge.minecraft.network.SelectionBlocksPayload;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

/**
 * 快照方块视图：供原版 face culling（Block.shouldRenderFace）与流体
 * tesselation（renderLiquid）读取邻居方块。选区内返回快照方块，区域外视为空气。
 */
final class SnapshotBlockView implements BlockAndTintGetter {
    private final ProjectionClipboardCache cache;
    private final Map<Integer, BlockEntity> blockEntities;
    private final int minX, minY, minZ, sizeX, sizeY, sizeZ;
    private final int worldOriginX, worldOriginY, worldOriginZ;
    private final ClientLevel delegate;
    private final BlockState[] neighbourhood;
    private final int strideX, strideY;
    private int filled;

    SnapshotBlockView(ProjectionClipboardCache cache, Map<Integer, BlockEntity> blockEntities,
                      int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ,
                      ClientLevel delegate, int worldOriginX, int worldOriginY, int worldOriginZ) {
        this.cache = cache;
        this.blockEntities = blockEntities;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.delegate = delegate;
        this.worldOriginX = worldOriginX;
        this.worldOriginY = worldOriginY;
        this.worldOriginZ = worldOriginZ;
        strideX = sizeX + 2;
        strideY = sizeY + 2;
        neighbourhood = new BlockState[strideX * strideY * (sizeZ + 2)];
    }

    boolean prepare(com.redawn.worldeditpp.operation.task.TickBudget budget) {
        while (filled < neighbourhood.length && budget.take()) {
            int i = filled++;
            neighbourhood[i] = cache.block(minX - 1 + i % strideX,
                    minY - 1 + i / strideX % strideY, minZ - 1 + i / (strideX * strideY));
        }
        return filled == neighbourhood.length;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        int x = pos.getX() - minX + 1, y = pos.getY() - minY + 1, z = pos.getZ() - minZ + 1;
        if (filled == neighbourhood.length && x >= 0 && x < strideX && y >= 0 && y < strideY && z >= 0 && z < sizeZ + 2) {
            return neighbourhood[x + strideX * (y + strideY * z)];
        }
        if (delegate != null && (x < 0 || x >= strideX || y < 0 || y >= strideY || z < 0 || z >= sizeZ + 2)) {
            // Outside the snapshot the paste does not replace blocks. If the live world already
            // has a block there, that face is hidden in the final result as well.
            return delegate.getBlockState(new BlockPos(
                    worldOriginX + pos.getX(), worldOriginY + pos.getY(), worldOriginZ + pos.getZ()));
        }
        return cache.block(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        int cell = cellAt(pos);
        return cell < 0 ? null : blockEntities.get(cell);
    }

    @Override
    public float getShade(Direction direction, boolean shaded) {
        return 1.0F;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return delegate.getLightEngine();
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        return delegate.getBlockTint(pos, resolver);
    }

    @Override
    public int getHeight() {
        return delegate.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return delegate.getMinBuildHeight();
    }

    private int cellAt(BlockPos pos) {
        int x = pos.getX() - minX;
        int y = pos.getY() - minY;
        int z = pos.getZ() - minZ;
        if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z < 0 || z >= sizeZ) {
            return -1;
        }
        return SelectionBlocksPayload.cellIndex(x, y, z, sizeX, sizeY);
    }
}
