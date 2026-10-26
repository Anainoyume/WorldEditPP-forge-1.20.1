package com.redawn.worldeditpp.bridge.minecraft.mode.accessibility;

import java.util.List;

public final class EditAccessibilityFeatures {
    public static final List<EditAccessibilityFeature> FEATURES = List.of(
            new BlockReachAccessibilityFeature()
    );

    private EditAccessibilityFeatures() {
        // 编辑辅助功能统一注册入口，新增功能只需要追加到 FEATURES。
    }
}
