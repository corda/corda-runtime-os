package net.corda.ledger.utxo.token.cache.entities.internal

import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.ledger.utxo.token.cache.entities.TokenPoolCache
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import net.corda.utilities.time.Clock
import java.time.Duration

class TokenPoolCacheImpl(private val expiryPeriod: Duration, private val clock: Clock) : TokenPoolCache {

    private val cache = mutableMapOf<TokenPoolKey, TokenCache>()

    override fun get(poolKey: TokenPoolKey): TokenCache {
        return cache.getOrPut(poolKey) { TokenCacheImpl(expiryPeriod, clock) }
    }
}
