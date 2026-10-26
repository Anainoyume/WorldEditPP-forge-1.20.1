package com.redawn.worldeditpp.bridge.worldedit.history;

import com.sk89q.worldedit.extension.platform.Actor;

/** One permission rule for enqueueing and continuing a history operation. */
final class HistoryPermissions {
    static boolean allows(Actor actor, boolean undo, boolean other) {
        String permission = "worldedit.history." + (undo ? "undo" : "redo");
        return (actor.hasPermission(permission) || actor.hasPermission(permission + ".self"))
                && (!other || actor.hasPermission(permission + ".other"));
    }

    private HistoryPermissions() { }
}
