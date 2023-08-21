package net.corda.ledger.utxo.token.cache.entities

class TokenCacheImpl: TokenCache {

    // We expect the calls to a specific pool to always be synchronised, therefore a simple map is sufficient
    private val cachedTokens = mutableMapOf<String, CachedToken>()

    override fun add(tokens: Collection<CachedToken>) {
        cachedTokens.putAll(tokens.map { it.stateRef to it })
    }

    override fun removeAll(stateRefs: Set<String>) {
        stateRefs.forEach { cachedTokens.remove(it) }
    }

    override fun iterator(): Iterator<CachedToken> {
        return cachedTokens.values.iterator()
    }
}
