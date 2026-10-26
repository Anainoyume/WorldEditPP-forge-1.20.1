package com.redawn.worldeditpp.bridge.worldedit.history;

import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BlockStateHolder;

/** Installed only on external sessions created while their owner's history is reserved. */
public final class HistoryLockedExtent extends AbstractDelegateExtent {
    public HistoryLockedExtent(Extent extent) { super(extent); }
    private static IllegalStateException locked() {
        return new IllegalStateException("A WorldEdit operation is still running; wait for it to finish");
    }
    @Override public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 pos, T block) { throw locked(); }
    @Override public boolean setBiome(BlockVector3 pos, BiomeType biome) { throw locked(); }
    @Override public Entity createEntity(Location location, BaseEntity entity) { throw locked(); }
    // Removal uses Entity.remove() directly; deny enumeration as well as creation.
    @Override public java.util.List<? extends Entity> getEntities() { throw locked(); }
    @Override public java.util.List<? extends Entity> getEntities(com.sk89q.worldedit.regions.Region region) { throw locked(); }
}
