package dev.worldeditpp.testing;

import com.redawn.worldeditpp.gametest.framework.GameTestFramework;
import com.redawn.common.test.TestCategory;
import com.redawn.common.test.TestReport;
import com.redawn.common.test.TestResult;
import java.nio.file.Path;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(TestHarnessMod.ID)
@PrefixGameTestTemplate(false)
public final class PortedGameTests {
    @GameTest(template = "empty", timeoutTicks = 1200, batch = "portedBusiness")
    public static void upstreamBusinessScenarios(GameTestHelper helper) throws Exception {
        var results = GameTestFramework.runAll(helper.getLevel().getServer());
        var report = Path.of(System.getProperty("worldeditpp.test.report")).resolveSibling("business.xml");
        var reportResults = results.stream().map(result -> result.passed()
                ? TestResult.pass("portedBusiness", result.suiteName() + "::" + result.testName(),
                        result.durationMs())
                : TestResult.fail("portedBusiness", result.suiteName() + "::" + result.testName(),
                        result.message() + " " + result.details(), result.durationMs(), result.details()))
                .toList();
        TestReport.writeSuite(report, "portedBusiness", TestCategory.SERVER_SIMULATION, reportResults);
        helper.assertTrue(!results.isEmpty(), "Business suites must run");
        var failures = results.stream().filter(result -> !result.passed()).toList();
        helper.assertTrue(failures.isEmpty(), "Business failures: " + failures);
        helper.succeed();
    }
}
