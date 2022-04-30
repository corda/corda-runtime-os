package net.corda.crypto.client

import net.corda.data.crypto.wire.hsm.HSMConfig
import net.corda.lifecycle.Lifecycle

/**
 * The HSM registration client to generate keys.
 */
interface HSMConfigurationClient : Lifecycle {
    /**
     * Adds new HSM configuration.
     */
    fun putHSM(config: HSMConfig)
}