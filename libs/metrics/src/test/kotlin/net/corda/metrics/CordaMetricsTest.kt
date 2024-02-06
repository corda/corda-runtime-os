package net.corda.metrics

import io.micrometer.core.instrument.Meter
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import net.corda.metrics.CordaMetrics.Tag.MembershipGroup
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Condition
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import kotlin.math.roundToLong

@ResourceLock("corda-metrics")
class CordaMetricsTest {
    private val meterSourceName = "Testing"
    private val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    private fun singleValueOf(expected: Long): Condition<in Meter> {
        return Condition<Meter>({ m ->
            m.measure().single().value.roundToLong() == expected
        }, "value of %d", expected)
    }

    @BeforeEach
    fun setup() {
        CordaMetrics.configure(
            meterSourceName,
            registry,
            ("corda_p2p_session_(inbound|outbound)|" +
                    "corda_membership_memberlist_cache_size|" +
                    "corda_flow_execution_time_seconds_(count|sum|max)|" +
                    "corda_http_server_request_time_seconds_(count|sum|max|bucket)").toRegex(),
            "virtualnode_source".toRegex()
        )
        assertThat(CordaMetrics.registry.registries).hasSize(1)
    }

    @AfterEach
    fun done() {
        CordaMetrics.registry.clear()
        CordaMetrics.registry.remove(registry)
    }

    @Test
    fun `configure factory sets registry`() {
        assertThat(CordaMetrics.registry.registries).contains(registry)
    }

    @Test
    fun `gauge with computed values`() {
        val items = mutableListOf<String>()
        val gauge = CordaMetrics.Metric.OutboundSessionCount(items::size).builder().build()
        assertThat(CordaMetrics.registry.meters)
            .hasSize(1)
            .element(0).isEqualTo(gauge).has(singleValueOf(0))

        items += "Hello Metrics!"
        assertThat(gauge).has(singleValueOf(1))

        items += "Goodbye, Cruel Metrics!"
        assertThat(gauge).has(singleValueOf(2))

        val gaugeId = CordaMetrics.Metric.OutboundSessionCount { Double.NaN }.builder().buildPreFilterId()
        CordaMetrics.registry.removeByPreFilterId(gaugeId)

        assertThat(CordaMetrics.registry.meters).isEmpty()
    }

    @Test
    fun `gauge with computed property of weak object`() {
        val items = mutableListOf<String>()
        val gauge = CordaMetrics.Metric.Membership.MemberListCacheSize(items).builder().build()
        assertThat(CordaMetrics.registry.meters)
            .hasSize(1)
            .element(0).isEqualTo(gauge).has(singleValueOf(0))

        items += "Hello Weak Object!"
        assertThat(gauge).has(singleValueOf(1))

        items += "Goodbye, Cruel Object!"
        assertThat(gauge).has(singleValueOf(2))

        val gaugeId = CordaMetrics.Metric.Membership.MemberListCacheSize(null).builder().buildPreFilterId()
        CordaMetrics.registry.removeByPreFilterId(gaugeId)

        assertThat(CordaMetrics.registry.meters).isEmpty()
    }

    @Test
    fun `gauges with tags`() {
        val things = mutableListOf<String>()
        val thingsGauge = CordaMetrics.Metric.InboundSessionCount(things::size).builder()
            .withTag(MembershipGroup, "things")
            .build()

        val stuff = mutableListOf<String>()
        val stuffGauge = CordaMetrics.Metric.InboundSessionCount(stuff::size).builder()
            .withTag(MembershipGroup, "stuff")
            .build()

        assertThat(CordaMetrics.registry.meters)
            .containsExactlyInAnyOrder(thingsGauge, stuffGauge)

        val thingsGaugeId = CordaMetrics.Metric.InboundSessionCount { Double.NaN }.builder()
            .withTag(MembershipGroup, "things")
            .buildPreFilterId()
        CordaMetrics.registry.removeByPreFilterId(thingsGaugeId)

        assertThat(CordaMetrics.registry.meters).containsExactly(stuffGauge)
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

    @Test
    fun `summary metrics allowed for timer but not histogram`() {
        val meter = CordaMetrics.Metric.FlowExecutionTime.builder().build()
        assertThat(CordaMetrics.registry.meters)
            .hasSize(1)
        assertThat(meter.takeSnapshot().histogramCounts())
            .isEmpty()
    }

    @Test
    fun `all metrics allowed for timer`() {
        val meter = CordaMetrics.Metric.HttpRequestTime.builder().build()
        assertThat(CordaMetrics.registry.meters)
            .hasSize(1)
        assertThat(meter.takeSnapshot().histogramCounts())
            .isNotEmpty()
    }

    @Test
    fun `metric not matching keep names is filtered out`() {
        CordaMetrics.Metric.SandboxCreateTime.builder().build()
        assertThat(CordaMetrics.registry.meters)
            .hasSize(0)
    }

    @Test
    fun `dropped tags are filtered out`() {
        val meter = CordaMetrics.Metric.HttpRequestTime
            .builder()
            .withTag(CordaMetrics.Tag.UriPath, "/hello")
            .withTag(CordaMetrics.Tag.SourceVirtualNode, "abc")
            .build()
        assertThat(meter.id.tags.map { Pair(it.key, it.value) })
            .contains(Pair(CordaMetrics.Tag.UriPath.value, "/hello"))
            .doesNotContain(Pair(CordaMetrics.Tag.SourceVirtualNode.value, "abc"))
    }
}
