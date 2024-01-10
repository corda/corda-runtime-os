package net.corda.ledger.utxo.token.cache.entities

interface TokenPoolCache {
    /**
     * Gets the cached tokens for a given pool
     *
     * @param poolKey The key for the required [TokenCache]
     */
    fun get(poolKey: TokenPoolKey): TokenCache
}
