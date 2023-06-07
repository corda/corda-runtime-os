package net.corda.metrics

import io.micrometer.core.instrument.Gauge
import java.util.concurrent.atomic.AtomicInteger

/**
 * A [Gauge] metric is for measuring the current value of some property, but it doesn't necessarily need
 * to wrap an object. You can also build a [Gauge] around a [Supplier&lt;Number&gt;][java.util.function.Supplier]
 * lambda. For example, to gauge the size of some [Collection] you really only need something like:
 *
 * ```kotlin
 * object CordaMetrics {
 *     sealed class Metric<T : Meter> {
 *
 *         class MyCollectionSize(computation: Supplier<Number>) : ComputedValue("my.collection.size", computation)
 *     }
 * }
 *
 * CordaMetrics.Metric.MyCollectionSize { myCollection.size }.builder().build()
 * ```
 * A [ComputedValue][CordaMetrics.Metric.ComputedValue] gauge is simpler because we will automatically
 * collect the latest value without needing to update an [AtomicInteger] manually.
 */
class SettableGauge(private val gauge: Gauge, private val gaugeValue: AtomicInteger): Gauge by gauge {

    fun set(value: Int) {
        gaugeValue.set(value)
    }

}