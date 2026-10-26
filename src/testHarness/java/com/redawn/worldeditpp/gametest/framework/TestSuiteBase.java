package com.redawn.worldeditpp.gametest.framework;

import com.redawn.common.test.TestResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class TestSuiteBase implements TestSuite {
    private final List<TestResult> results = new ArrayList<>();
    private long suiteStartMs;

    @Override
    public final void run(GameSimulation simulation) {
        results.clear();
        suiteStartMs = System.currentTimeMillis();
        runTests(simulation);
    }

    protected abstract void runTests(GameSimulation simulation);

    protected void test(String testName, Runnable testLogic) {
        long start = System.currentTimeMillis();
        try {
            testLogic.run();
            results.add(TestResult.pass(suiteName(), testName, System.currentTimeMillis() - start));
        } catch (AssertionError e) {
            results.add(new TestResult.Builder()
                    .suiteName(suiteName())
                    .testName(testName)
                    .detail(e.getMessage())
                    .fail("断言失败: " + e.getMessage(), System.currentTimeMillis() - start));
        } catch (Exception e) {
            results.add(new TestResult.Builder()
                    .suiteName(suiteName())
                    .testName(testName)
                    .detail(e.getClass().getSimpleName() + ": " + e.getMessage())
                    .fail("异常: " + e.getMessage(), System.currentTimeMillis() - start));
        }
    }

    protected String suiteName() {
        GameTestSuiteDescriptor descriptor = getClass().getAnnotation(GameTestSuiteDescriptor.class);
        return descriptor != null ? descriptor.name() : getClass().getSimpleName();
    }

    public List<TestResult> results() {
        return Collections.unmodifiableList(results);
    }

    public long elapsedMs() {
        return System.currentTimeMillis() - suiteStartMs;
    }
}
