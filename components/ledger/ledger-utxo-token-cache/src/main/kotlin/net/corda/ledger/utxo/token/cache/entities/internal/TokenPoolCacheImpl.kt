package net.corda.ledger.utxo.token.cache.entities.internal

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import net.corda.cache.caffeine.CacheFactoryImpl
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.entities.TokenPoolCache
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import java.time.Duration

class TokenPoolCacheImpl(expiryPeriod: Duration) : TokenPoolCache {

    private val cache: Cache<TokenPoolKey, TokenCache> =
        if (expiryPeriod == Duration.ZERO) {
            CacheFactoryImpl().build("token-pool-cache", Caffeine.newBuilder())
        } else {
            CacheFactoryImpl().build("token-pool-cache", Caffeine.newBuilder().expireAfterWrite(expiryPeriod))
        }

    override fun get(poolKey: TokenPoolKey): TokenCache {
        return cache.get(poolKey) { TokenCacheImpl() }
    }

    // This call resets the expiry period
    override fun put(poolKey: TokenPoolKey, tokenCache: TokenCache) {
        cache.put(poolKey, tokenCache)
    }
}
