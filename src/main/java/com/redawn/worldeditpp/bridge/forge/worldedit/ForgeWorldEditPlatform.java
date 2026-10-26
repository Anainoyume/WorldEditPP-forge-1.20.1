package com.redawn.worldeditpp.bridge.forge.worldedit;

import com.redawn.worldeditpp.bridge.worldedit.WorldEditPlatform;
import com.sk89q.worldedit.forge.ForgeAdapter;
import com.sk89q.worldedit.forge.internal.NBTConverter;

/** The only production dependency on WorldEdit's loader-specific adapter and NBT converter. */
public final class ForgeWorldEditPlatform implements WorldEditPlatform.Backend {
    @Override
    public com.sk89q.worldedit.world.World adapt(net.minecraft.server.level.ServerLevel world) {
        return ForgeAdapter.adapt(world);
    }
    @Override
    public net.minecraft.server.level.ServerLevel adapt(com.sk89q.worldedit.world.World world) {
        return ForgeAdapter.adapt(world);
    }
    @Override
    public com.sk89q.worldedit.entity.Player adaptPlayer(net.minecraft.server.level.ServerPlayer player) {
        return ForgeAdapter.adaptPlayer(player);
    }
    @Override
    public net.minecraft.world.level.block.state.BlockState adapt(com.sk89q.worldedit.world.block.BlockState state) {
        return ForgeAdapter.adapt(state);
    }
    @Override
    public com.sk89q.worldedit.world.block.BlockState adapt(net.minecraft.world.level.block.state.BlockState state) {
        return ForgeAdapter.adapt(state);
    }
    @Override
    public net.minecraft.world.level.block.Block adapt(com.sk89q.worldedit.world.block.BlockType type) {
        return ForgeAdapter.adapt(type);
    }
    @Override
    public com.sk89q.worldedit.math.BlockVector3 adapt(net.minecraft.core.BlockPos pos) {
        return ForgeAdapter.adapt(pos);
    }
    @Override
    public net.minecraft.core.BlockPos toBlockPos(com.sk89q.worldedit.math.BlockVector3 pos) {
        return ForgeAdapter.toBlockPos(pos);
    }
    @Override
    public net.minecraft.nbt.CompoundTag toNative(com.sk89q.jnbt.CompoundTag tag) {
        return NBTConverter.toNative(tag);
    }
    @Override
    public com.sk89q.jnbt.CompoundTag fromNative(net.minecraft.nbt.CompoundTag tag) {
        return NBTConverter.fromNative(tag);
    }
}
