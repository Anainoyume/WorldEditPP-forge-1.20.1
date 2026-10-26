package com.redawn.common.test;

import java.util.ArrayList;
import java.util.List;

/** Immutable result shared by JUnit-adjacent suites and the real client/server runners. */
public final class TestResult {
    private final String suiteName;
    private final String testName;
    private final boolean passed;
    private final String message;
    private final long durationMs;
    private final List<String> details;

    private TestResult(String suiteName, String testName, boolean passed, String message,
                       long durationMs, List<String> details) {
        this.suiteName = suiteName;
        this.testName = testName;
        this.passed = passed;
        this.message = message;
        this.durationMs = durationMs;
        this.details = List.copyOf(details);
    }

    public static TestResult pass(String suiteName, String testName, long durationMs) {
        return new TestResult(suiteName, testName, true, "OK", durationMs, List.of());
    }

    public static TestResult fail(String suiteName, String testName, String message,
                                  long durationMs, List<String> details) {
        return new TestResult(suiteName, testName, false, message, durationMs, details);
    }

    public String suiteName() { return suiteName; }
    public String testName() { return testName; }
    public boolean passed() { return passed; }
    public String message() { return message; }
    public long durationMs() { return durationMs; }
    public List<String> details() { return details; }

    @Override
    public String toString() {
        String status = passed ? "PASS" : "FAIL";
        String info = passed ? "" : " - " + message;
        return String.format("  [%s] %s::%s (%dms)%s", status, suiteName, testName, durationMs, info);
    }

    public static final class Builder {
        private final List<String> details = new ArrayList<>();
        private String suiteName;
        private String testName;

        public Builder suiteName(String name) { this.suiteName = name; return this; }
        public Builder testName(String name) { this.testName = name; return this; }
        public Builder detail(String detail) { this.details.add(detail); return this; }
        public TestResult pass(long durationMs) { return TestResult.pass(suiteName, testName, durationMs); }
        public TestResult fail(String message, long durationMs) {
            return TestResult.fail(suiteName, testName, message, durationMs, details);
        }
    }
}
