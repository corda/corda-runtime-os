package net.corda.ledger.utxo.token.cache.services

import net.corda.data.flow.event.FlowEvent
import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.ledger.utxo.token.cache.converters.EntityConverter
import net.corda.ledger.utxo.token.cache.entities.TokenEvent
import net.corda.ledger.utxo.token.cache.entities.TokenPoolCache
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import net.corda.ledger.utxo.token.cache.handlers.TokenEventHandler
import org.slf4j.LoggerFactory

class TokenPoolCacheManager(
    private val tokenPoolCache: TokenPoolCache,
    private val eventHandlerMap: Map<Class<*>, TokenEventHandler<in TokenEvent>>,
    private val entityConverter: EntityConverter
) {

    private companion object {
        val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun processEvent(
        state: TokenPoolCacheState,
        poolKey: TokenPoolKey,
        tokenEvent: TokenEvent
    ): ResponseAndState {

        removeInvalidClaims(state)

        val poolCacheState = entityConverter.toPoolCacheState(state)
        val tokenCache = tokenPoolCache.get(poolKey)

        poolCacheState.removeExpiredClaims()

        // Get the handler that knows how to process the event
        val handler = checkNotNull(eventHandlerMap[tokenEvent.javaClass]) {
            "Received an event with and unrecognized payload '${tokenEvent.javaClass}'"
        }

        // Ask the respective handler to process the event
        val result = handler.handle(tokenCache, poolCacheState, tokenEvent)

        return ResponseAndState(result?.value, poolCacheState.toAvro())
    }

    fun removeAllCachedTokens(poolKey: TokenPoolKey) {
        val tokenCache = tokenPoolCache.get(poolKey)
        tokenCache.removeAll()
    }

    // Temporary logic that covers the upgrade from release/5.0 to release/5.1
    // The field claimedTokens has been added to the TokenCaim avro object, and it will replace claimedTokenStateRefs.
    // In order to avoid breaking compatibility, the claimedTokenStateRefs has been deprecated, and it will eventually
    // be removed. Any claim that contains a non-empty claimedTokenStateRefs field are considered invalid because
    // this means the avro object is an old one, and it should be replaced by the new format.
    private fun removeInvalidClaims(state: TokenPoolCacheState) {
        val invalidClaims =
            state.tokenClaims.filterNot { it.claimedTokenStateRefs.isNullOrEmpty() }
        if (invalidClaims.isNotEmpty()) {
            val invalidClaimsId = invalidClaims.map { it.claimId }
            logger.warn("Invalid claims were found and have been discarded. Invalid claims: ${invalidClaimsId}")
        }
    }
    data class ResponseAndState(val response: FlowEvent?, val state: TokenPoolCacheState)
}