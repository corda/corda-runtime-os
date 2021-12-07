package net.corda.processors.crypto

import net.corda.libs.configuration.SmartConfig

/** The processor for a `CryptoWorker`. */
interface CryptoProcessor {
    fun start(instanceId: Int, topicPrefix: String, config: SmartConfig)

    fun stop()
}