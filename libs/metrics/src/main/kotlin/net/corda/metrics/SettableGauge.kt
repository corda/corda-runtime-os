package net.corda.metrics

import io.micrometer.core.instrument.Gauge
import java.util.concurrent.atomic.AtomicInteger

class SettableGauge(private val gauge: Gauge, private val gaugeValue: AtomicInteger): Gauge by gauge {

    fun set(value: Int) {
        gaugeValue.set(value)
    }

}