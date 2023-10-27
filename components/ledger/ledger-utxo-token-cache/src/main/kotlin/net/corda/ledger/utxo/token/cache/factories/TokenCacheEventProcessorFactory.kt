package net.corda.ledger.utxo.token.cache.factories

import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.ledger.utxo.token.cache.services.TokenSelectionDelegatedProcessor
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.processor.StateAndEventProcessor

/**
 * The [TokenCacheEventProcessorFactory] creates instances of the [StateAndEventProcessor]
 */
interface TokenCacheEventProcessorFactory {

    /**
     * Creates an instance of the  [StateAndEventProcessor]
     */
    fun create(): StateAndEventProcessor<TokenPoolCacheKey, TokenPoolCacheState, TokenPoolCacheEvent>

    fun createDelegatedProcessor(
        stateManager: StateManager,
        processor: StateAndEventProcessor<TokenPoolCacheKey, TokenPoolCacheState, TokenPoolCacheEvent>
    ): TokenSelectionDelegatedProcessor
}

