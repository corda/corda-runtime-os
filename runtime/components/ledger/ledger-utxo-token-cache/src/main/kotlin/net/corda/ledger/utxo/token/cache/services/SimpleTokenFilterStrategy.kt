package net.corda.ledger.utxo.token.cache.services

import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.ClaimQuery

class SimpleTokenFilterStrategy : TokenFilterStrategy {

    override fun filterTokens(cachedTokenSource: Iterable<CachedToken>, claimQuery: ClaimQuery): Iterable<CachedToken> {
        if (claimQuery.tagRegex == null && claimQuery.ownerHash == null) {
            return cachedTokenSource
        }

        val tagMatcher = createTagMatcher(claimQuery.tagRegex)
        val ownerMatcher = createOwnerMatcher(claimQuery.ownerHash)

        return cachedTokenSource.filter {
            tagMatcher(it.tag) && ownerMatcher(it.ownerHash)
        }
    }

    private fun createTagMatcher(pattern: String?): (String?) -> Boolean {
        if (pattern == null) {
            return { _ -> true }
        }

        val matcher = Regex(pattern)
        return { it != null && matcher.containsMatchIn(it) }
    }

    private fun createOwnerMatcher(ownerHash: String?): (String?) -> Boolean {
        return if (ownerHash == null) {
            { _ -> true }
        } else {
            { ownerHash == it }
        }
    }
}
