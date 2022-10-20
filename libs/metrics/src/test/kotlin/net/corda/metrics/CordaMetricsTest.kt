package net.corda.metrics

import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class CordaMetricsTest {
    private val meterSourceName = "Testing"
    private val registry = mock<MeterRegistry>()

    @BeforeEach
    @Test
    fun setup() {
        CordaMetrics.configure(meterSourceName, registry)
    }

    @Test
    fun `configure factory sets registry`() {
        assertThat(CordaMetrics.registry.registries).contains(registry)
    }

    @Test
    fun `create meter supports tags name`() {
        val meter = CordaMetrics.Metric.HttpRequestCount
            .builder()
            .withTag(CordaMetrics.Tag.Address, "blah")
            .build()
        assertThat(meter.id.tags.map { Pair(it.key, it.value) })
            .contains(Pair(CordaMetrics.Tag.Address.value, "blah"))
    }

    @Test
    fun `create meter supports vnode tag`() {
        val meter = CordaMetrics.Metric.HttpRequestCount
            .builder()
            .forVirtualNode("ABC")
            .build()
        assertThat(meter.id.tags.map { Pair(it.key, it.value) })
            .contains(Pair(CordaMetrics.Tag.VirtualNode.value, "ABC"))
    }

    @Test
    fun `create http counter sets name`() {
        val meter = CordaMetrics.Metric.HttpRequestCount.builder().build()
        assertThat(meter.id.name).isEqualTo(CordaMetrics.Metric.HttpRequestCount.metricsName)
        assertThat(meter.id.tags.map { Pair(it.key, it.value) })
            .contains(Pair(CordaMetrics.Tag.WorkerType.value, meterSourceName))
    }
}