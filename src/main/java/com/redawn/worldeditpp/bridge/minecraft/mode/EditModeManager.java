package com.redawn.worldeditpp.bridge.minecraft.mode;

import com.redawn.worldeditpp.mode.EditModeState;

import com.redawn.worldeditpp.mode.EditSubMode;

import com.redawn.worldeditpp.WorldEditPlusPlus;
import com.redawn.worldeditpp.bridge.minecraft.mode.accessibility.EditAccessibilityFeature;
import com.redawn.worldeditpp.bridge.minecraft.mode.accessibility.EditAccessibilityFeatures;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.GameType;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

public final class EditModeManager {
    private static final Map<UUID, OriginalPlayerState> EDITING_PLAYERS = new HashMap<>();
    private static final Map<UUID, Map<EditAccessibilityFeature, EditAccessibilityFeature.FeatureState>> ACCESSIBILITY_STATES = new HashMap<>();

    public static void toggle(ServerPlayer player, String keyName) {
        if (EDITING_PLAYERS.containsKey(player.getUUID())) {
            if (WorldEditAuthorizationService.isAuthorized(player)) {
                disable(player, true, true);
            } else {
                disable(player, true, false);
                WorldEditAuthorizationService.deny(player);
            }
        } else {
            if (!WorldEditAuthorizationService.require(player)) {
                syncToClient(player);
                return;
            }
            enable(player, keyName, true,
                    Component.translatable("message.worldeditpp.edit_mode.enabled", keyName));
        }
        syncToClient(player);
    }

    public static void restoreOnLogin(ServerPlayer player) {
        if (PlayerEditModeData.getRestoreEditMode(player) && !isEditing(player)) {
            if (!WorldEditAuthorizationService.require(player)) {
                PlayerEditModeData.setRestoreEditMode(player, false);
                PlayerEditModeData.setOriginalValid(player, false);
                return;
            }
            enable(player, "", false, Component.translatable("message.worldeditpp.edit_mode.restored"));
        }
    }

    public static boolean isEditing(ServerPlayer player) {
        return EDITING_PLAYERS.containsKey(player.getUUID());
    }

    public static void applyServerTick(ServerPlayer player) {
        if (!isEditing(player)) {
            return;
        }

        if (!WorldEditAuthorizationService.isAuthorized(player)) {
            disable(player, true, false);
            WorldEditAuthorizationService.deny(player);
            syncToClient(player);
            return;
        }

        // 服务端也需要持续允许无碰撞移动，否则客户端穿墙后可能被服务端位置校正。
        applyEditMovementFlags(player);
        player.fallDistance = 0;
        player.getAbilities().setFlyingSpeed((float) PlayerEditModeData.getFlightSpeed(player));

        Map<EditAccessibilityFeature, EditAccessibilityFeature.FeatureState> states = ACCESSIBILITY_STATES.get(player.getUUID());
        if (states != null) {
            for (EditAccessibilityFeature feature : EditAccessibilityFeatures.FEATURES) {
                feature.maintain(player, states.get(feature));
            }
        }
    }

    public static void handleLogout(ServerPlayer player) {
        if (isEditing(player)) {
            disable(player, false, false);
        }
    }

    private static void applyEditMovementFlags(ServerPlayer player) {
        player.noPhysics = true;
        player.blocksBuilding = false;
        player.setNoGravity(true);
    }

