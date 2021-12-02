package net.corda.processors.db

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.v5.base.util.contextLogger

/** The processor for a `DBWorker`. */
class DBProcessor : Lifecycle {
    private companion object {
        val logger = contextLogger()
    }

    /** Starts the processor with the provided [config]. */
    @Suppress("Unused_Parameter")
    fun startup(config: SmartConfig) {
        logger.info("DB processor starting.")
        isRunning = true
    }

    override var isRunning = false

    override fun start() = Unit

    override fun stop() = Unit
}