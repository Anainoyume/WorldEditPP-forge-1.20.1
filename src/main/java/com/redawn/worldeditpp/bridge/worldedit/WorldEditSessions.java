package com.redawn.worldeditpp.bridge.worldedit;

import com.sk89q.worldedit.LocalSession;
import net.minecraft.server.level.ServerPlayer;

/** Single adapter for the WorldEdit session of a server player. */
public final class WorldEditSessions {
    public static LocalSession of(ServerPlayer player) {
        return WorldEditBridge.worldEdit().getSessionManager().get(WorldEditPlatform.adaptPlayer(player));
    }

    private WorldEditSessions() { }
}
