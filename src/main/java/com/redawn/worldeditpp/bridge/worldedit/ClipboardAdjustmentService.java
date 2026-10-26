package com.redawn.worldeditpp.bridge.worldedit;

import com.redawn.worldeditpp.bridge.worldedit.clipboard.RepeatedClipboard;
import com.redawn.worldeditpp.bridge.worldedit.operation.IncrementalJob;
import com.redawn.worldeditpp.bridge.worldedit.operation.IncrementalJobScheduler;
import com.redawn.worldeditpp.bridge.worldedit.operation.ProjectionPasteJob;
import com.redawn.worldeditpp.operation.task.TickBudget;
import com.redawn.worldeditpp.WorldEditPlusPlus;
import com.redawn.worldeditpp.math.DiscreteRotation;
import com.redawn.worldeditpp.bridge.minecraft.network.ClipboardAdjustmentPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.ClipboardPlacementStatusPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.SelectionBlocksPayload;
import com.redawn.worldeditpp.network.SelectionProjectionLimits;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.redawn.worldeditpp.bridge.minecraft.api.PlatformBridge;

/** Owns one independent server-side projection buffer while the client adjusts it. */
public final class ClipboardAdjustmentService {
    private static final int BUILD_BUDGET_PER_TICK = 32768;
    private static final int CHUNKS_PER_TICK = 4;
    private static final long PASTE_PLAYER_BUDGET_NANOS = 10_000_000L;
    private static final long PASTE_BUSY_BUDGET_NANOS = 2_000_000L;
    private static final Map<UUID, PlayerState> STATES = new HashMap<>();

    public static void handle(ServerPlayer player, ClipboardAdjustmentPayload payload) {
        if (hasPlacement(player.getUUID()) && payload.action() != ClipboardAdjustmentPayload.Action.CANCEL
                && payload.action() != ClipboardAdjustmentPayload.Action.CONFIRM
                && payload.action() != ClipboardAdjustmentPayload.Action.CONFIRM_IGNORE_AIR) {
            // Do not replace a live PasteJob with a new clipboard and orphan its history/tickets.
            return;
        }
        switch (payload.action()) {
            case COPY -> copySelection(player);
            case BEGIN -> begin(player);
            case BEGIN_SELECTION -> beginSelection(player);
            case CANCEL -> cancel(player);
            case STRETCH_FACE -> stretchFace(player, payload.first(), payload.second() > 0);
            case RESIZE -> resize(player, payload.first() > 0);
            case ROTATE -> rotate(player, payload.first(), payload.second());
            case MIRROR -> mirror(player, payload.first());
            case CONFIRM, CONFIRM_IGNORE_AIR -> confirm(player,
                    payload.first(), payload.second(), payload.third(),
                    payload.action() == ClipboardAdjustmentPayload.Action.CONFIRM_IGNORE_AIR);
        }
    }

    public static void tick(ServerPlayer player) {
        PlayerState state = STATES.get(player.getUUID());
        if (state == null || state.pasteJob != null) return;
        ProjectionPayloadBuilder payloads = state.payloads;
        if (payloads != null) {
            if (!payloads.complete()) payloads.build(BUILD_BUDGET_PER_TICK);
            if (payloads.failed()) {
                failAndClear(player, "message.worldeditpp.adjustment.too_large");
                return;
            }
            if (!payloads.complete()) return;
            for (int index = 0; index < CHUNKS_PER_TICK && payloads.hasPayload(); index++) {
                PlatformBridge.sendToPlayer(player, payloads.nextPayload(state.generation));
            }
            if (!payloads.hasPayload()) {
                state.contentBlocks = payloads.blockCount();
                state.contentBlockEntities = payloads.blockEntityCount();
                state.contentBlockEntityBytes = payloads.blockEntityBytes();
                payloads.release();
                state.payloads = null;
            }
        }
    }

    public static boolean hasPlacement(UUID player) {
        PlayerState state = STATES.get(player);
        return state != null && state.pasteJob != null;
    }

    public static void clear(ServerPlayer player) {
        PlayerState state = STATES.remove(player.getUUID());
        if (state != null && state.pasteJob != null) {
            IncrementalJobScheduler.instance().unregister(player.getUUID());
            boolean finalized = state.pasteJob.finish();
            if (!finalized) {
                WorldEditPlusPlus.LOGGER.warn(
                        "Failed to finalize partial projection placement for disconnected player {}",
                        player.getGameProfile().getName());
            }
        }
    }

