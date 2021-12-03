package net.corda.processors.crypto

import net.corda.libs.configuration.SmartConfig

/** The processor for a `CryptoWorker`. */
interface CryptoProcessor {
    fun start(instanceId: Int, config: SmartConfig)

    fun stop()
}