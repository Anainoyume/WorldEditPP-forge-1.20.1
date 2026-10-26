package com.redawn.worldeditpp.bridge.worldedit.operation;

import com.redawn.worldeditpp.WorldEditPlusPlus;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;

/** Always retains partial work in history, including when flushing or closing fails. */
public final class EditSessionLifecycle {
    public static boolean rememberAndClose(LocalSession history, EditSession edit) {
        boolean successful = true;
        try { edit.flushSession(); }
        catch (RuntimeException e) { successful = false; WorldEditPlusPlus.LOGGER.error("Edit flush failed", e); }
        try { edit.close(); }
        catch (RuntimeException e) { successful = false; WorldEditPlusPlus.LOGGER.error("Edit close failed", e); }
        try { history.remember(edit); }
        catch (RuntimeException e) { successful = false; WorldEditPlusPlus.LOGGER.error("Partial edit could not be remembered", e); }
        return successful;
    }
    private EditSessionLifecycle() {}
}
