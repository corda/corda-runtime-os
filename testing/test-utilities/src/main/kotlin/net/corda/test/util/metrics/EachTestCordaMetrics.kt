package net.corda.test.util.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import net.corda.metrics.CordaMetrics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * A JUnit extension that configures [CordaMetrics] for each test in a given class.
 */
class EachTestCordaMetrics(
    private val workerType: String,
    private val registry: MeterRegistry
) : BeforeEachCallback, AfterEachCallback {
    constructor(workerType: String) : this(workerType, SimpleMeterRegistry())

    override fun beforeEach(ctx: ExtensionContext) {
        CordaMetrics.configure(workerType, registry, null, null)
        assertEquals(1, CordaMetrics.registry.registries.size)
    }

    override fun afterEach(ctx: ExtensionContext) {
        CordaMetrics.registry.clear()
        CordaMetrics.registry.remove(registry)
    }
}
