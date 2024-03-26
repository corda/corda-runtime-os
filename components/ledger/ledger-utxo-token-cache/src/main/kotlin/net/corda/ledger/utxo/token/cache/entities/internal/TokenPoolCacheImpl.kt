package net.corda.ledger.utxo.token.cache.entities.internal

import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.entities.TokenPoolCache
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import java.time.Duration

class TokenPoolCacheImpl(private val expiryPeriodInMillis: Duration) : TokenPoolCache {

    private val cache = mutableMapOf<TokenPoolKey, TokenCache>()

    override fun get(poolKey: TokenPoolKey): TokenCache {
        return cache.getOrPut(poolKey) { TokenCacheImpl(expiryPeriodInMillis) }
    }
}
