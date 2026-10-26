package com.redawn.worldeditpp.bridge.worldedit.history;

import com.sk89q.worldedit.history.UndoContext;
import com.sk89q.worldedit.history.change.Change;
import com.sk89q.worldedit.math.BlockVector3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;

/** UUID-preserving, idempotent recovery of an entity after a clean server shutdown. */
final class RecoveryEntityChange implements Change {
    final ServerLevel level;
    final CompoundTag data;
    final boolean afterPresent;
    final BlockVector3 position;

    RecoveryEntityChange(ServerLevel level, CompoundTag data, boolean afterPresent, BlockVector3 position) {
        if (!data.hasUUID("UUID")) throw new IllegalArgumentException("Entity checkpoint is missing UUID");
        this.level = level; this.data = data; this.afterPresent = afterPresent; this.position = position;
    }
    @Override public void undo(UndoContext context) { apply(!afterPresent); }
    @Override public void redo(UndoContext context) { apply(afterPresent); }
    private void apply(boolean present) {
        var existing = level.getEntity(data.getUUID("UUID"));
        if (!present) { if (existing != null) existing.discard(); return; }
        if (existing != null) return;
        var entity = EntityType.loadEntityRecursive(data.copy(), level, e -> e);
        if (entity == null || !level.tryAddFreshEntityWithPassengers(entity))
            throw new IllegalStateException("Cannot restore checkpoint entity " + data.getUUID("UUID"));
    }
}
