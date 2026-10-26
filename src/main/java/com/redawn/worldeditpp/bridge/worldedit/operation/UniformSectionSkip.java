package com.redawn.worldeditpp.bridge.worldedit.operation;

import com.redawn.worldeditpp.bridge.worldedit.WorldEditPlatform;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.block.BaseBlock;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Conservative section skip for uniform fills. It never writes a section; it only proves that a
 * fully covered 16³ section already contains the target state, so the caller can advance the
 * cursor without producing history entries or block writes.
 */
final class UniformSectionSkip {
    static Set<Long> find(ServerLevel level, Region region, BaseBlock target) {
        if (!(region instanceof CuboidRegion)) return Collections.emptySet();
        if (target.getNbtData() != null) return Collections.emptySet();
        var nativeTarget = WorldEditPlatform.adapt(target.toImmutableState());
        if (nativeTarget.hasBlockEntity()) return Collections.emptySet();

        int minX = region.getMinimumPoint().getX();
        int minY = region.getMinimumPoint().getY();
        int minZ = region.getMinimumPoint().getZ();
        int maxX = region.getMaximumPoint().getX();
        int maxY = region.getMaximumPoint().getY();
        int maxZ = region.getMaximumPoint().getZ();
        Set<Long> result = new HashSet<>();
        for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
            int sy0 = sy << 4;
            if (sy0 < minY || sy0 + 15 > maxY) continue;
            for (int sz = minZ >> 4; sz <= maxZ >> 4; sz++) {
                int sz0 = sz << 4;
                if (sz0 < minZ || sz0 + 15 > maxZ) continue;
                for (int sx = minX >> 4; sx <= maxX >> 4; sx++) {
                    int sx0 = sx << 4;
                    if (sx0 < minX || sx0 + 15 > maxX) continue;
                    LevelChunk chunk = level.getChunkSource().getChunkNow(sx, sz);
                    if (chunk == null) continue;
                    int sectionIndex = chunk.getSectionIndex(sy0);
                    if (sectionIndex < 0 || sectionIndex >= chunk.getSections().length) continue;
                    LevelChunkSection section = chunk.getSection(sectionIndex);
                    if (section.hasOnlyAir() && nativeTarget.isAir()) {
                        result.add(key(sx0, sy0, sz0));
                        continue;
                    }
                    boolean[] same = {true};
                    section.getStates().count((state, count) -> {
                        if (!state.equals(nativeTarget)) same[0] = false;
                    });
                    if (same[0]) result.add(key(sx0, sy0, sz0));
                }
            }
        }
        return result;
    }

    static long key(BlockVector3 position) {
        return key(position.getX() & ~15, position.getY() & ~15, position.getZ() & ~15);
    }

    private static long key(int x, int y, int z) {
        return BlockPos.asLong(x >> 4, y >> 4, z >> 4);
    }

    private UniformSectionSkip() { }
}
