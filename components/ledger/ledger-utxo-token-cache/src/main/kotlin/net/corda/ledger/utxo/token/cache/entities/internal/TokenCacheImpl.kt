package net.corda.ledger.utxo.token.cache.entities.internal

import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.TokenCache
import java.time.Instant

class TokenCacheImpl : TokenCache {

    // We expect the calls to a specific pool to always be synchronised, therefore a simple map is sufficient
    private val cachedTokens = mutableMapOf<String, CachedToken>()
    private var lastAddTime = 0L

    override fun add(tokens: Collection<CachedToken>) {
        tokens.associateByTo(cachedTokens) { it.stateRef }
        lastAddTime = Instant.now().toEpochMilli()
    }

    override fun removeAll(stateRefs: Set<String>) {
        stateRefs.forEach { cachedTokens.remove(it) }
    }

    override fun removeAll() {
        cachedTokens.clear()
    }

    override fun getLastAddAgeMs(): Long {
        return Instant.now().toEpochMilli() - lastAddTime
    }

    override fun iterator(): Iterator<CachedToken> {
        return cachedTokens.values.iterator()
    }
}
