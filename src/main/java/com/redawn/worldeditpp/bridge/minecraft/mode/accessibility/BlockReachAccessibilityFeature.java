package com.redawn.worldeditpp.bridge.minecraft.mode.accessibility;

import com.redawn.worldeditpp.bridge.minecraft.mode.PlayerEditModeData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import java.util.Optional;

public final class BlockReachAccessibilityFeature implements EditAccessibilityFeature {
    public static final String ID = "block_reach_distance";
    public static final String ENABLED_ID = "block_reach_enabled";
    private static final double DEFAULT_DISTANCE = 4.5;

    @Override
    public FeatureState captureOriginal(ServerPlayer player) {
        AttributeInstance attr = player.getAttribute(com.redawn.worldeditpp.bridge.minecraft.api.PlatformBridge.blockReachAttribute());
        return new State(attr == null ? DEFAULT_DISTANCE : attr.getBaseValue());
    }

    @Override
    public Optional<FeatureState> loadPersistentOriginal(ServerPlayer player) {
        return Optional.of(new State(PlayerEditModeData.getOriginalBlockReachDistance(player)));
    }

    @Override
    public void persistOriginal(ServerPlayer player, FeatureState originalState) {
        PlayerEditModeData.setOriginalBlockReachDistance(player, ((State) originalState).originalDistance);
    }

    @Override
    public void clearPersistentOriginal(ServerPlayer player) {
        PlayerEditModeData.setOriginalBlockReachDistance(player, DEFAULT_DISTANCE);
    }

    @Override
    public void applyEditValue(ServerPlayer player, FeatureState originalState) {
        if (PlayerEditModeData.getBlockReachEnabled(player))
            setReach(player, PlayerEditModeData.getBlockReachDistance(player));
        else restoreOriginal(player, originalState);
    }

    @Override
    public void restoreOriginal(ServerPlayer player, FeatureState originalState) {
        setReach(player, ((State) originalState).originalDistance);
    }

    @Override
    public void applyRequest(ServerPlayer player, FeatureState originalState, String action, double value) {
        if (ENABLED_ID.equals(action)) {
            PlayerEditModeData.setBlockReachEnabled(player, value > 0.0);
            applyEditValue(player, originalState);
        } else if (ID.equals(action)) {
            PlayerEditModeData.setBlockReachDistance(player, clamp(value));
            if (PlayerEditModeData.getBlockReachEnabled(player))
                applyEditValue(player, originalState);
        }
    }

    @Override
    public void maintain(ServerPlayer player, FeatureState originalState) {
        if (PlayerEditModeData.getBlockReachEnabled(player))
            setReach(player, PlayerEditModeData.getBlockReachDistance(player));
    }

    @Override
    public void sanitizeSaveData(ServerPlayer player, FeatureState originalState, CompoundTag saveData) {
        double orig = ((State) originalState).originalDistance;
        ListTag attrs = saveData.getList("Attributes", 10);
        for (int i = 0; i < attrs.size(); i++) {
            CompoundTag attr = attrs.getCompound(i);
            if ("forge:block_reach".equals(attr.getString("Name"))) {
                attr.putDouble("Base", orig);
                return;
            }
        }
        CompoundTag attr = new CompoundTag();
        attr.putString("Name", "forge:block_reach");
        attr.putDouble("Base", orig);
        attrs.add(attrs.size(), attr);
        saveData.put("Attributes", attrs);
    }

    private static void setReach(ServerPlayer player, double v) {
        AttributeInstance attr = player.getAttribute(com.redawn.worldeditpp.bridge.minecraft.api.PlatformBridge.blockReachAttribute());
        if (attr != null) attr.setBaseValue(clamp(v));
    }

    private static double clamp(double v) { return Math.max(0.0, Math.min(64.0, v)); }

    private record State(double originalDistance) implements FeatureState {}
}