    private static void enable(ServerPlayer player, String keyName, boolean persist, Component message) {
        UUID playerId = player.getUUID();
        OriginalPlayerState originalState = OriginalPlayerState.capture(player);
        EDITING_PLAYERS.put(playerId, originalState);

        Map<EditAccessibilityFeature, EditAccessibilityFeature.FeatureState> featureStates = new IdentityHashMap<>();
        boolean usePersistentOriginal = PlayerEditModeData.getOriginalValid(player);
        if (usePersistentOriginal) {
            WorldEditPlusPlus.LOGGER.debug("玩家 {} 使用持久化辅助功能原始状态恢复编辑模式。", player.getGameProfile().getName());
        }
        for (EditAccessibilityFeature feature : EditAccessibilityFeatures.FEATURES) {
            EditAccessibilityFeature.FeatureState state = usePersistentOriginal
                    ? feature.loadPersistentOriginal(player).orElseGet(() -> feature.captureOriginal(player))
                    : feature.captureOriginal(player);
            feature.persistOriginal(player, state);
            featureStates.put(feature, state);
        }
        PlayerEditModeData.setOriginalValid(player, true);
        ACCESSIBILITY_STATES.put(playerId, featureStates);
        EditModeState.setServerEditing(playerId, true);

        if (persist) {
            PlayerEditModeData.setRestoreEditMode(player, true);
        }

        // 进入编辑模式时切换为创造模式，获得默认创造模式行为，再叠加无碰撞飞行。
        player.setGameMode(GameType.CREATIVE);
        Abilities abilities = player.getAbilities();
        abilities.mayfly = true;
        abilities.flying = true;
        abilities.instabuild = true;
        abilities.mayBuild = true;
        abilities.invulnerable = true;
        abilities.setFlyingSpeed((float) PlayerEditModeData.getFlightSpeed(player));
        applyEditMovementFlags(player);

        for (EditAccessibilityFeature feature : EditAccessibilityFeatures.FEATURES) {
            feature.applyEditValue(player, featureStates.get(feature));
        }

        player.onUpdateAbilities();
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.GREEN), false);
        WorldEditPlusPlus.LOGGER.debug("玩家 {} 进入 WorldEdit++ 编辑模式。", player.getGameProfile().getName());
    }

    private static void disable(ServerPlayer player, boolean persist, boolean notifyPlayer) {
        UUID playerId = player.getUUID();
        OriginalPlayerState originalState = EDITING_PLAYERS.remove(playerId);
        if (originalState == null) {
            return;
        }

        Map<EditAccessibilityFeature, EditAccessibilityFeature.FeatureState> states = ACCESSIBILITY_STATES.remove(playerId);
        for (EditAccessibilityFeature feature : EditAccessibilityFeatures.FEATURES) {
            EditAccessibilityFeature.FeatureState state = states == null ? null : states.get(feature);
            if (state == null) {
                state = feature.loadPersistentOriginal(player).orElse(null);
            }
            if (state != null) {
                feature.restoreOriginal(player, state);
            }
            if (persist) {
                feature.clearPersistentOriginal(player);
            }
        }
        EditModeState.setServerEditing(playerId, false);

        if (persist) {
            PlayerEditModeData.setRestoreEditMode(player, false);
            PlayerEditModeData.setOriginalValid(player, false);
        }

        originalState.restore(player);
        player.onUpdateAbilities();
        if (notifyPlayer) {
            player.displayClientMessage(
                    Component.translatable("message.worldeditpp.edit_mode.disabled")
                            .withStyle(ChatFormatting.GREEN),
                    false);
        }
        WorldEditPlusPlus.LOGGER.debug("玩家 {} 退出 WorldEdit++ 编辑模式。", player.getGameProfile().getName());
    }

    public static void applyAccessibility(ServerPlayer player, String action, double value) {
        if (!isEditing(player)) {
            return;
        }
        if (!Double.isFinite(value)) {
            return;
        }

        if ("flight_speed".equals(action)) {
            double clamped = Math.max(0.02, Math.min(0.60, value));
            PlayerEditModeData.setFlightSpeed(player, clamped);
            player.getAbilities().setFlyingSpeed((float) clamped);
            player.onUpdateAbilities();
            syncToClient(player);
            return;
        }

        if ("gamma".equals(action)) {
            PlayerEditModeData.setGamma(player, value);
            syncToClient(player);
            return;
        }

        if ("edit_sub_mode".equals(action)) {
            EditSubMode.fromNetworkValue(value).ifPresent(mode ->
                    PlayerEditModeData.setEditSubMode(player, mode.id()));
            syncToClient(player);
            return;
        }

        Map<EditAccessibilityFeature, EditAccessibilityFeature.FeatureState> states = ACCESSIBILITY_STATES.get(player.getUUID());
        if (states == null) {
            return;
        }

        for (EditAccessibilityFeature feature : EditAccessibilityFeatures.FEATURES) {
            feature.applyRequest(player, states.get(feature), action, value);
        }
        syncToClient(player);
    }

    public static void syncToClient(ServerPlayer player) {
        com.redawn.worldeditpp.bridge.minecraft.api.PlatformBridge.sendToPlayer(player,
                new com.redawn.worldeditpp.bridge.minecraft.network.SyncAccessibilityPayload(
                        PlayerEditModeData.getGamma(player),
                        PlayerEditModeData.getBlockReachEnabled(player),
                        PlayerEditModeData.getBlockReachDistance(player),
                        PlayerEditModeData.getFlightSpeed(player),
                        isEditing(player),
                        PlayerEditModeData.getEditSubMode(player)
                ));
    }

    public static void sanitizeSaveData(ServerPlayer player, net.minecraft.nbt.CompoundTag saveData) {
        OriginalPlayerState originalState = EDITING_PLAYERS.get(player.getUUID());
        if (originalState != null) {
            EditModeSaveDataSanitizer.sanitize(saveData, originalState.saveSnapshot());
        }

        Map<EditAccessibilityFeature, EditAccessibilityFeature.FeatureState> states = ACCESSIBILITY_STATES.get(player.getUUID());
        if (states == null) {
            return;
        }

        for (EditAccessibilityFeature feature : EditAccessibilityFeatures.FEATURES) {
            feature.sanitizeSaveData(player, states.get(feature), saveData);
        }
    }

    private record OriginalPlayerState(
            GameType gameType,
            GameType previousGameType,
            boolean invulnerable,
            boolean flying,
            boolean mayfly,
            boolean instabuild,
            boolean mayBuild,
            float flyingSpeed,
            float walkingSpeed,
            boolean noPhysics,
            boolean blocksBuilding,
            boolean noGravity
    ) {
        private static OriginalPlayerState capture(ServerPlayer player) {
            Abilities abilities = player.getAbilities();
            return new OriginalPlayerState(
                    player.gameMode.getGameModeForPlayer(),
                    player.gameMode.getPreviousGameModeForPlayer(),
                    abilities.invulnerable,
                    abilities.flying,
                    abilities.mayfly,
                    abilities.instabuild,
                    abilities.mayBuild,
                    abilities.getFlyingSpeed(),
                    abilities.getWalkingSpeed(),
                    player.noPhysics,
                    player.blocksBuilding,
                    player.isNoGravity()
            );
        }

        private void restore(ServerPlayer player) {
            player.setGameMode(gameType);
            Abilities abilities = player.getAbilities();
            abilities.invulnerable = invulnerable;
            abilities.flying = flying;
            abilities.mayfly = mayfly;
            abilities.instabuild = instabuild;
            abilities.mayBuild = mayBuild;
            abilities.setFlyingSpeed(flyingSpeed);
            abilities.setWalkingSpeed(walkingSpeed);
            player.noPhysics = noPhysics;
            player.blocksBuilding = blocksBuilding;
            player.setNoGravity(noGravity);
            player.fallDistance = 0;
        }

        private EditModeSaveDataSanitizer.Snapshot saveSnapshot() {
            return new EditModeSaveDataSanitizer.Snapshot(
                    gameType.getId(),
                    previousGameType == null ? null : previousGameType.getId(),
                    invulnerable,
                    flying,
                    mayfly,
                    instabuild,
                    mayBuild,
                    flyingSpeed,
                    walkingSpeed,
                    noGravity
            );
        }
    }

    private EditModeManager() {
        // 编辑模式状态由玩家 UUID 映射维护，不需要实例化管理器。
    }
}
