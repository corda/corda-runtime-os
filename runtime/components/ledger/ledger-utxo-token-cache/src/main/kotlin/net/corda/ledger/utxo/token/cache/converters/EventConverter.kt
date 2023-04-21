package net.corda.ledger.utxo.token.cache.converters

import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.ledger.utxo.token.cache.entities.TokenEvent

/**
 * The [EventConverter] maps an incoming event to a [TokenEvent]
 */
interface EventConverter {

    /**
     * Converts a received [TokenPoolCacheEvent] to a [TokenEvent]
     *
     * @param tokenPoolCacheEvent The incoming event to convert
     *
     * @return A new instance of [TokenEvent]
     */
    fun convert(tokenPoolCacheEvent: TokenPoolCacheEvent?): TokenEvent
}

