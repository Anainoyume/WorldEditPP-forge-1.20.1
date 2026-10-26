package com.redawn.common.test;

import java.util.Objects;

/** Assertion primitives for suites that cannot or should not depend on a JUnit runtime. */
public final class TestAssertions {
    private TestAssertions() { }

    public static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void equal(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + " [expected=" + expected + ", actual=" + actual + "]");
        }
    }

    public static void equalFloat(float expected, float actual, float delta, String message) {
        if (Math.abs(expected - actual) > delta) {
            throw new AssertionError(message + " [expected=" + expected + ", actual=" + actual + "]");
        }
    }

    public static void fail(String message) {
        throw new AssertionError(message);
    }
}
