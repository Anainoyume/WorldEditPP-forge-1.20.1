package com.redawn.worldeditpp.bridge.minecraft.mixin.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import java.nio.ByteBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.20.1 has no BufferBuilder.close(): discard() does not free its malloc allocation. */
@Mixin(BufferBuilder.class)
public interface BufferBuilderStorageMixin {
    @Accessor("buffer")
    ByteBuffer worldeditpp$storage();

    @Accessor("buffer")
    void worldeditpp$setStorage(ByteBuffer buffer);
}