    private static void copySelection(ServerPlayer player) {
        RegionSelection selection = WorldEditSelectionService.currentSelection(player).orElse(null);
        if (selection == null) {
            tip(player, "message.worldeditpp.clipboard.no_selection", ChatFormatting.RED);
            return;
        }
        if (!selection.isCuboid()) {
            tip(player, "message.worldeditpp.selection.cuboid_only", ChatFormatting.RED);
            return;
        }
        Region region = selection.cuboid();
        BlockVector3 dimensions = region.getMaximumPoint().subtract(region.getMinimumPoint()).add(1, 1, 1);
        if (!SelectionProjectionLimits.hasValidVolume(dimensions.getX(), dimensions.getY(), dimensions.getZ())) {
            tip(player, "message.worldeditpp.adjustment.too_large", ChatFormatting.RED);
            return;
        }
        World world = WorldEditPlatform.adapt(player.serverLevel());
        LocalSession session = WorldEditSessions.of(player);
        try (EditSession editSession = WorldEditBridge.newEditSession(world)) {
            BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
            clipboard.setOrigin(region.getCenter().toBlockPoint());
            ClipboardBuffers.copyRegion(editSession, region, clipboard, region.getMinimumPoint());
            session.setClipboard(new ClipboardHolder(clipboard));
            tip(player, "message.worldeditpp.clipboard.copied", ChatFormatting.GREEN);
        } catch (WorldEditException | RuntimeException exception) {
            WorldEditPlusPlus.LOGGER.warn("Failed to copy selection into WorldEdit clipboard", exception);
            tip(player, "message.worldeditpp.clipboard.copy_failed", ChatFormatting.RED);
        }
    }

    private static void begin(ServerPlayer player) {
        try {
            ClipboardHolder holder = WorldEditSessions.of(player).getClipboard();
            Clipboard transformed = ClipboardTransforms.bake(holder);
            BlockVector3 dimensions = transformed.getDimensions();
            // Reject a transformed clipboard before materializing its full block table.
            if (!SelectionProjectionLimits.hasValidVolume(dimensions.getX(), dimensions.getY(), dimensions.getZ())) {
                failAndClear(player, "message.worldeditpp.adjustment.too_large");
                return;
            }
            BlockArrayClipboard buffer = ClipboardBuffers.copyOf(transformed);
            // The buffer is never mutated in place: stretching wraps it in a read-only
            // RepeatedClipboard and rotate/mirror allocate new clipboards. Sharing the initial
            // object therefore avoids a second full block + block-entity copy at BEGIN.
            BlockArrayClipboard originalBuffer = buffer;
            PlayerState state = new PlayerState(
                    buffer, originalBuffer, dimensions.getX(), dimensions.getY(), dimensions.getZ());
            STATES.put(player.getUUID(), state);
            state.startBuild();
        } catch (EmptyClipboardException exception) {
            failAndClear(player, "message.worldeditpp.clipboard.empty");
        } catch (WorldEditException | RuntimeException exception) {
            WorldEditPlusPlus.LOGGER.warn("Failed to prepare clipboard adjustment", exception);
            failAndClear(player, "message.worldeditpp.adjustment.failed");
        }
    }

    private static void beginSelection(ServerPlayer player) {
        RegionSelection selection = WorldEditSelectionService.currentSelection(player).orElse(null);
        if (selection == null) {
            failAndClear(player, "message.worldeditpp.clipboard.no_selection");
            return;
        }
        if (!selection.isCuboid()) {
            failAndClear(player, "message.worldeditpp.selection.cuboid_only");
            return;
        }
        Region region = selection.cuboid();
        BlockVector3 dimensions = region.getMaximumPoint().subtract(region.getMinimumPoint()).add(1, 1, 1);
        if (!SelectionProjectionLimits.hasValidVolume(dimensions.getX(), dimensions.getY(), dimensions.getZ())) {
            failAndClear(player, "message.worldeditpp.adjustment.too_large");
            return;
        }
        World world = WorldEditPlatform.adapt(player.serverLevel());
        try (EditSession editSession = WorldEditBridge.newEditSession(world)) {
            BlockArrayClipboard buffer = ClipboardBuffers.empty(dimensions);
            ClipboardBuffers.copyRegion(editSession, region, buffer, BlockVector3.ZERO);
            PlayerState state = new PlayerState(
                    buffer, buffer, dimensions.getX(), dimensions.getY(), dimensions.getZ());
            STATES.put(player.getUUID(), state);
            state.startBuild();
        } catch (WorldEditException | RuntimeException exception) {
            WorldEditPlusPlus.LOGGER.warn("Failed to prepare selection adjustment", exception);
            failAndClear(player, "message.worldeditpp.adjustment.failed");
        }
    }

