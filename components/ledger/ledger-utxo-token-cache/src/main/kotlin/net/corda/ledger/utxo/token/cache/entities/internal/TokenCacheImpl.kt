package net.corda.ledger.utxo.token.cache.entities.internal

import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.utilities.time.Clock
import net.corda.v5.ledger.utxo.token.selection.Strategy
import java.time.Duration

class TokenCacheImpl(private val expiryPeriod: Duration, private val clock: Clock) : TokenCache {

    private var expiryTime = clock.instant()
    private var strategy: Strategy = Strategy.RANDOM

    // We expect the calls to a specific pool to always be synchronised, therefore a simple map is sufficient
    private val cachedTokens = mutableMapOf<String, CachedToken>()

    override fun add(tokens: Collection<CachedToken>, strategy: Strategy) {
        expiryTime = clock.instant().plus(expiryPeriod)
        this.strategy = strategy
        tokens.associateByTo(cachedTokens) { it.stateRef }
    }

    override fun removeAll(stateRefs: Set<String>) {
        stateRefs.forEach { cachedTokens.remove(it) }
    }

    override fun removeAll() {
        cachedTokens.clear()
    }

    override fun get(strategy: Strategy): Iterable<CachedToken> {
        if (strategyChanged(strategy) || cacheExpired()) {
            return emptySet()
        }

        return cachedTokens.values
    }

    private fun strategyChanged(strategy: Strategy) =
        this.strategy != strategy

    private fun cacheExpired() =
        strategy == Strategy.PRIORITY && expiryTime < clock.instant()
}
