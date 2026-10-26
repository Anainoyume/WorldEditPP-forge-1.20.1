package com.redawn.worldeditpp.bridge.worldedit;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import com.sk89q.worldedit.regions.selector.limit.SelectorLimits;
import com.sk89q.worldedit.util.formatting.text.TranslatableComponent;
import com.sk89q.worldedit.world.World;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

/** Server-side WorldEdit selection operations, shared by payloads and snapshot readers. */
public final class WorldEditSelectionService {
    private static final SelectorLimits LIMITS = new SelectorLimits() {
        @Override public Optional<Integer> getPolygonVertexLimit() { return Optional.empty(); }
        @Override public Optional<Integer> getPolyhedronVertexLimit() { return Optional.empty(); }
    };

    /** Returns the native selection without narrowing it to a cuboid. */
    public static Optional<RegionSelection> currentSelection(ServerPlayer player) {
        SelectionSession selection = session(player);
        try {
            Region region = selection.session().getSelection(selection.world());
            return Optional.of(new RegionSelection(region, SelectionKind.of(region)));
        } catch (IncompleteRegionException ignored) {
            return Optional.empty();
        }
    }

    /** Cuboid-only accessor for the gizmo, projection and clipboard-adjustment features. */
    public static Optional<CuboidRegion> currentCuboid(ServerPlayer player) {
        return currentSelection(player)
                .filter(RegionSelection::isCuboid)
                .map(RegionSelection::cuboid);
    }

    public static void clear(ServerPlayer player) {
        SelectionSession selection = session(player);
        selection.selector().clear();
        // learnChanges() here would recreate the cleared corners at the origin.
        var actor = WorldEditPlatform.adaptPlayer(player);
        selection.session().dispatchCUISelection(actor);
        actor.printInfo(TranslatableComponent.of("worldedit.select.cleared"));
    }

    public static void setCorners(ServerPlayer player, int x1, int y1, int z1, int x2, int y2, int z2) {
        SelectionSession selection = session(player);
        // Custom gizmo resizes are cuboid-only. Never overwrite a poly/cylinder selection
        // merely because a stale or external client sent cuboid corners.
        if (!(selection.selector() instanceof CuboidRegionSelector)) return;
        RegionSelector selector = selection.selector();
        selector.selectPrimary(BlockVector3.at(x1, y1, z1), LIMITS);
        selector.selectSecondary(BlockVector3.at(x2, y2, z2), LIMITS);
        selector.learnChanges();
    }

    public static void selectPoint(ServerPlayer player, int x, int y, int z, boolean primary) {
        SelectionSession selection = session(player);
        RegionSelector selector = selection.selector();
        BlockVector3 point = BlockVector3.at(x, y, z);
        var actor = WorldEditPlatform.adaptPlayer(player);
        if (!(selector instanceof CuboidRegionSelector)) {
            // Let polyhedral/cylindrical selectors handle their own vertices. The old
            // clear-then-secondary workaround below is specific to cuboid corner semantics.
            if (primary) {
                selector.selectPrimary(point, LIMITS);
                selector.explainPrimarySelection(actor, selection.session(), point);
            } else {
                selector.selectSecondary(point, LIMITS);
                selector.explainSecondarySelection(actor, selection.session(), point);
            }
            selector.learnChanges();
            return;
        }
        if (primary) {
            try {
                selection.session().getSelection(selection.world());
            } catch (IncompleteRegionException ignored) {
                selector.clear();
                selector.selectSecondary(point, LIMITS);
            }
            selector.selectPrimary(point, LIMITS);
            selector.explainPrimarySelection(actor, selection.session(), point);
        } else {
            selector.selectSecondary(point, LIMITS);
            selector.explainSecondarySelection(actor, selection.session(), point);
        }
        selector.learnChanges();
    }

    private static SelectionSession session(ServerPlayer player) {
        World world = WorldEditPlatform.adapt(player.serverLevel());
        LocalSession session = WorldEditSessions.of(player);
        return new SelectionSession(world, session, session.getRegionSelector(world));
    }

    private record SelectionSession(World world, LocalSession session, RegionSelector selector) { }

    private WorldEditSelectionService() { }
}
