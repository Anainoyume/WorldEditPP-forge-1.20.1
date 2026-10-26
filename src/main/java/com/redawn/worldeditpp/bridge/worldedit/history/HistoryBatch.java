package com.redawn.worldeditpp.bridge.worldedit.history;

import com.redawn.worldeditpp.bridge.worldedit.WorldEditPlatform;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;

/** One user command, possibly containing several sequential history entries. */
final class HistoryBatch {
    private final boolean undo;
    private int applied;
    private boolean notified;

    HistoryBatch(boolean undo) { this.undo = undo; }

    /** Counts one committed history entry of this command. */
    void stepApplied() { applied++; }

    /** Reports the whole command once; an exhausted history reports only what was applied. */
    void completed(ServerPlayer player) {
        if (notified) return;
        notified = true;
        var actor = WorldEditPlatform.adaptPlayer(player);
        if (applied > 0) {
            actor.printInfo(TranslatableComponent.of(
                    undo ? "worldedit.undo.undone" : "worldedit.redo.redone",
                    TextComponent.of(applied)));
        } else {
            actor.printError(TranslatableComponent.of(
                    undo ? "worldedit.undo.none" : "worldedit.redo.none"));
        }
    }
}
