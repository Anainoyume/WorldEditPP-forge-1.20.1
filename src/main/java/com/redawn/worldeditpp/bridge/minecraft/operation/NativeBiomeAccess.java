package com.redawn.worldeditpp.bridge.minecraft.operation;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.PalettedContainer;

/** Converts block coordinates to 4x4x4 biome cells, including negative coordinates. */
public final class NativeBiomeAccess {
    public static boolean set(ServerLevel level, int x, int y, int z, String biomeId) {
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) return false;
        var chunk = level.getChunk(x >> 4,z >> 4);
        var container = (PalettedContainer<Holder<Biome>>) chunk.getSection(chunk.getSectionIndex(y)).getBiomes();
        var biome = level.registryAccess().registryOrThrow(Registries.BIOME)
                .getHolderOrThrow(ResourceKey.create(Registries.BIOME,new ResourceLocation(biomeId)));
        container.getAndSet((x >> 2) & 3,(y >> 2) & 3,(z >> 2) & 3,biome);
        chunk.setUnsaved(true);
        return true;
    }
    private NativeBiomeAccess() {}
}
