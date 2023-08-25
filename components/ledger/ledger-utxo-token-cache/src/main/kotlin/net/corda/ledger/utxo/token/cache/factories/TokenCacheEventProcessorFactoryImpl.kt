package net.corda.ledger.utxo.token.cache.factories

import net.corda.data.ledger.utxo.token.selection.event.TokenPoolCacheEvent
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.ledger.utxo.token.cache.converters.EntityConverter
import net.corda.ledger.utxo.token.cache.converters.EventConverter
import net.corda.ledger.utxo.token.cache.entities.TokenEvent
import net.corda.ledger.utxo.token.cache.entities.TokenPoolCache
import net.corda.ledger.utxo.token.cache.handlers.TokenEventHandler
import net.corda.ledger.utxo.token.cache.services.TokenCacheEventProcessor
import net.corda.messaging.api.processor.StateAndEventProcessor

class TokenCacheEventProcessorFactoryImpl constructor(
    private val eventConverter: EventConverter,
    private val entityConverter: EntityConverter,
    private val poolCache: TokenPoolCache,
    private val tokenCacheEventHandlerMap: Map<Class<*>, TokenEventHandler<in TokenEvent>>
) : TokenCacheEventProcessorFactory {

    override fun create(): StateAndEventProcessor<TokenPoolCacheKey, TokenPoolCacheState, TokenPoolCacheEvent> {
        return TokenCacheEventProcessor(eventConverter, entityConverter,poolCache, tokenCacheEventHandlerMap)
    }
}
