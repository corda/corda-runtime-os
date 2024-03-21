package net.corda.ledger.utxo.token.cache.handlers

import net.corda.data.flow.event.FlowEvent
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenEvent
import net.corda.ledger.utxo.token.cache.entities.TokenPoolCache
import net.corda.messaging.api.records.Record

/**
 * The [TokenEventHandler] represents the action to be taken when a specific type of event is received.
 */
interface TokenEventHandler<E : TokenEvent> {

    /**
     * Handles the received event
     *
     * @param tokenPoolCache An instance of the current [TokenPoolCache]
     * @param state An instance of the [PoolCacheState] linked to this event
     * @param event The received event.
     */
    fun handle(
        tokenPoolCache: TokenPoolCache,
        state: PoolCacheState,
        event: E
    ): Record<String, FlowEvent>?
}
