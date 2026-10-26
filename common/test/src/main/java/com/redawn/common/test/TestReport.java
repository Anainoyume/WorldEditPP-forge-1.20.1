package com.redawn.common.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Minimal JUnit-compatible XML report for tests that run outside a JUnit process. */
public final class TestReport {
    private TestReport() { }

    public static void write(Path path, String name, TestCategory category,
                             long elapsedMillis, Throwable failure) throws IOException {
        TestResult result = failure == null
                ? TestResult.pass(category.name().toLowerCase() + "-run", name, elapsedMillis)
                : TestResult.fail(category.name().toLowerCase() + "-run", name,
                        failure.toString(), elapsedMillis, List.of());
        writeSuite(path, category.name().toLowerCase() + "-run", category, List.of(result));
    }

    public static void writeSuite(Path path, String suite, TestCategory category,
                                  List<TestResult> results) throws IOException {
        if (results.isEmpty()) throw new IllegalArgumentException("A suite must execute at least one test");
        Files.createDirectories(path.toAbsolutePath().getParent());
        long failures = results.stream().filter(result -> !result.passed()).count();
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<testsuite name=\"").append(escape(suite))
                .append("\" category=\"").append(category.name())
                .append("\" tests=\"").append(results.size())
                .append("\" failures=\"").append(failures).append("\" errors=\"0\">\n");
        for (TestResult result : results) {
            xml.append("<testcase classname=\"").append(escape(suite))
                    .append("\" name=\"").append(escape(result.testName()))
                    .append("\" time=\"").append(result.durationMs() / 1000.0).append("\">");
            if (!result.passed()) {
                xml.append("<failure message=\"").append(escape(result.message())).append("\"/>");
            }
            xml.append("</testcase>\n");
        }
        xml.append("</testsuite>\n");
        Files.writeString(path, xml, StandardCharsets.UTF_8);
    }

    static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
