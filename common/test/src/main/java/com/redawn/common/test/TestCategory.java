package com.redawn.common.test;

/**
 * Test layers that share the same result/report contract but run in very different
 * environments. The category is metadata only; it never changes how a test executes.
 */
public enum TestCategory {
    UNIT,
    CLIENT_SIMULATION,
    SERVER_SIMULATION,
    STRESS
}
