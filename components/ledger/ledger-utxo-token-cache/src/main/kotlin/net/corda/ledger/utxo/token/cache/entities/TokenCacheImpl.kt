package net.corda.ledger.utxo.token.cache.entities

import net.corda.data.ledger.utxo.token.selection.state.TokenPoolCacheState
import net.corda.ledger.utxo.token.cache.converters.EntityConverter

/**
 * This is a very primitive implementation of the cached, available tokens for a given pool. The implementation is a
 * simple facade over the state object received through the state and event pattern. This implementation will be
 * replaced once `the messaging API can be updated to support state and events pattern with multiple compacted topics.
 * CORE-6975
 */
class TokenCacheImpl(
    private val tokenPoolCacheState: TokenPoolCacheState,
    private val entityConverter: EntityConverter
) : TokenCache {

    override fun add(tokens: List<CachedToken>) {
        // This is not very efficient, but it's good enough for this implementation where the list of available tokens
        // is stored in the state object.
        val refsToAdd = tokens.map { it.stateRef }.toSet()
        val existingTokens = tokenPoolCacheState.availableTokens.toMutableList()
        existingTokens.removeIf { refsToAdd.contains(it.stateRef) }
        existingTokens.addAll(tokens.map { it.toAvro() })
        tokenPoolCacheState.availableTokens = existingTokens
    }

    override fun removeAll(stateRefs: Set<String>) {
        tokenPoolCacheState.availableTokens = tokenPoolCacheState
            .availableTokens
            .toMutableList().apply {
                removeIf { stateRefs.contains(it.stateRef) }
            }
    }

    override fun iterator(): Iterator<CachedToken> {
        return tokenPoolCacheState.availableTokens
            .map { entityConverter.toCachedToken(it) }
            .iterator()
    }
}
