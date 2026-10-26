package com.redawn.worldeditpp.bridge.forge;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ForgeConfiguration {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLE_DEBUG_LOG = BUILDER
            .comment("是否启用调试日志。开发新功能时可以打开，发布正式版前建议关闭。")
            .define("enableDebugLog", false);

    public static void save() {
        SPEC.save();
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private ForgeConfiguration() {
        // 工具配置类不需要实例化。
    }
}