    private static void cancel(ServerPlayer player) {
        PlayerState state = STATES.get(player.getUUID());
        if (state != null && state.pasteJob != null) {
            long completed = state.pasteJob.completed(), total = state.pasteJob.total();
            clear(player);
            sendPlacementStatus(player, ClipboardPlacementStatusPayload.Status.FAILED, completed, total);
        } else {
            STATES.remove(player.getUUID());
        }
    }

    private static void stretchFace(ServerPlayer player, int face, boolean expand) {
        PlayerState state = readyState(player);
        if (state == null || face < 0 || face >= 6) return;
        int axis = face / 2;
        if (!stretch(state, axis, expand)) {
            tip(player, "message.worldeditpp.adjustment.minimum_size", ChatFormatting.RED);
        }
    }

    private static void resize(ServerPlayer player, boolean expand) {
        PlayerState state = readyState(player);
        if (state == null) return;
        for (int axis = 0; axis < 3; axis++) {
            if (expand || dimension(state.buffer, axis) >= state.tile(axis) * 3) {
                stretch(state, axis, expand);
                stretch(state, axis, expand);
            }
        }
    }

    private static void rotate(ServerPlayer player, int axis, int quarterTurn) {
        PlayerState state = readyState(player);
        if (state == null || axis < 0 || axis > 2 || Math.abs(quarterTurn) != 1) return;
        try {
            DiscreteRotation finalRotation = state.rotation.thenWorldQuarterTurn(axis, quarterTurn);
            ProjectionClipboardRotation.Result rotated = ProjectionClipboardRotation.rotate(
                    RepeatedClipboard.template(state.buffer), RepeatedClipboard.template(state.originalBuffer), finalRotation, axis, quarterTurn);
            BlockVector3 old = state.buffer.getDimensions();
            BlockVector3 dimensions = switch (axis) {
                case 0 -> BlockVector3.at(old.getX(), old.getZ(), old.getY());
                case 1 -> BlockVector3.at(old.getZ(), old.getY(), old.getX());
                default -> BlockVector3.at(old.getY(), old.getX(), old.getZ());
            };
            state.buffer = new RepeatedClipboard(rotated.current(), dimensions);
            state.originalBuffer = new RepeatedClipboard(rotated.original(), dimensions);
            state.rotation = finalRotation;
            state.swapTileAxes(axis);
        } catch (RuntimeException exception) {
            WorldEditPlusPlus.LOGGER.warn("Failed to rotate clipboard adjustment", exception);
            failAndClear(player, "message.worldeditpp.adjustment.failed");
        }
    }

    private static void mirror(ServerPlayer player, int axis) {
        PlayerState state = readyState(player);
        if (state == null || axis < 0 || axis > 2) return;
        try {
            DiscreteRotation finalTransform = state.rotation.thenWorldMirror(axis);
            ProjectionClipboardRotation.Result mirrored = ProjectionClipboardRotation.mirror(
                    RepeatedClipboard.template(state.buffer), RepeatedClipboard.template(state.originalBuffer), finalTransform, axis);
            var dimensions = state.buffer.getDimensions();
            state.buffer = new RepeatedClipboard(mirrored.current(), dimensions);
            state.originalBuffer = new RepeatedClipboard(mirrored.original(), dimensions);
            state.rotation = finalTransform;
        } catch (RuntimeException exception) {
            WorldEditPlusPlus.LOGGER.warn("Failed to mirror clipboard adjustment", exception);
            failAndClear(player, "message.worldeditpp.adjustment.failed");
        }
    }

    private static void confirm(ServerPlayer player, int minX, int minY, int minZ, boolean ignoreAir) {
        PlayerState state = readyState(player);
        if (state == null) {
            PlayerState existing = STATES.get(player.getUUID());
            if (existing != null && existing.pasteJob != null) {
                sendPlacementStatus(player, ClipboardPlacementStatusPayload.Status.PROGRESS,
                        existing.pasteJob.completed(), existing.pasteJob.total());
            } else {
                sendPlacementStatus(player, ClipboardPlacementStatusPayload.Status.FAILED, 0, 0);
            }
            return;
        }
        BlockVector3 dimensions = state.buffer.getDimensions();
        long volume = (long) dimensions.getX() * dimensions.getY() * dimensions.getZ();
        sendPlacementStatus(player, ClipboardPlacementStatusPayload.Status.STARTED, 0, volume);
        startIncrementalPaste(player, state, minX, minY, minZ, ignoreAir);
    }

