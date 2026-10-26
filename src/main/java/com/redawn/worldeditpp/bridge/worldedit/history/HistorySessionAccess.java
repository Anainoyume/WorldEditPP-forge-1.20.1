package com.redawn.worldeditpp.bridge.worldedit.history;

import com.redawn.worldeditpp.bridge.worldedit.WorldEditBridge;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.session.SessionManager;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The only adapter for WorldEdit 7.2.15's private history cursor.
 * Reserve without moving the cursor; commit only after every change and flush succeeds.
 */
final class HistorySessionAccess {
    private static final Field HISTORY = field(LocalSession.class, "history");
    private static final Field POINTER = field(LocalSession.class, "historyPointer");
    private static final Field BYPASS = field(EditSession.class, "bypassHistory");
    private static final Field BYPASS_REORDER = field(EditSession.class, "bypassReorderHistory");
    private static final Field SESSIONS = field(SessionManager.class, "sessions");
    private static final Field SESSION, LAST_ACTIVE;
    static {
        try {
            Class<?> holder = Class.forName("com.sk89q.worldedit.session.SessionManager$SessionHolder");
            SESSION = field(holder, "session"); LAST_ACTIVE = field(holder, "lastActive");
        } catch (ClassNotFoundException e) { throw new ExceptionInInitializerError(e); }
    }

    static Field field(Class<?> type, String name) {
        try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("WorldEdit 7.2.15 API mismatch: " + name, e); }
    }
    static Object read(Field field, Object instance) {
        try { return field.get(instance); }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
    @SuppressWarnings("unchecked")
    private static List<EditSession> history(LocalSession session) { return (List<EditSession>) read(HISTORY, session); }
    static Extent bypass(EditSession edit) { return (Extent) read(BYPASS, edit); }
    static Extent bypassReorder(EditSession edit) { return (Extent) read(BYPASS_REORDER, edit); }

    static UUID owner(LocalSession session) {
        SessionManager manager = WorldEditBridge.worldEdit().getSessionManager();
        synchronized (manager) {
            for (var e : ((Map<?, ?>) read(SESSIONS, manager)).entrySet()) {
                if (read(SESSION, e.getValue()) == session) return (UUID) e.getKey();
            }
        }
        throw new IllegalStateException("History session is no longer registered");
    }

    static void keepAlive(UUID owner, LocalSession session) {
        SessionManager manager = WorldEditBridge.worldEdit().getSessionManager();
        synchronized (manager) {
            Object holder = ((Map<?, ?>) read(SESSIONS, manager)).get(owner);
            if (holder == null || read(SESSION, holder) != session) throw new IllegalStateException("History session was replaced");
            try { LAST_ACTIVE.setLong(holder, System.currentTimeMillis()); }
            catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        }
    }

    static Reservation reserve(LocalSession session, boolean undo) {
        int pointer = (Integer) read(POINTER, session);
        List<EditSession> history = history(session);
        int index = undo ? pointer - 1 : pointer;
        if (index < 0 || index >= history.size()) return null;
        return new Reservation(session, List.copyOf(history), pointer, undo ? pointer - 1 : pointer + 1, history.get(index));
    }

    record Reservation(LocalSession session, List<EditSession> entries, int before, int after, EditSession edit) {
        boolean valid() { return (Integer) read(POINTER, session) == before && entries.equals(history(session)); }
        void commit() {
            if (!valid()) throw new IllegalStateException("History changed while an operation was suspended");
            try { POINTER.setInt(session, after); }
            catch (IllegalAccessException e) { throw new IllegalStateException(e); }
        }
    }
    private HistorySessionAccess() {}
}
