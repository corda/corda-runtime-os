package net.corda.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics
import io.micrometer.core.instrument.composite.CompositeMeterRegistry

object CordaMetrics {
    enum class Meters(val meterName: String) {
        /**
         * Number of HTTP Requests.
         */
        HttpRequestCount("http.server.requestCount"),
        /**
         * HTTP Requests time.
         */
        HttpRequestTime("http.server.requestTime"),
    }

    enum class Tags(val value: String) {
        /**
         * Source of metric.
         */
        Source("source"),
        /**
         * Virtual Node for which the metric is applicable.
         */
        VirtualNode("virtualNode"),
        /**
         * Address for which the metric is applicable..
         */
        Address("address")
    }

    fun Meters.builder(): MeterBuilder {
        return MeterBuilder(this.meterName)
    }

    val registry: CompositeMeterRegistry = Metrics.globalRegistry

    fun configure(name: String, registry: MeterRegistry) {
        this.registry.add(registry)
        this.registry.config().commonTags(Tags.Source.value, name)
    }

    class MeterBuilder(
        private val name: String
    ) {
        private val allTags: MutableList<Tag> = mutableListOf()

        // special case for VirtualNode - TODO - is this worth it?
        fun forVirtualNode(name: String): MeterBuilder {
            return withTag(Tags.VirtualNode, name)
        }

        fun withTag(key: Tags, value: String): MeterBuilder {
            allTags.add(Tag.of(key.value, value))
            return this
        }

        fun <T> build(func: (name: String, tags: Iterable<Tag>) -> T): T {
            return func(name, allTags)
        }
    }
}