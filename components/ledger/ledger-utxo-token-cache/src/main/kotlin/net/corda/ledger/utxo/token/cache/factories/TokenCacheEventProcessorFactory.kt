package net.corda.ledger.utxo.token.cache.factories

import net.corda.ledger.utxo.token.cache.services.TokenSelectionSyncRPCProcessor
import net.corda.libs.statemanager.api.StateManager

/**
 * The [TokenCacheEventProcessorFactory] creates instances of the [TokenSelectionSyncRPCProcessor]
 */
interface TokenCacheEventProcessorFactory {

    /**
     * Creates an instance of the [TokenSelectionSyncRPCProcessor]
     */
    fun createTokenSelectionSyncRPCProcessor(
        stateManager: StateManager,
    ): TokenSelectionSyncRPCProcessor
}
