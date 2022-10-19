package net.corda.metrics

import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.Tag as micrometerTag

object CordaMetrics {
    sealed class Metric (
        val metricsName: String,
        private val meter: (String, Iterable<micrometerTag>) -> Meter) {

        fun builder(): MeterBuilder {
            return MeterBuilder(this.metricsName, this.meter)
        }
        /**
         * Number of HTTP Requests.
         */
        object HttpRequestCount : Metric("http.server.request", Metrics::counter)
        /**
         * HTTP Requests time.
         */
        object HttpRequestTime : Metric("http.server.request.time", Metrics::timer)
        /**
         * Time it took to create the sandbox
         */
        object SandboxCreateTime : Metric("sandbox.create.time", Metrics::timer)
    }

    enum class Tag(val value: String) {
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
        WorkerType("workerType"),
        /**
         * Virtual Node for which the metric is applicable.
         */
        VirtualNode("virtualNode"),
    }

    val registry: CompositeMeterRegistry = Metrics.globalRegistry

    /**
     * Configure the Metrics Registry
     *
     * @param workerType Type of Worker, will be tagged to each metric.
     * @param registry Registry instance
     */
    fun configure(workerType: String, registry: MeterRegistry) {
        this.registry.add(registry)
        this.registry.config().commonTags(Tag.WorkerType.value, workerType)
    }

    class MeterBuilder(
        val name: String,
        val func: (String, Iterable<micrometerTag>) -> Any
    ) {
        val allTags: MutableList<io.micrometer.core.instrument.Tag> = mutableListOf()

        /**
         * Tag the metric with the Holding ID short hash for the Virtual Node.
         */
        fun forVirtualNode(holdingId: String): MeterBuilder {
            return withTag(Tag.VirtualNode, holdingId)
        }

        fun withTag(key: Tag, value: String): MeterBuilder {
            allTags.add(io.micrometer.core.instrument.Tag.of(key.value, value))
            return this
        }

        // NOTE: because T is reified, this has to be inline, which means func, name and allTags needs to be public.
        inline fun <reified T: Meter> build(): T {
            return func(name, allTags) as T
        }
    }
}