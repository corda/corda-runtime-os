package net.corda.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import net.corda.metrics.CordaMetrics.builder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class MeterFactoryTest {
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
        val meter = CordaMetrics.Meters.HttpRequestCount
            .builder()
            .withTag(CordaMetrics.Tags.Address, "blah")
            .build<Counter>(Metrics::counter)
        assertThat(meter.id.tags.map { Pair(it.key, it.value) })
            .contains(Pair(CordaMetrics.Tags.Address.value, "blah"))
    }

    @Test
    fun `create meter supports vnode tag`() {
        val meter = CordaMetrics.Meters.HttpRequestCount
            .builder()
            .forVirtualNode("ABC")
            .build<Counter>(Metrics::counter)
        assertThat(meter.id.tags.map { Pair(it.key, it.value) })
            .contains(Pair(CordaMetrics.Tags.VirtualNode.value, "ABC"))
    }

    @Test
    fun `create http counter sets name`() {
        val meter = CordaMetrics.Meters.HttpRequestCount.builder().build<Counter>(Metrics::counter)
        assertThat(meter.id.name).isEqualTo(CordaMetrics.Meters.HttpRequestCount.meterName)
        assertThat(meter.id.tags.map { Pair(it.key, it.value) })
            .contains(Pair(CordaMetrics.Tags.Source.value, meterSourceName))
    }
}