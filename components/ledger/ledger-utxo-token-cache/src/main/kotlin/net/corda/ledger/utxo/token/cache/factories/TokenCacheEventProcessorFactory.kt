package net.corda.ledger.utxo.token.cache.factories

import net.corda.ledger.utxo.token.cache.services.TokenSelectionSyncRPCProcessor
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.processor.StateAndEventProcessor

/**
 * The [TokenCacheEventProcessorFactory] creates instances of the [TokenSelectionSyncRPCProcessor]
 */
interface TokenCacheEventProcessorFactory {

    fun createTokenSelectionSyncRPCProcessor(
        stateManager: StateManager,
    ): TokenSelectionSyncRPCProcessor
}

