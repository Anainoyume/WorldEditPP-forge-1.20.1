package com.redawn.worldeditpp.bridge.worldedit.history;

import com.redawn.worldeditpp.bridge.worldedit.WorldEditPlatform;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.util.Location;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

/** History keeps an identity across chunk unloads, rather than ForgeEntity's weak native reference. */
final class HistoryEntityReference implements Entity {
    private final ServerLevel level;
    private final UUID id;
    private BaseEntity snapshot;
    private Location location;

    HistoryEntityReference(ServerLevel level, Entity original) {
        this.level = level;
        snapshot = original.getState();
        if (snapshot == null || snapshot.getNbtData() == null)
            throw new IllegalStateException("Entity history reference expired; load its original chunk before retrying, or cancel");
        var data = WorldEditPlatform.toNative(snapshot.getNbtData());
        if (!data.hasUUID("UUID")) throw new IllegalStateException("Entity history has no stable UUID");
        id = data.getUUID("UUID");
        location = original.getLocation();
    }

    void requirePresent() {
        if (level.getEntity(id) == null)
            throw new IllegalStateException("History entity " + id + " is unavailable; resume after loading its chunk, or cancel");
    }
    @Override public BaseEntity getState() {
        var entity = level.getEntity(id);
        if (entity != null) {
            var data = entity.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            snapshot = new BaseEntity(snapshot.getType(), WorldEditPlatform.fromNative(data));
        }
        return snapshot;
    }
    @Override public Location getLocation() {
        var entity = level.getEntity(id);
        if (entity != null) location = new Location(getExtent(),
                com.sk89q.worldedit.math.Vector3.at(entity.getX(), entity.getY(), entity.getZ()), entity.getYRot(), entity.getXRot());
        return location;
    }
    @Override public Extent getExtent() { return WorldEditPlatform.adapt(level); }
    @Override public boolean setLocation(Location location) { return false; }
    @Override public boolean remove() {
        requirePresent();
        getState(); getLocation();
        level.getEntity(id).discard();
        return true;
    }
    @Override public <T> T getFacet(Class<? extends T> type) { return null; }
}
