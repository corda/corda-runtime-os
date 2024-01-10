package net.corda.ledger.utxo.token.cache.factories

import net.corda.ledger.utxo.token.cache.services.TokenSelectionSyncHttpProcessor
import net.corda.libs.statemanager.api.StateManager

/**
 * The [TokenCacheEventProcessorFactory] creates instances of the [TokenSelectionSyncHttpProcessor]
 */
interface TokenCacheEventProcessorFactory {

    /**
     * Creates an instance of the [TokenSelectionSyncHttpProcessor]
     */
    fun createTokenSelectionSyncHttpProcessor(
        stateManager: StateManager,
    ): TokenSelectionSyncHttpProcessor
}
