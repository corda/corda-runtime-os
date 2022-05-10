package net.corda.processors.crypto

import net.corda.libs.configuration.SmartConfig

/** The processor for a `CryptoWorker`. */
interface CryptoDependenciesProcessor {
    val isRunning: Boolean

    fun start(bootConfig: SmartConfig)

    fun stop()
}