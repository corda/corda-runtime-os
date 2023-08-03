package net.corda.ledger.utxo.token.cache.repositories

import javax.persistence.EntityManager
import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey

@Suppress("TooManyFunctions")
interface UtxoTokenRepository {

    /** Retrieves tokens */
    fun findTokens(
        entityManager: EntityManager,
        poolKey: TokenPoolCacheKey,
        ownerHash: String?,
        regexTag: String?
    )
}
