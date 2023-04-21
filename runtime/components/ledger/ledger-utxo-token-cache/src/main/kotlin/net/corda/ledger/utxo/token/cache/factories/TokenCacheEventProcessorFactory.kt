package net.corda.ledger.utxo.token.cache.factories

import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.messaging.api.processor.StateAndEventProcessor

/**
 * The [TokenCacheEventProcessorFactory] creates instances of the [StateAndEventProcessor]
 */
interface TokenCacheEventProcessorFactory {

    /**
     * Creates an instance of the  [StateAndEventProcessor]
     */
    fun create(): StateAndEventProcessor<TokenPoolCacheKey, TokenPoolCacheState, TokenPoolCacheEvent>
}

