package net.corda.ledger.utxo.token.cache.entities.internal

import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.TokenCache

class TokenCacheImpl : TokenCache {

    // We expect the calls to a specific pool to always be synchronised, therefore a simple map is sufficient
    private val cachedTokens = mutableMapOf<String, CachedToken>()

    override fun add(tokens: Collection<CachedToken>) {
        tokens.associateByTo(cachedTokens) { it.stateRef }
    }

    override fun removeAll(stateRefs: Set<String>) {
        stateRefs.forEach { cachedTokens.remove(it) }
    }

    override fun removeAll() {
        cachedTokens.clear()
    }

    override fun iterator(): Iterator<CachedToken> {
        return cachedTokens.values.iterator()
    }
}
