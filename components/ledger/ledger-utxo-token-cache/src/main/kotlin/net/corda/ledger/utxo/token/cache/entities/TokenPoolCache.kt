package net.corda.ledger.utxo.token.cache.entities

import net.corda.v5.ledger.utxo.token.selection.Strategy

/**
 * In order to improve performance, tokens are cached in memory. This avoids the need of always going to
 * the database to fetch new tokens. The `TokenPoolCache` class contains a cache for
 * each group of tokens which are identified by their key.
 */
interface TokenPoolCache {
    /**
     * Gets the cached tokens for a given pool
     *
     * @param poolKey The key for the required [TokenCache]
     */
    fun get(poolKey: TokenPoolKey): TokenCache
}
