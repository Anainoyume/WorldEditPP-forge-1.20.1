package com.redawn.worldeditpp.bridge.worldedit.operation;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.util.SideEffectSet;

/** Common tuning for large edits that are already divided across server ticks. */
public final class EditSessionTuning {
    public static void configureIncrementalBulkWrite(EditSession session) {
        session.setSideEffectApplier(SideEffectSet.none().with(
                SideEffect.LIGHTING, SideEffect.State.DELAYED));
        // The task owns the tick boundary and flushes there. A second reorder buffer would
        // retain the whole operation and undo the scheduler's memory and latency bounds.
        session.disableBuffering();
    }

    /**
     * Exact bulk writes apply their own final states: no neighbour shape updates, no
     * validation pass and no per-block update packets. Shared by draw replaces, shape fills
     * and single-cell state edits so the three call sites cannot drift apart.
     */
    public static void suppressBlockUpdates(EditSession session) {
        session.setSideEffectApplier(session.getSideEffectApplier()
                .with(SideEffect.NEIGHBORS, SideEffect.State.OFF)
                .with(SideEffect.UPDATE, SideEffect.State.OFF)
                .with(SideEffect.VALIDATION, SideEffect.State.OFF));
    }

    private EditSessionTuning() { }
}
