package net.corda.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.composite.CompositeMeterRegistry

class MeterFactory {
    enum class Meters(val meterName: String) {
        HttpRequestsCount("http.server.requests"),
        HttpRequestsTime("http.server.requestTime"),
    }

    enum class Tags(val value: String) {
        Source("source"),
        VirtualNode("vnode"),
        Address("address")
    }

    companion object {
        val registry: CompositeMeterRegistry = Metrics.globalRegistry
        private var name: String? = null

        fun configure(name: String, registry: MeterRegistry) {
            this.name = name
            this.registry.add(registry)
            this.registry.config().commonTags(Tags.Source.value, name)
        }

        fun create(meter: Meters): MeterBuilder {
            if(null == name) {
                throw IllegalStateException("Meter Factory must be configured before using it.")
            }

            return MeterBuilder(meter.meterName)
        }
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