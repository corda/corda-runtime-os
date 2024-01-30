package net.corda.test.util.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import net.corda.metrics.CordaMetrics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * A JUnit extension that configures [CordaMetrics] for all tests in a given class.
 */
class AllTestsCordaMetrics(
    private val workerType: String,
    private val registry: MeterRegistry
) : BeforeAllCallback, AfterEachCallback, AfterAllCallback {
    constructor(workerType: String) : this(workerType, SimpleMeterRegistry())

    override fun beforeAll(ctx: ExtensionContext) {
        CordaMetrics.configure(workerType, registry, null, null)
        assertEquals(1, CordaMetrics.registry.registries.size)
    }

    override fun afterEach(ctx: ExtensionContext) {
        CordaMetrics.registry.clear()
    }

    override fun afterAll(ctx: ExtensionContext) {
        CordaMetrics.registry.remove(registry)
    }
}
