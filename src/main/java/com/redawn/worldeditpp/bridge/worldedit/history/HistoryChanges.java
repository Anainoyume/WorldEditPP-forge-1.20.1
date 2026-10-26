package com.redawn.worldeditpp.bridge.worldedit.history;

import com.sk89q.worldedit.history.change.*;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.Location;
import java.lang.reflect.Field;

/** Version-specific position lookup, including entity changes without public location accessors. */
final class HistoryChanges {
    private static final Field CREATE_LOCATION = HistorySessionAccess.field(EntityCreate.class, "location");
    private static final Field REMOVE_LOCATION = HistorySessionAccess.field(EntityRemove.class, "location");
    private static final Field CREATE_ENTITY = HistorySessionAccess.field(EntityCreate.class, "entity");
    private static final Field REMOVE_ENTITY = HistorySessionAccess.field(EntityRemove.class, "entity");

    static void retainEntity(Change change, net.minecraft.server.level.ServerLevel level) {
        if (change instanceof ReversedChange reverse) { retainEntity(reverse.original(), level); return; }
        Field field = change.getClass() == EntityCreate.class ? CREATE_ENTITY : change.getClass() == EntityRemove.class ? REMOVE_ENTITY : null;
        if (field == null) return;
        var entity = (com.sk89q.worldedit.entity.Entity) HistorySessionAccess.read(field, change);
        if (entity == null || entity instanceof HistoryEntityReference) return;
        try { field.set(change, new HistoryEntityReference(level, entity)); }
        catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }

    static void checkEntityRemoval(Change change, boolean undo) {
        if (change instanceof ReversedChange reverse) { checkEntityRemoval(reverse.original(), !undo); return; }
        Field field = change instanceof EntityCreate && undo ? CREATE_ENTITY : change instanceof EntityRemove && !undo ? REMOVE_ENTITY : null;
        if (field != null && HistorySessionAccess.read(field, change) instanceof HistoryEntityReference entity) entity.requirePresent();
    }

    static BlockVector3 position(Change change) {
        if (change instanceof ReversedChange reverse) return position(reverse.original());
        if (change instanceof RecoveryEntityChange entity) return entity.position;
        if (change.getClass() == BlockChange.class) return ((BlockChange) change).getPosition();
        if (change.getClass() == BiomeChange3D.class) return ((BiomeChange3D) change).getPosition();
        if (change.getClass() == BiomeChange.class) return ((BiomeChange) change).getPosition().toBlockVector3();
        Field location, entity;
        if (change.getClass() == EntityCreate.class) { location = CREATE_LOCATION; entity = CREATE_ENTITY; }
        else if (change.getClass() == EntityRemove.class) { location = REMOVE_LOCATION; entity = REMOVE_ENTITY; }
        else throw new IllegalArgumentException("Unsupported history change: " + change.getClass().getName());
        var live = (com.sk89q.worldedit.entity.Entity) HistorySessionAccess.read(entity, change);
        return (live == null ? (Location) HistorySessionAccess.read(location, change) : live.getLocation()).toVector().toBlockPoint();
    }
    static net.minecraft.nbt.CompoundTag liveEntitySnapshot(Change change) {
        if (change instanceof ReversedChange reverse) return liveEntitySnapshot(reverse.original());
        if (change instanceof RecoveryEntityChange entity) return entity.data.copy();
        Field entity = change instanceof EntityCreate ? CREATE_ENTITY : change instanceof EntityRemove ? REMOVE_ENTITY : null;
        if (entity == null) return null;
        var live = (com.sk89q.worldedit.entity.Entity) HistorySessionAccess.read(entity, change);
        if (live == null || live.getState() == null || live.getState().getNbtData() == null) return null;
        var state = live.getState();
        var tag = com.redawn.worldeditpp.bridge.worldedit.WorldEditPlatform.toNative(state.getNbtData());
        tag.putString("id", state.getType().getId());
        return tag;
    }

    record ReversedChange(Change original) implements Change {
        @Override public void undo(com.sk89q.worldedit.history.UndoContext context) throws com.sk89q.worldedit.WorldEditException { original.redo(context); }
        @Override public void redo(com.sk89q.worldedit.history.UndoContext context) throws com.sk89q.worldedit.WorldEditException { original.undo(context); }
    }
    static boolean entity(Change change) {
        if (change instanceof ReversedChange reverse) return entity(reverse.original());
        return change instanceof EntityCreate || change instanceof EntityRemove || change instanceof RecoveryEntityChange;
    }
    private HistoryChanges() {}
}
