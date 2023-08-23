package net.corda.processors.db

import net.corda.libs.configuration.SmartConfig

/** The processor for a `EVMWorker`. */
interface EVMProcessor {
    /**
     * Starts performing the work of the EVM worker.
     *
     * @throws EVMProcessorException If the EVM Network cannot be connected to.
     */
    fun start(bootConfig: SmartConfig)

    fun stop()
}