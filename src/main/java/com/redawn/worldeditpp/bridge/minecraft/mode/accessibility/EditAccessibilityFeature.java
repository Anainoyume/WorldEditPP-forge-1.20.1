package com.redawn.worldeditpp.bridge.minecraft.mode.accessibility;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;

import java.util.Optional;

public interface EditAccessibilityFeature {
    FeatureState captureOriginal(ServerPlayer player);

    default Optional<FeatureState> loadPersistentOriginal(ServerPlayer player) {
        return Optional.empty();
    }

    default void persistOriginal(ServerPlayer player, FeatureState originalState) {
    }

    default void clearPersistentOriginal(ServerPlayer player) {
    }

    void applyEditValue(ServerPlayer player, FeatureState originalState);

    void restoreOriginal(ServerPlayer player, FeatureState originalState);

    void applyRequest(ServerPlayer player, FeatureState originalState, String action, double value);

    void maintain(ServerPlayer player, FeatureState originalState);

    default void sanitizeSaveData(ServerPlayer player, FeatureState originalState, CompoundTag saveData) {
    }

    interface FeatureState {
    }
}
