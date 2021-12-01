package net.corda.processors.db

import net.corda.applications.workers.workercommon.HealthProvider
import net.corda.libs.configuration.SmartConfig
import net.corda.v5.base.util.contextLogger

/** The processor for a `DBWorker`. */
class DBProcessor {
    private companion object {
        val logger = contextLogger()
    }

    /**
     * Starts the processor.
     *
     * @param healthProvider The [HealthProvider] used to control the worker's healthiness.
     * @param workerConfig The [SmartConfig] required to connect to the bus.
     */
    @Suppress("Unused_Parameter")
    fun startup(healthProvider: HealthProvider, workerConfig: SmartConfig) {
        logger.info("DB processor starting.")
    }
}