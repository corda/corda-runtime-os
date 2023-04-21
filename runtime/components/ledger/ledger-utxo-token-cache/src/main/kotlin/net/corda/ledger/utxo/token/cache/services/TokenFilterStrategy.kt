package net.corda.ledger.utxo.token.cache.services

import net.corda.ledger.utxo.token.cache.entities.CachedToken
import net.corda.ledger.utxo.token.cache.entities.ClaimQuery

/**
 * The [TokenFilterStrategy] implements the filtering strategy used to select tokens for a given [ClaimQuery]
 */
interface TokenFilterStrategy {

    /**
     * Filters an iterable set of tokens based on the [ClaimQuery]
     *
     * @param cachedTokenSource The source of tokens to filter
     * @param claimQuery The criteria used for the filter
     *
     * @return An iterable list of filtered tokens
     */
    fun filterTokens(cachedTokenSource: Iterable<CachedToken>, claimQuery: ClaimQuery): Iterable<CachedToken>
}

