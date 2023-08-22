package net.corda.processors.uniqueness

import net.corda.libs.configuration.SmartConfig

/**
 * The uniqueness processor provides fixed-function checking for spent input states and reference
 * states, as well as time window validation.
 */
interface UniquenessProcessor {
    fun start(bootConfig: SmartConfig)

    fun stop()
}
