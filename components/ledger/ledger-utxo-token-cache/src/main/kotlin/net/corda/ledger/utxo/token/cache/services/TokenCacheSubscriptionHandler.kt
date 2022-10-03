package net.corda.ledger.utxo.token.cache.services

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle

/**
 * The [TokenCacheSubscriptionHandler] controls the lifecycle of event subscriptions used by the token cache
 */
interface TokenCacheSubscriptionHandler: Lifecycle {

    /**
     * Receives a change in configuration.
     *
     * When called this method will close any existing subscriptions and create a new set based on the configuration
     *
     * @param config the latest system configuration
     */
    fun onConfigChange(config: Map<String, SmartConfig>)
}
