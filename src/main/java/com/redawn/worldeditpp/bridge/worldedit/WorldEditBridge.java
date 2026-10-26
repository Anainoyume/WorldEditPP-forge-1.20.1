package com.redawn.worldeditpp.bridge.worldedit;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.world.World;

public final class WorldEditBridge {
    public static WorldEdit worldEdit() {
        // WorldEdit 官方 API 入口。后续访问会话管理、平台管理、命令系统时统一从这里获取。
        return WorldEdit.getInstance();
    }

    public static EditSession newEditSession(World world) {
        // 创建基础编辑会话。后续如果需要默认限制、权限检查或方块背包消耗，可以改为 newEditSessionBuilder()。
        return worldEdit()
                .newEditSessionBuilder()
                .world(world)
                .build();
    }

    private WorldEditBridge() {
        // WorldEdit 工具桥接类不需要实例化。
    }
}
