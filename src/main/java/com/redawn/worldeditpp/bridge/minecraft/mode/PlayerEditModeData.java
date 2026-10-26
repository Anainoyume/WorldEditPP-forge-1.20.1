package com.redawn.worldeditpp.bridge.minecraft.mode;

import com.redawn.worldeditpp.mode.EditSubMode;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerEditModeData {
    private static final double DEFAULT_BLOCK_REACH_DISTANCE = 4.5;
    private static final double DEFAULT_FLIGHT_SPEED = 0.12;
    private static final String KEY_ROOT = "WorldEditPP";
    private static final String KEY_RESTORE = "restoreEditMode";
    private static final String KEY_GAMMA = "gamma";
    private static final String KEY_BLOCK_REACH_ENABLED = "blockReachEnabled";
    private static final String KEY_BLOCK_REACH_DISTANCE = "blockReachDistance";
    private static final String KEY_ORIG_VALID = "originalValid";
    private static final String KEY_ORIG_REACH = "originalBlockReachDistance";
    private static final String KEY_FLIGHT_SPEED = "flightSpeed";
    private static final String KEY_EDIT_SUB_MODE = "editSubMode";

    public static CompoundTag getOrCreate(ServerPlayer player) {
        CompoundTag persistent = com.redawn.worldeditpp.bridge.minecraft.api.PlatformBridge.persistentData(player);
        if (!persistent.contains(KEY_ROOT)) {
            persistent.put(KEY_ROOT, new CompoundTag());
        }
        CompoundTag data = persistent.getCompound(KEY_ROOT);
        data.remove("nightVision");
        data.remove("originalNightVisionPresent");
        data.remove("originalNightVisionTag");
        return data;
    }

    public static boolean getRestoreEditMode(ServerPlayer player) {
        return getOrCreate(player).getBoolean(KEY_RESTORE);
    }

    public static void setRestoreEditMode(ServerPlayer player, boolean value) {
        getOrCreate(player).putBoolean(KEY_RESTORE, value);
    }

    public static double getGamma(ServerPlayer player) {
        CompoundTag tag = getOrCreate(player);
        double value = tag.contains(KEY_GAMMA) ? tag.getDouble(KEY_GAMMA) : 0.5;
        return bounded(value, 0.5, 0.0, 12.0);
    }

    public static void setGamma(ServerPlayer player, double value) {
        getOrCreate(player).putDouble(KEY_GAMMA, bounded(value, 0.5, 0.0, 12.0));
    }

    public static boolean getBlockReachEnabled(ServerPlayer player) {
        return getOrCreate(player).getBoolean(KEY_BLOCK_REACH_ENABLED);
    }

    public static void setBlockReachEnabled(ServerPlayer player, boolean value) {
        getOrCreate(player).putBoolean(KEY_BLOCK_REACH_ENABLED, value);
    }

    public static double getBlockReachDistance(ServerPlayer player) {
        CompoundTag tag = getOrCreate(player);
        double value = tag.contains(KEY_BLOCK_REACH_DISTANCE)
                ? tag.getDouble(KEY_BLOCK_REACH_DISTANCE) : DEFAULT_BLOCK_REACH_DISTANCE;
        return bounded(value, DEFAULT_BLOCK_REACH_DISTANCE, 0.0, 64.0);
    }

    public static void setBlockReachDistance(ServerPlayer player, double value) {
        getOrCreate(player).putDouble(KEY_BLOCK_REACH_DISTANCE,
                bounded(value, DEFAULT_BLOCK_REACH_DISTANCE, 0.0, 64.0));
    }

    public static boolean getOriginalValid(ServerPlayer player) {
        return getOrCreate(player).getBoolean(KEY_ORIG_VALID);
    }

    public static void setOriginalValid(ServerPlayer player, boolean value) {
        getOrCreate(player).putBoolean(KEY_ORIG_VALID, value);
    }

    public static double getOriginalBlockReachDistance(ServerPlayer player) {
        CompoundTag tag = getOrCreate(player);
        double value = tag.contains(KEY_ORIG_REACH)
                ? tag.getDouble(KEY_ORIG_REACH) : DEFAULT_BLOCK_REACH_DISTANCE;
        return bounded(value, DEFAULT_BLOCK_REACH_DISTANCE, 0.0, 64.0);
    }

    public static void setOriginalBlockReachDistance(ServerPlayer player, double value) {
        getOrCreate(player).putDouble(KEY_ORIG_REACH,
                bounded(value, DEFAULT_BLOCK_REACH_DISTANCE, 0.0, 64.0));
    }

    public static double getFlightSpeed(ServerPlayer player) {
        CompoundTag tag = getOrCreate(player);
        double value = tag.contains(KEY_FLIGHT_SPEED)
                ? tag.getDouble(KEY_FLIGHT_SPEED) : DEFAULT_FLIGHT_SPEED;
        return bounded(value, DEFAULT_FLIGHT_SPEED, 0.02, 0.60);
    }

    public static void setFlightSpeed(ServerPlayer player, double value) {
        getOrCreate(player).putDouble(KEY_FLIGHT_SPEED,
                bounded(value, DEFAULT_FLIGHT_SPEED, 0.02, 0.60));
    }

    public static String getEditSubMode(ServerPlayer player) {
        CompoundTag tag = getOrCreate(player);
        String storedId = tag.contains(KEY_EDIT_SUB_MODE) ? tag.getString(KEY_EDIT_SUB_MODE) : EditSubMode.MOVE.id();
        return EditSubMode.fromId(storedId).id();
    }

    public static void setEditSubMode(ServerPlayer player, String subModeId) {
        getOrCreate(player).putString(KEY_EDIT_SUB_MODE, subModeId);
    }

    private static double bounded(double value, double fallback, double minimum, double maximum) {
        if (!Double.isFinite(value)) return fallback;
        return Math.max(minimum, Math.min(maximum, value));
    }

    private PlayerEditModeData() {}
}
