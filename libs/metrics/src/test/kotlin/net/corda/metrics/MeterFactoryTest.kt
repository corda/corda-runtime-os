package net.corda.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock

class MeterFactoryTest {
    private val meterSourceName = "Testing"
    private val registry = mock<MeterRegistry>()

    @BeforeEach
    @Test
    fun setup() {
        MeterFactory.configure(meterSourceName, registry)
    }

    @Test
    fun `configure factory sets registry`() {
        assertThat(MeterFactory.registry.registries).contains(registry)
    }

    @Test
    fun `create meter supports tags name`() {
        val meter = MeterFactory
            .httpServer
            .requests()
            .withTag(MeterFactory.TagKeys.Uri, "blah")
            .build<Any>(Metrics::counter)
        assertThat(meter.id.tags.map { Pair(it.key, it.value) })
            .contains(Pair(MeterFactory.TagKeys.Uri.value, "blah"))
    }

    @Test
    fun `create meter supports vnode tag`() {
        val meter = MeterFactory
            .httpServer
            .requests()
            .forVirtualNode("ABC")
            .build<Any>(Metrics::counter)
        assertThat(meter.id.tags.map { Pair(it.key, it.value) })
            .contains(Pair(MeterFactory.TagKeys.VirtualNode.value, "ABC"))
    }

    @Test
    fun `create http counter sets name`() {
        val meter = MeterFactory.httpServer.requests().build<Any>(Metrics::counter)
        assertThat(meter.id.name).isEqualTo("${MeterFactory.Companion.HttpServer.HTTP_SERVER}.requests")
        assertThat(meter.id.tags.map { Pair(it.key, it.value) })
            .contains(Pair(MeterFactory.TagKeys.Source.value, meterSourceName))
    }
}

class UnConfiguredMeterFactoryTest {
    @Test
    fun `if not configured, throw`() {
        assertThrows<IllegalStateException> {
            MeterFactory.httpServer.requests().build<Any>(Metrics::counter)
        }
    }
}