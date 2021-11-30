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
     * @param busConfig The [Config] required to connect to the bus.
     */
    fun startup(healthProvider: HealthProvider, busConfig: Config) {
        logger.info("Flow processor starting")
        busConfig.entrySet().forEach { entry ->
            logger.info(entry.key)
            logger.info(entry.value.toString())
            logger.info("")
        }
    }
}