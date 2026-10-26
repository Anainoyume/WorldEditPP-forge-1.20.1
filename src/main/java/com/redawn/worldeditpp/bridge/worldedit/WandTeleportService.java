package com.redawn.worldeditpp.bridge.worldedit;

import com.redawn.worldeditpp.bridge.minecraft.item.ModItems;
import com.sk89q.worldedit.util.Location;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Server-authoritative long-range navigation for the WorldEdit++ wand. */
public final class WandTeleportService {
    private static final double MAX_TRACE_DISTANCE = 32_768.0D;

    public static void teleport(ServerPlayer player) {
        if (!player.getMainHandItem().is(ModItems.WORLD_EDIT_WAND.get())) return;

        Optional<BlockHitResult> hit = traceLoadedBlocks(player);
        var actor = WorldEditPlatform.adaptPlayer(player);
        if (hit.isEmpty()) {
            invalidTarget(player);
            return;
        }

        BlockPos target = hit.get().getBlockPos();
        Vec3 beforeTeleport = player.position();
        actor.findFreePosition(new Location(actor.getWorld(), target.getX(), target.getY(), target.getZ()));
        if (!player.position().equals(beforeTeleport)) {
            playTeleportFeedback(player);
        } else {
            invalidTarget(player);
        }
    }

    private static void invalidTarget(ServerPlayer player) {
        player.displayClientMessage(
                Component.translatable("message.worldeditpp.wand.teleport.invalid_target")
                        .withStyle(ChatFormatting.RED),
                false);
    }

    private static void playTeleportFeedback(ServerPlayer player) {
        player.playNotifySound(SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.7F);
        ServerLevel level = player.serverLevel();
        double particleX = player.getX();
        double particleY = player.getY() + player.getBbHeight() * 0.5D;
        double particleZ = player.getZ();
        ClientboundLevelParticlesPacket packet = new ClientboundLevelParticlesPacket(
                ParticleTypes.PORTAL,
                true,
                particleX, particleY, particleZ,
                0.55F, player.getBbHeight() * 0.55F, 0.55F,
                0.18F,
                96
        );
        // A long-range teleport can leave range checks observing the pre-teleport position.
        // Send directly so the owner always receives the destination effect this tick.
        player.connection.send(packet);

        for (ServerPlayer viewer : level.players()) {
            if (viewer != player) {
                level.sendParticles(
                        viewer, ParticleTypes.PORTAL, true,
                        particleX, particleY, particleZ,
                        96,
                        0.55D, player.getBbHeight() * 0.55D, 0.55D,
                        0.18D
                );
            }
        }
    }

    private static Optional<BlockHitResult> traceLoadedBlocks(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getLookAngle().scale(MAX_TRACE_DISTANCE));
        // Ignore decorative blocks without a supporting surface so the ray continues to the
        // solid ground that can actually serve as a teleport destination.
        ClipContext clip = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
        LoadedTrace context = new LoadedTrace(level, clip);
        return BlockGetter.traverseBlocks(from, to, context, WandTeleportService::testBlock,
                ignored -> Optional.empty());
    }

    @SuppressWarnings("deprecation")
    private static Optional<BlockHitResult> testBlock(LoadedTrace trace, BlockPos position) {
        ServerLevel level = trace.level();
        if (!level.hasChunkAt(position)) {
            return Optional.empty();
        }

        BlockState state = level.getBlockState(position);
        // WorldEdit's free-position search uses the same movement-blocking property to decide
        // whether a player can stand above a block. Carpet has a thin collision shape but does
        // not block movement, so treating it as the target would make that search return without
        // moving the player. Continue through it to the supporting block below instead.
        if (!state.blocksMotion()) return null;
        VoxelShape shape = trace.clip().getBlockShape(state, level, position);
        if (shape.isEmpty()) return null;
        BlockHitResult hit = shape.clip(trace.clip().getFrom(), trace.clip().getTo(), position);
        return hit == null ? null : Optional.of(hit);
    }

    private record LoadedTrace(ServerLevel level, ClipContext clip) { }

    private WandTeleportService() { }
}
