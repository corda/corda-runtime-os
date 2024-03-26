package net.corda.ledger.utxo.token.cache.entities.internal

import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import net.corda.v5.ledger.utxo.token.selection.Strategy
import java.time.Duration
import java.time.Instant

class TokenCacheImpl(private val expiryPeriodInMillis: Duration) : TokenCache {

    private var expiryTime = Instant.now()
    private var strategy: Strategy = Strategy.RANDOM

    // We expect the calls to a specific pool to always be synchronised, therefore a simple map is sufficient
    private val cachedTokens = mutableMapOf<String, CachedToken>()

    override fun add(tokens: Collection<CachedToken>, strategy: Strategy) {
        expiryTime = Instant.now().plus(expiryPeriodInMillis)
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
        if (this.strategy != strategy || strategy == Strategy.PRIORITY && expiryTime < Instant.now()) {
            return emptySet()
        }

        return cachedTokens.values
    }
}
