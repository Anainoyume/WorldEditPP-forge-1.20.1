package com.redawn.worldeditpp.bridge.worldedit;

import com.redawn.worldeditpp.WorldEditPlusPlus;
import com.redawn.worldeditpp.bridge.minecraft.blockstate.BlockStatePropertiesEditor;
import com.redawn.worldeditpp.bridge.minecraft.mode.EditModeManager;
import com.redawn.worldeditpp.bridge.minecraft.mode.WorldEditAuthorizationService;
import com.redawn.worldeditpp.bridge.minecraft.network.BlockStateEditPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.BlockStateEditResultPayload;
import com.redawn.worldeditpp.bridge.minecraft.network.BlockStateEditResultPayload.Status;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.math.BlockVector3;
import com.redawn.worldeditpp.bridge.worldedit.operation.EditSessionTuning;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import com.redawn.worldeditpp.bridge.minecraft.api.PlatformBridge;

/** Server-authoritative single-cell edits deliberately leave undo/redo history untouched. */
public final class BlockStateEditService {
    public static void handle(ServerPlayer player, BlockStateEditPayload request) {
        if (!WorldEditAuthorizationService.require(player) || !EditModeManager.isEditing(player)) {
            reply(player, request, 0, Status.DENIED);
            return;
        }
        var level = player.serverLevel();
        var pos = request.position();
        double reach = PlatformBridge.blockReach(player) + 1.0;
        if (!level.dimension().location().equals(request.dimension()) || !level.hasChunkAt(pos)
                || level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || player.getEyePosition().distanceToSqr(pos.getCenter()) > reach * reach) {
            reply(player, request, 0, Status.UNAVAILABLE);
            return;
        }
        BlockState current = level.getBlockState(pos);
        if (current.isAir() || !BuiltInRegistries.BLOCK.getKey(current.getBlock()).equals(request.block())) {
            reply(player, request, Block.getId(current), Status.UNAVAILABLE);
            return;
        }
        var next = BlockStatePropertiesEditor.apply(current, request.property(), request.value());
        if (next.isEmpty()) {
            reply(player, request, Block.getId(current), Status.INVALID);
            return;
        }
        Status status = Status.APPLIED;
        if (next.get() != current) {
            var actor = WorldEditPlatform.adaptPlayer(player);
            var session = WorldEditSessions.of(player);
            try (EditSession edit = session.createEditSession(actor)) {
                try {
                    setState(edit, BlockVector3.at(pos.getX(), pos.getY(), pos.getZ()), next.get());
                } finally {
                    edit.flushSession();
                }
            } catch (WorldEditException | RuntimeException exception) {
                WorldEditPlusPlus.LOGGER.warn("Failed to edit block state at {}", pos, exception);
                status = Status.FAILED;
            }
        }
        reply(player, request, Block.getId(level.getBlockState(pos)), status);
    }

    public static void setState(EditSession edit, BlockVector3 position, BlockState state) throws WorldEditException {
        // Property tweaks must neither consume undo slots nor discard pending redo entries.
        var original = edit.getFullBlock(position);
        EditSessionTuning.suppressBlockUpdates(edit);
        edit.smartSetBlock(position, WorldEditPlatform.adapt(state).toBaseBlock(original.getNbtData()));
    }

    private static void reply(ServerPlayer player, BlockStateEditPayload request, int state, Status status) {
        if (status == Status.APPLIED || status == Status.INVALID || status == Status.FAILED) {
            // Chunk broadcasts normally wait until the tick ends. Publish the edited cell before
            // the acknowledgement so the screen cannot read an older world state after success.
            player.connection.send(new ClientboundBlockUpdatePacket(request.position(), Block.stateById(state)));
        }
        PlatformBridge.sendToPlayer(player, new BlockStateEditResultPayload(
                request.sessionId(), request.requestId(), state, status));
    }

    private BlockStateEditService() { }
}
