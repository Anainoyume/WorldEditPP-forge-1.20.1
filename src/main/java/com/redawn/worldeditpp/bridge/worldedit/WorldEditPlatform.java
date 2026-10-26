package com.redawn.worldeditpp.bridge.worldedit;

/** Server-side conversion boundary. Never load a WorldEdit registry from client rendering. */
public final class WorldEditPlatform {
    public interface Backend {
        com.sk89q.worldedit.world.World adapt(net.minecraft.server.level.ServerLevel world);
        net.minecraft.server.level.ServerLevel adapt(com.sk89q.worldedit.world.World world);
        com.sk89q.worldedit.entity.Player adaptPlayer(net.minecraft.server.level.ServerPlayer player);
        net.minecraft.world.level.block.state.BlockState adapt(com.sk89q.worldedit.world.block.BlockState state);
        com.sk89q.worldedit.world.block.BlockState adapt(net.minecraft.world.level.block.state.BlockState state);
        net.minecraft.world.level.block.Block adapt(com.sk89q.worldedit.world.block.BlockType type);
        com.sk89q.worldedit.math.BlockVector3 adapt(net.minecraft.core.BlockPos pos);
        net.minecraft.core.BlockPos toBlockPos(com.sk89q.worldedit.math.BlockVector3 pos);
        net.minecraft.nbt.CompoundTag toNative(com.sk89q.jnbt.CompoundTag tag);
        com.sk89q.jnbt.CompoundTag fromNative(net.minecraft.nbt.CompoundTag tag);
    }
    private static Backend backend;
    public static void install(Backend value) {
        if (backend != null) throw new IllegalStateException("WorldEdit platform already installed");
        backend = java.util.Objects.requireNonNull(value);
    }
    private static Backend backend() {
        return java.util.Objects.requireNonNull(backend, "WorldEdit platform not installed");
    }
    public static com.sk89q.worldedit.world.World adapt(net.minecraft.server.level.ServerLevel world) {
        return backend().adapt(world);
    }
    public static net.minecraft.server.level.ServerLevel adapt(com.sk89q.worldedit.world.World world) {
        return backend().adapt(world);
    }
    public static com.sk89q.worldedit.entity.Player adaptPlayer(net.minecraft.server.level.ServerPlayer player) {
        return backend().adaptPlayer(player);
    }
    public static net.minecraft.world.level.block.state.BlockState adapt(com.sk89q.worldedit.world.block.BlockState state) {
        return backend().adapt(state);
    }
    public static com.sk89q.worldedit.world.block.BlockState adapt(net.minecraft.world.level.block.state.BlockState state) {
        return backend().adapt(state);
    }
    public static net.minecraft.world.level.block.Block adapt(com.sk89q.worldedit.world.block.BlockType type) {
        return backend().adapt(type);
    }
    public static com.sk89q.worldedit.math.BlockVector3 adapt(net.minecraft.core.BlockPos pos) {
        return backend().adapt(pos);
    }
    public static net.minecraft.core.BlockPos toBlockPos(com.sk89q.worldedit.math.BlockVector3 pos) {
        return backend().toBlockPos(pos);
    }
    public static net.minecraft.nbt.CompoundTag toNative(com.sk89q.jnbt.CompoundTag tag) {
        return backend().toNative(tag);
    }
    public static com.sk89q.jnbt.CompoundTag fromNative(net.minecraft.nbt.CompoundTag tag) {
        return backend().fromNative(tag);
    }
    private WorldEditPlatform() {}
}
