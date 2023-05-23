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
        val meter = CordaMetrics.Metric.HttpRequestTime
            .builder()
            .withTag(CordaMetrics.Tag.UriPath, "/hello")
            .withTag(CordaMetrics.Tag.HttpMethod, "GET")
            .withTag(CordaMetrics.Tag.OperationStatus, "200")
            .build()
        assertThat(meter.id.tags.map { Pair(it.key, it.value) })
            .contains(Pair(CordaMetrics.Tag.UriPath.value, "/hello"))
            .contains(Pair(CordaMetrics.Tag.HttpMethod.value, "GET"))
            .contains(Pair(CordaMetrics.Tag.OperationStatus.value, "200"))
    }

    @Test
    fun `create meter supports vnode tag`() {
        val meter = CordaMetrics.Metric.HttpRequestTime
            .builder()
            .forVirtualNode("ABC")
            .build()
        assertThat(meter.id.tags.map { Pair(it.key, it.value) })
            .contains(Pair(CordaMetrics.Tag.VirtualNode.value, "ABC"))
    }

    @Test
    fun `create http counter sets name`() {
        val meter = CordaMetrics.Metric.HttpRequestTime.builder().build()
        assertThat(meter.id.name).isEqualTo("corda.${CordaMetrics.Metric.HttpRequestTime.metricsName}")
        assertThat(meter.id.tags.map { Pair(it.key, it.value) })
            .contains(Pair(CordaMetrics.Tag.WorkerType.value, meterSourceName))
    }
}