    private static void startIncrementalPaste(
            ServerPlayer player, PlayerState state, int minX, int minY, int minZ, boolean ignoreAir) {
        LocalSession localSession = WorldEditSessions.of(player);
        try {
            state.pasteJob = ProjectionPasteJob.open(
                    player.serverLevel(), state.buffer, localSession,
                    minX, minY, minZ, ignoreAir);
            if (!IncrementalJobScheduler.instance().register(new PlacementJob(player, state))) {
                state.pasteJob.finish();
                state.pasteJob = null;
                STATES.remove(player.getUUID(), state);
                sendPlacementStatus(player, ClipboardPlacementStatusPayload.Status.FAILED, 0,
                        (long) state.buffer.getDimensions().getX()
                                * state.buffer.getDimensions().getY()
                                * state.buffer.getDimensions().getZ());
            }
        } catch (RuntimeException exception) {
            STATES.remove(player.getUUID());
            WorldEditPlusPlus.LOGGER.warn("Failed to start incremental projection placement", exception);
            sendPlacementStatus(player, ClipboardPlacementStatusPayload.Status.FAILED, 0,
                    (long) state.buffer.getDimensions().getX()
                            * state.buffer.getDimensions().getY()
                            * state.buffer.getDimensions().getZ());
        }
    }

    private static void failIncrementalPaste(
            ServerPlayer player, PlayerState state, Exception exception) {
        ProjectionPasteJob job = state.pasteJob;
        if (job == null) return;
        IncrementalJobScheduler.instance().unregister(player.getUUID());
        WorldEditPlusPlus.LOGGER.warn("Incremental projection placement failed", exception);
        job.finish();
        long completed = job.completed();
        long total = job.total();
        state.pasteJob = null;
        STATES.remove(player.getUUID(), state);
        sendPlacementStatus(player, ClipboardPlacementStatusPayload.Status.FAILED, completed, total);
    }

    private static void sendPlacementStatus(
            ServerPlayer player,
            ClipboardPlacementStatusPayload.Status status,
            long completed,
            long total) {
        if (completed < 0 || total < 0 || completed > Integer.MAX_VALUE || total > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Placement progress exceeds protocol limits");
        }
        PlatformBridge.sendToPlayer(player, new ClipboardPlacementStatusPayload(
                status, (int) completed, (int) total));
    }

    private static PlayerState readyState(ServerPlayer player) {
        PlayerState state = STATES.get(player.getUUID());
        return state != null && state.payloads == null && state.pasteJob == null ? state : null;
    }

    private static boolean stretch(PlayerState state, int axis, boolean expand) {
        int oldSize = dimension(state.buffer, axis);
        int unit = state.tile(axis);
        int newSize = expand ? oldSize + unit : oldSize - unit;
        if (newSize < unit
                || !validResizedVolume(state.buffer.getDimensions(), axis, newSize)
                || !hasValidRepeatedContent(state, axis, newSize)) {
            return false;
        }
        BlockVector3 oldDimensions = state.buffer.getDimensions();
        BlockVector3 newDimensions = withAxis(oldDimensions, axis, newSize);
        state.buffer = new RepeatedClipboard(state.buffer, newDimensions);
        state.originalBuffer = new RepeatedClipboard(state.originalBuffer, newDimensions);
        return true;
    }

    private static int dimension(Clipboard clipboard, int axis) {
        BlockVector3 dimensions = clipboard.getDimensions();
        return axis == 0 ? dimensions.getX() : axis == 1 ? dimensions.getY() : dimensions.getZ();
    }

    private static BlockVector3 withAxis(BlockVector3 dimensions, int axis, int value) {
        return BlockVector3.at(
                axis == 0 ? value : dimensions.getX(),
                axis == 1 ? value : dimensions.getY(),
                axis == 2 ? value : dimensions.getZ());
    }

    private static boolean validResizedVolume(BlockVector3 dimensions, int axis, int value) {
        BlockVector3 resized = withAxis(dimensions, axis, value);
        return SelectionProjectionLimits.hasValidVolume(resized.getX(), resized.getY(), resized.getZ());
    }

