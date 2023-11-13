package net.corda.ledger.utxo.token.cache.entities

/**
 * In order to improve performance, tokens are cached in memory. This allows the code to avoid
 * always going to the database. The `TokenPoolCache` class contains a cache for
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
