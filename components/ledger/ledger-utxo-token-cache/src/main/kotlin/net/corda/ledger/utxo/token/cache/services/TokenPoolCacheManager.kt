package net.corda.ledger.utxo.token.cache.services

import net.corda.data.flow.event.FlowEvent
import net.corda.ledger.utxo.token.cache.entities.PoolCacheState
import net.corda.ledger.utxo.token.cache.entities.TokenEvent
import net.corda.ledger.utxo.token.cache.entities.TokenPoolCache
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import net.corda.ledger.utxo.token.cache.handlers.TokenEventHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TokenPoolCacheManager(
    private val tokenPoolCache: TokenPoolCache,
    private val eventHandlerMap: Map<Class<*>, TokenEventHandler<in TokenEvent>>,
) {
    fun processEvent(
        state: PoolCacheState,
        poolKey: TokenPoolKey,
        tokenEvent: TokenEvent
    ): ResponseAndState {
        // Cleanup
        state.removeInvalidClaims()
        state.removeExpiredClaims()

        // Get the handler that knows how to process the event
        val handler = checkNotNull(eventHandlerMap[tokenEvent.javaClass]) {
            "Received an event with and unrecognized payload '${tokenEvent.javaClass}'"
        }

        // Ask the respective handler to process the event
        val tokenCache = tokenPoolCache.get(poolKey)
        val result = handler.handle(tokenCache, state, tokenEvent)

        return ResponseAndState(result?.value, state)
    }

    fun removeAllTokensFromCache(poolKey: TokenPoolKey) {
        val tokenCache = tokenPoolCache.get(poolKey)
        tokenCache.removeAll()
    }

    data class ResponseAndState(val response: FlowEvent?, val state: PoolCacheState)
}
