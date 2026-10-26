package com.redawn.worldeditpp.gametest.framework;

import com.redawn.common.test.TestResult;

import com.redawn.worldeditpp.WorldEditPlusPlus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;

public final class GameTestFramework {
    private static final Map<String, TestSuite> SUITES = new LinkedHashMap<>();
    private static GameSimulation simulation;
    private static final List<TestResult> results = new ArrayList<>();

    static {
        try {
            Class.forName("com.redawn.worldeditpp.gametest.GameTestRunner");
        } catch (ClassNotFoundException ignored) {
        }
    }

    public static void register(TestSuite suite) {
        String name = suite.getClass().getSimpleName();
        String displayName = suite.getClass().isAnnotationPresent(GameTestSuiteDescriptor.class)
                ? suite.getClass().getAnnotation(GameTestSuiteDescriptor.class).name()
                : name;
        SUITES.put(displayName, suite);
    }

    public static List<TestResult> runAll(MinecraftServer server) {
        results.clear();
        simulation = new GameSimulation(server);

        WorldEditPlusPlus.LOGGER.info("========== [WorldEdit++ 集成测试框架] 启动 ==========");
        WorldEditPlusPlus.LOGGER.info("已注册 {} 个测试套件", SUITES.size());

        long startMs = System.currentTimeMillis();
        for (Map.Entry<String, TestSuite> entry : SUITES.entrySet()) {
            runSuite(entry.getKey(), entry.getValue());
        }
        long elapsedMs = System.currentTimeMillis() - startMs;

        int passed = 0;
        int failed = 0;
        for (TestResult result : results) {
            if (result.passed()) {
                passed++;
                WorldEditPlusPlus.LOGGER.info(result.toString());
            } else {
                failed++;
                WorldEditPlusPlus.LOGGER.error(result.toString());
                for (String detail : result.details()) {
                    WorldEditPlusPlus.LOGGER.error("    {}", detail);
                }
            }
        }

        WorldEditPlusPlus.LOGGER.info("========== [WorldEdit++ 集成测试框架] 完成 ==========");
        WorldEditPlusPlus.LOGGER.info("总计: {} 通过, {} 失败, {} 套件 ({})",
                passed, failed, SUITES.size(), formatDuration(elapsedMs));

        if (failed > 0) {
            WorldEditPlusPlus.LOGGER.error("集成测试存在失败项，请检查上述错误日志。");
        }
        return List.copyOf(results);
    }

    private static void runSuite(String name, TestSuite suite) {
        WorldEditPlusPlus.LOGGER.info("  >> 运行测试套件: {}", name);
        try {
            suite.run(simulation);
            if (suite instanceof TestSuiteBase base) {
                results.addAll(base.results());
            }
        } catch (AssertionError e) {
            results.add(new TestResult.Builder()
                    .suiteName(name)
                    .testName("suite")
                    .detail(e.getMessage())
                    .fail("断言失败: " + e.getMessage(), 0));
        } catch (Exception e) {
            results.add(new TestResult.Builder()
                    .suiteName(name)
                    .testName("suite")
                    .detail(e.getClass().getSimpleName() + ": " + e.getMessage())
                    .fail("异常: " + e.getMessage(), 0));
        }
    }

    static void addResult(TestResult result) {
        results.add(result);
    }

    static GameSimulation activeSimulation() {
        return simulation;
    }

    private static String formatDuration(long ms) {
        if (ms < 1000) return ms + "ms";
        return String.format("%.1fs", ms / 1000.0);
    }

    private GameTestFramework() {}
}
