package com.redawn.worldeditpp.gametest;

import com.redawn.worldeditpp.WorldEditPlusPlus;
import com.redawn.worldeditpp.gametest.framework.GameTestFramework;
import com.redawn.worldeditpp.gametest.suites.CuboidRegionTestSuite;
import com.redawn.worldeditpp.gametest.suites.DrawKeyInterceptTest;
import com.redawn.worldeditpp.gametest.suites.EditModeIsolationTestSuite;
import com.redawn.worldeditpp.gametest.suites.EditModePersistenceTestSuite;
import com.redawn.worldeditpp.gametest.suites.KeyInterceptorTestSuite;
import com.redawn.worldeditpp.gametest.suites.ProjectionBlockEntitySnapshotTestSuite;
import com.redawn.worldeditpp.gametest.suites.ProjectionPasteBatchTestSuite;
import com.redawn.worldeditpp.gametest.suites.ProjectionBlockStateRotationTestSuite;
import com.redawn.worldeditpp.gametest.suites.SelectionDrawTestSuite;
import com.redawn.worldeditpp.gametest.suites.WorldEditToolConfigurationTestSuite;

public final class GameTestRunner {

    static {
        registerAll();
    }

    public static void registerAll() {
        WorldEditPlusPlus.LOGGER.info("[WorldEdit++ GameTest] 注册测试套件...");
        GameTestFramework.register(new CuboidRegionTestSuite());
        GameTestFramework.register(new EditModeIsolationTestSuite());
        GameTestFramework.register(new EditModePersistenceTestSuite());
        GameTestFramework.register(new KeyInterceptorTestSuite());
        GameTestFramework.register(new DrawKeyInterceptTest());
        GameTestFramework.register(new ProjectionBlockEntitySnapshotTestSuite());
        GameTestFramework.register(new ProjectionPasteBatchTestSuite());
        GameTestFramework.register(new ProjectionBlockStateRotationTestSuite());
        GameTestFramework.register(new com.redawn.worldeditpp.gametest.suites.ProjectionMirrorTestSuite());
        GameTestFramework.register(new SelectionDrawTestSuite());
        GameTestFramework.register(new WorldEditToolConfigurationTestSuite());
        GameTestFramework.register(new com.redawn.worldeditpp.gametest.suites.BlockStateEditorTestSuite());
    }

    private GameTestRunner() {}
}
