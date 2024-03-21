package net.corda.ledger.utxo.token.cache.entities.internal

import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.entities.TokenPoolCache
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.DurationUnit

class TokenPoolCacheImpl(expiryPeriod: Duration) : TokenPoolCache {

    private val cache = if(expiryPeriod == Duration.ZERO) {
        Caffeine.newBuilder().build<TokenPoolKey, TokenCache>()
    } else {
        Caffeine.newBuilder()
            .expireAfterWrite(expiryPeriod.toLong(DurationUnit.MILLISECONDS), TimeUnit.MILLISECONDS)
            .build<TokenPoolKey, TokenCache>()
    }

    override fun get(poolKey: TokenPoolKey): TokenCache {
        return cache.get(poolKey) { TokenCacheImpl() }
    }

    // This call resets the expiry period
    override fun put(poolKey: TokenPoolKey, tokenCache: TokenCache) {
        cache.put(poolKey, tokenCache)
    }
}