    private static boolean hasValidRepeatedContent(PlayerState state, int axis, int newSize) {
        BlockVector3 dimensions = state.buffer.getDimensions();
        long repetitions = 1L;
        for (int currentAxis = 0; currentAxis < 3; currentAxis++) {
            int size = currentAxis == axis ? newSize
                    : currentAxis == 0 ? dimensions.getX()
                    : currentAxis == 1 ? dimensions.getY() : dimensions.getZ();
            repetitions *= size / state.tile(currentAxis);
        }
        return repetitions * state.contentBlocks <= SelectionProjectionLimits.MAX_BLOCKS
                && repetitions * state.contentBlockEntities <= SelectionProjectionLimits.MAX_BLOCK_ENTITY_COUNT
                && repetitions * state.contentBlockEntityBytes
                        <= SelectionProjectionLimits.MAX_TOTAL_BLOCK_ENTITY_NBT_BYTES;
    }

    private static void failAndClear(ServerPlayer player, String key) {
        STATES.remove(player.getUUID());
        PlatformBridge.sendToPlayer(player, new SelectionBlocksPayload(
                0, 0, 0, 0, 0, 0, 0, 0, 0, null, new int[0], List.of()));
        tip(player, key, ChatFormatting.RED);
    }

    private static void tip(ServerPlayer player, String key, ChatFormatting color) {
        player.displayClientMessage(Component.translatable(key).withStyle(color), true);
    }

    private static final class PlacementJob implements IncrementalJob {
        private final ServerPlayer player;
        private final PlayerState state;

        PlacementJob(ServerPlayer player, PlayerState state) {
            this.player = player;
            this.state = state;
        }

        @Override public UUID ownerId() { return player.getUUID(); }

        @Override public UUID actorId() { return player.getUUID(); }

        @Override public boolean step(ServerPlayer actor, TickBudget budget) {
            ProjectionPasteJob job = state.pasteJob;
            if (job == null) return true;
            ServerPlayer target = actor != null ? actor : player;
            if (target == null || !job.belongsTo(target.serverLevel())) {
                failIncrementalPaste(player, state,
                        new IllegalStateException("Player changed dimensions during projection placement"));
                return true;
            }
            try {
                job.step(budget.remainingNanos());
            } catch (WorldEditException | RuntimeException exception) {
                failIncrementalPaste(player, state, exception);
                return true;
            }
            if (job.isComplete()) {
                long total = job.total();
                boolean finalized = job.finish();
                state.pasteJob = null;
                STATES.remove(player.getUUID(), state);
                sendPlacementStatus(player, finalized
                                ? ClipboardPlacementStatusPayload.Status.COMPLETED
                                : ClipboardPlacementStatusPayload.Status.FAILED,
                        finalized ? total : job.completed(), total);
                return true;
            }
            if (job.shouldReportProgress()) {
                sendPlacementStatus(player, ClipboardPlacementStatusPayload.Status.PROGRESS,
                        job.completed(), job.total());
            }
            return false;
        }

        @Override public int workUnits(boolean spareTime) { return 1; }

        @Override public long timeBudgetNanos(boolean spareTime) {
            return spareTime ? PASTE_PLAYER_BUDGET_NANOS : PASTE_BUSY_BUDGET_NANOS;
        }

        @Override public void close() {
            ProjectionPasteJob job = state.pasteJob;
            if (job == null) return;
            job.finish();
            state.pasteJob = null;
            STATES.remove(player.getUUID(), state);
        }
    }

    private static final class PlayerState {
        Clipboard buffer;
        Clipboard originalBuffer;
        DiscreteRotation rotation = DiscreteRotation.IDENTITY;
        int tileX;
        int tileY;
        int tileZ;
        int generation = 1;
        int contentBlocks;
        int contentBlockEntities;
        long contentBlockEntityBytes;
        ProjectionPayloadBuilder payloads;
        ProjectionPasteJob pasteJob;

        PlayerState(
                BlockArrayClipboard buffer,
                BlockArrayClipboard originalBuffer,
                int tileX,
                int tileY,
                int tileZ
        ) {
            this.buffer = buffer;
            this.originalBuffer = originalBuffer;
            this.tileX = tileX;
            this.tileY = tileY;
            this.tileZ = tileZ;
        }

        void startBuild() {
            payloads = new ProjectionPayloadBuilder(buffer);
        }

        int tile(int axis) {
            return axis == 0 ? tileX : axis == 1 ? tileY : tileZ;
        }

        void swapTileAxes(int rotationAxis) {
            if (rotationAxis == 0) {
                int swap = tileY; tileY = tileZ; tileZ = swap;
            } else if (rotationAxis == 1) {
                int swap = tileX; tileX = tileZ; tileZ = swap;
            } else {
                int swap = tileX; tileX = tileY; tileY = swap;
            }
        }
    }

    private ClipboardAdjustmentService() { }
}
