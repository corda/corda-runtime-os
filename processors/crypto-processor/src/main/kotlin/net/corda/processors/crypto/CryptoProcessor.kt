package net.corda.processors.crypto

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import net.corda.v5.base.util.contextLogger

/** The processor for a `CryptoWorker`. */
class CryptoProcessor : Lifecycle {
    private companion object {
        val logger = contextLogger()
    }

    /** Starts the processor with the provided [config]. */
    @Suppress("Unused_Parameter")
    fun startup(config: SmartConfig) {
        logger.info("Crypto processor starting.")
        isRunning = true
    }

    override var isRunning = false

    override fun start() = Unit

    override fun stop() = Unit
}