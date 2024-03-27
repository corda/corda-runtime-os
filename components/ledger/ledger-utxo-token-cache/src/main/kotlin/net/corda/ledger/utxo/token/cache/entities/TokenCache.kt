package net.corda.ledger.utxo.token.cache.entities

import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.v5.ledger.utxo.token.selection.Strategy

/**
 * The [TokenCache] is a cache of all tokens for a given [TokenPoolCacheKey]
 */
interface TokenCache {

    /**
     * Returns the tokens present in the cache based on the strategy.
     *
     * @param strategy The strategy that should have been used to retrieve the tokens from the persistence layer.
     */
    fun get(strategy: Strategy): Iterable<CachedToken>

    /**
     * Adds a set of tokens to the cache.
     *
     * Any existing tokens with the same IDs will be replaced
     *
     * @param tokens The list of [CachedToken] to add
     * @param strategy The strategy in which the tokens were retrieved from the persistence layer
     */
    fun add(tokens: Collection<CachedToken>, strategy: Strategy)

    /**
     * Removes a set of [CachedToken] from the cache
     *
     * Refs for tokens that do not exist will be ignored
     *
     * @param stateRefs The list of tokens to be removed
     */
    fun removeAll(stateRefs: Set<String>)

    /**
     * Empties the cache
     */
    fun removeAll()
}
