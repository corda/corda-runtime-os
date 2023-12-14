package net.corda.metrics

import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.step.StepMeterRegistry
import io.micrometer.core.instrument.step.StepRegistryConfig
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.TimeUnit

class MyFileMeterRegistry(step: Duration) : StepMeterRegistry(object: StepRegistryConfig {
    override fun prefix(): String {
        return "log"
    }

    override fun get(key: String): String? {
        return null
    }

    override fun step(): Duration {
        return step
    }

}, Clock.SYSTEM) {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java.name)
    }

    override fun getBaseTimeUnit(): TimeUnit {
        return TimeUnit.MILLISECONDS
    }

    override fun publish() {
        meters.forEach { meter ->
            log.info(meter.id.toString())
            meter.measure().forEach {
                log.info(it.statistic.toString() + "=" + it.value)
            }
        }
    }
}