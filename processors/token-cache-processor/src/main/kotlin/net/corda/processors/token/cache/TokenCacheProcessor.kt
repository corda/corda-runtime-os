package net.corda.processors.token.cache

import net.corda.libs.configuration.SmartConfig

/** The processor for the `TokenCacheProcessor`. */
interface TokenCacheProcessor {
    fun start(bootConfig: SmartConfig)

    fun stop()
}