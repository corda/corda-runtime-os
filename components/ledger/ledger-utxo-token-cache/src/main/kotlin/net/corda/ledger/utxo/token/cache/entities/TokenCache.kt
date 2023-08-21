package net.corda.ledger.utxo.token.cache.entities

import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey

/**
 * The [TokenCache] is a cache of all tokens for a given [TokenPoolCacheKey]
 */
interface TokenCache : Iterable<CachedToken> {

    /**
     * Adds a set of tokens to the cache.
     *
     * Any existing tokens with the same IDs will be replaced
     *
     * @param tokens The list of [CachedToken] to add
     */
    fun add(tokens: Collection<CachedToken>)

    /**
     * Removes a set of [CachedToken] from the cache
     *
     * Refs for tokens that do not exist will be ignored
     *
     * @param stateRefs The list of tokens to be removed
     */
    fun removeAll(stateRefs: Set<String>)
}

interface TokenPoolCache {
    /**
     * Gets the cached tokens for a given pool
     *
     * @param poolKey The key for the required [TokenCache]
     */
    fun get(poolKey: TokenPoolKey): TokenCache
}

class TokenPoolCacheImpl : TokenPoolCache {

    private val cache = mutableMapOf<TokenPoolKey, TokenCache>()
    override fun get(poolKey: TokenPoolKey): TokenCache {
        return cache.getOrPut(poolKey) { TokenCacheImpl() }
    }
}

