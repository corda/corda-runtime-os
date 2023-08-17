package net.corda.ledger.utxo.token.cache.entities

class TokenPoolCacheImpl : TokenPoolCache {

    private val cache = mutableMapOf<TokenPoolKey, TokenCache>()

    override fun get(poolKey: TokenPoolKey): TokenCache {
        return cache.getOrPut(poolKey) { TokenCacheImpl() }
    }
}
