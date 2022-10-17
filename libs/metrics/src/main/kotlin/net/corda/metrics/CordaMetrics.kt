package net.corda.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.composite.CompositeMeterRegistry

object CordaMetrics {
    enum class Meters(val meterName: String) {
        /**
         * Number of HTTP Requests.
         */
        HttpRequestCount("http.server.request.count"),
        /**
         * HTTP Requests time.
         */
        HttpRequestTime("http.server.request.time"),
        /**
         * Time it took to create the sandbox
         */
        SandboxCreateTime("sandbox.create.time")
    }

    enum class Tags(val value: String) {
        /**
         * Address for which the metric is applicable.
         */
        Address("address"),
        /**
         * Type of the SandboxGroup to which the metric applies.
         */
        SandboxGroupType("sandboxGroupType"),
        /**
         * Source of metric.
         */
        Source("source"),
        /**
         * Virtual Node for which the metric is applicable.
         */
        VirtualNode("virtualNode"),
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

        /**
         * Tag the metric with the Holding ID short hash for the Virtual Node.
         */
        fun forVirtualNode(holdingId: String): MeterBuilder {
            return withTag(Tags.VirtualNode, holdingId)
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