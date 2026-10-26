package com.redawn.worldeditpp.gametest.framework;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class GameSimulation {
    private final MinecraftServer server;
    private final ServerLevel overworld;

    public GameSimulation(MinecraftServer server) {
        this.server = server;
        this.overworld = server.overworld();
    }

    public MinecraftServer server() {
        return server;
    }

    public ServerLevel overworld() {
        return overworld;
    }

    public com.sk89q.worldedit.world.World worldEditWorld() {
        return com.sk89q.worldedit.forge.ForgeAdapter.adapt(overworld);
    }

    public void shutdown() {
        server.halt(false);
    }
}
