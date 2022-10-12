package net.corda.metrics

import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.composite.CompositeMeterRegistry

class MeterFactory {
    enum class TagKeys(val value: String) {
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
            this.registry.config().commonTags(TagKeys.Source.value, name)
        }

        val httpServer = HttpServer()

        class HttpServer {
            companion object {
                const val HTTP_SERVER = "http.server"
            }

            fun requests(): MeterBuilder {
                verifyRegistry()
                return MeterBuilder("${HTTP_SERVER}.requests")
            }
        }

        private fun verifyRegistry() {
            if(null == name) {
                throw IllegalStateException("Meter Factory must be configured before using it.")
            }
        }
    }

    class MeterBuilder(
        private val name: String
    ) {
        private val allTags: MutableList<Tag> = mutableListOf()

        fun forVirtualNode(name: String): MeterBuilder {
            return withTag(TagKeys.VirtualNode, name)
        }

        fun withTag(key: TagKeys, value: String): MeterBuilder {
            allTags.add(Tag.of(key.value, value))
            return this
        }

        fun <T> build(func: (name: String, tags: Iterable<Tag>) -> T): T {
            return func(name, allTags)
        }
    }
}