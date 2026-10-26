package com.redawn.worldeditpp.bridge.worldedit.clipboard;

import com.sk89q.worldedit.extent.AbstractDelegateExtent;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.*;
import java.util.List;

/** Read-only repeated template; resizing allocates no block array and never nests wrappers. */
public final class RepeatedClipboard extends AbstractDelegateExtent implements Clipboard {
    private final Clipboard template;
    private final BlockVector3 dimensions;
    private BlockVector3 origin;

    public RepeatedClipboard(Clipboard source, BlockVector3 dimensions) {
        super(template(source));
        this.template = template(source);
        this.dimensions = dimensions;
        var tile = template.getDimensions();
        if (dimensions.getX() < 1 || dimensions.getY() < 1 || dimensions.getZ() < 1
                || dimensions.getX() % tile.getX() != 0 || dimensions.getY() % tile.getY() != 0 || dimensions.getZ() % tile.getZ() != 0)
            throw new IllegalArgumentException("Repeated dimensions must be whole tiles");
        origin = dimensions.subtract(1,1,1).divide(2);
    }
    public static Clipboard template(Clipboard clipboard) {
        return clipboard instanceof RepeatedClipboard repeated ? repeated.template : clipboard;
    }
    public int templateVolume() {
        var size = template.getDimensions();
        return Math.multiplyExact(Math.multiplyExact(size.getX(), size.getY()), size.getZ());
    }
    public int templateCell(int x, int y, int z) {
        var size = template.getDimensions();
        int localX = Math.floorMod(x, size.getX());
        int localY = Math.floorMod(y, size.getY());
        int localZ = Math.floorMod(z, size.getZ());
        return localX + size.getX() * (localY + size.getY() * localZ);
    }
    public BaseBlock templateFullBlock(int cell) {
        var size = template.getDimensions();
        if (cell < 0 || cell >= templateVolume()) throw new IndexOutOfBoundsException(cell);
        int x = cell % size.getX();
        int yz = cell / size.getX();
        int y = yz % size.getY();
        int z = yz / size.getY();
        return template.getFullBlock(template.getMinimumPoint().add(x, y, z));
    }
    private BlockVector3 source(BlockVector3 pos) {
        var size = template.getDimensions();
        return template.getMinimumPoint().add(Math.floorMod(pos.getX(), size.getX()),
                Math.floorMod(pos.getY(), size.getY()), Math.floorMod(pos.getZ(), size.getZ()));
    }
    private boolean contains(BlockVector3 pos) {
        return pos.getX() >= 0 && pos.getY() >= 0 && pos.getZ() >= 0
                && pos.getX() < dimensions.getX() && pos.getY() < dimensions.getY() && pos.getZ() < dimensions.getZ();
    }
    @Override public BlockState getBlock(BlockVector3 pos) {
        return contains(pos) ? template.getBlock(source(pos)) : BlockTypes.AIR.getDefaultState();
    }
    @Override public BaseBlock getFullBlock(BlockVector3 pos) {
        return contains(pos) ? template.getFullBlock(source(pos)) : BlockTypes.AIR.getDefaultState().toBaseBlock();
    }
    @Override public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 pos, T block) {
        throw new UnsupportedOperationException("Repeated clipboard is read-only");
    }
    @Override public BlockVector3 getMinimumPoint() { return BlockVector3.ZERO; }
    @Override public BlockVector3 getMaximumPoint() { return dimensions.subtract(1,1,1); }
    @Override public Region getRegion() { return new CuboidRegion(getMinimumPoint(), getMaximumPoint()); }
    @Override public BlockVector3 getDimensions() { return dimensions; }
    @Override public BlockVector3 getOrigin() { return origin; }
    @Override public void setOrigin(BlockVector3 origin) { this.origin = origin; }
    @Override public boolean setBiome(BlockVector3 pos, com.sk89q.worldedit.world.biome.BiomeType biome) {
        throw new UnsupportedOperationException("Repeated clipboard is read-only");
    }
    @Override public com.sk89q.worldedit.entity.Entity createEntity(com.sk89q.worldedit.util.Location location, com.sk89q.worldedit.entity.BaseEntity state) {
        throw new UnsupportedOperationException("Repeated clipboard is read-only");
    }
    @Override public List<? extends com.sk89q.worldedit.entity.Entity> getEntities() { return List.of(); }
    @Override public List<? extends com.sk89q.worldedit.entity.Entity> getEntities(Region region) { return List.of(); }
}
