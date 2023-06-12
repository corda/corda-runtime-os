@file:JvmName("CordaMetrics")
package net.corda.test.util.metrics

/**
 * Key value for a JUnit [ResourceLock][org.junit.jupiter.api.parallel.ResourceLock].
 * Required by JUnit's parallel execution when running in concurrent mode:
 * - `junit.jupiter.execution.parallel.enabled=true`
 * - `junit.jupiter.execution.parallel.mode.default=concurrent`
 */
const val CORDA_METRICS_LOCK = "corda-metrics"
