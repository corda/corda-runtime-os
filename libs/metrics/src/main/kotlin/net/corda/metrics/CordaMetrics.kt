package net.corda.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.Tag as micrometerTag


object CordaMetrics {
    sealed class Metric<T: Meter> (
        val metricsName: String,
        private val meter: (String, Iterable<micrometerTag>) -> T) {

        fun builder(): MeterBuilder<T> {
            return MeterBuilder(this.metricsName, this.meter)
        }
        /**
         * Number of HTTP Requests.
         */
        object HttpRequestCount : Metric<Counter>("http.server.request", Metrics::counter)
        /**
         * HTTP Requests time.
         */
        object HttpRequestTime : Metric<Timer>("http.server.request.time", Metrics::timer)
        /**
         * Time it took to create the sandbox
         */
        object SandboxCreateTime : Metric<Timer>("sandbox.create.time", Metrics::timer)

        /**
         * Time it took for a flow to complete sucessfully or to error.
         */
        object FlowRunTime : Metric<Timer>("flow.run.time", Metrics::timer)
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

        /**
         * Flow class for which the metric is applicable.
         */
        FlowClass("flowClass"),


        /**
         * Flow Id for which the metric is applicable.
         */
        FlowId("flowId"),

        /**
         * The status of the operation. Can be used to indicate whether an operation was successful or failed.
         */
        OperationStatus("operationStatus"),
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
        this.registry.config()
            .meterFilter(object : MeterFilter {
                override fun map(id: Meter.Id): Meter.Id {
                    // prefix all metrics with `corda`, except standard JVM and Process metrics
                    @Suppress("ComplexCondition")
                    if(
                        id.name.startsWith("corda") ||
                        id.name.startsWith("jvm") ||
                        id.name.startsWith("system") ||
                        id.name.startsWith("process")) {
                        return id
                    }
                    return id.withName("corda." + id.name)
                }
            })
            .commonTags(Tag.WorkerType.value, workerType)
    }

    class MeterBuilder<T: Meter>(
        val name: String,
        val func: (String, Iterable<micrometerTag>) -> T
    ) {
        val allTags: MutableList<io.micrometer.core.instrument.Tag> = mutableListOf()

        /**
         * Tag the metric with the Holding ID short hash for the Virtual Node.
         */
        fun forVirtualNode(holdingId: String): MeterBuilder<T> {
            return withTag(Tag.VirtualNode, holdingId)
        }

        fun withTag(key: Tag, value: String): MeterBuilder<T> {
            allTags.add(io.micrometer.core.instrument.Tag.of(key.value, value))
            return this
        }

        fun build(): T {
            return func(name, allTags)
        }
    }
}