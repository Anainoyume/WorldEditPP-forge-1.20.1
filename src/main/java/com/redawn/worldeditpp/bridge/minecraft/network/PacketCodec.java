package com.redawn.worldeditpp.bridge.minecraft.network;

import java.util.function.BiConsumer;
import java.util.function.Function;

/** Preserves bounded packet encoders/decoders on the 1.20.1 buffer API. */
public record PacketCodec<B, T>(BiConsumer<B, T> encoder, Function<B, T> decoder) {
    public static <B, T> PacketCodec<B, T> unit(T value) {
        return of((buffer, message) -> {}, buffer -> value);
    }
    public static <B, T> PacketCodec<B, T> of(BiConsumer<B, T> encoder, Function<B, T> decoder) {
        return new PacketCodec<>(encoder, decoder);
    }
    public void encode(B buffer, T message) { encoder.accept(buffer, message); }
    public T decode(B buffer) { return decoder.apply(buffer); }
}
