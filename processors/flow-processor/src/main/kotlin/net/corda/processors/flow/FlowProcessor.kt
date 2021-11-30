package net.corda.processors.flow

import com.typesafe.config.Config
import net.corda.applications.workers.workercommon.HealthProvider
import net.corda.v5.base.util.contextLogger

/** The processor for a `FlowWorker`. */
class FlowProcessor {
    private companion object {
        val logger = contextLogger()
    }

    /**
     * Starts the processor.
     *
     * @param healthProvider The [HealthProvider] used to control the worker's healthiness.
     * @param workerConfig The [Config] required to connect to the bus.
     */
    @Suppress("Unused_Parameter")
    fun startup(healthProvider: HealthProvider, workerConfig: Config) {
        logger.info("Flow processor starting. Config:")
        workerConfig.entrySet().forEach { entry ->
            logger.info("${entry.key} - ${entry.value}")
        }
    }
}