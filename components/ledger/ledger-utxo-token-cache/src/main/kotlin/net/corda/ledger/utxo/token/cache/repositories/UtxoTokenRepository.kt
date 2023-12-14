package net.corda.ledger.utxo.token.cache.repositories

import net.corda.ledger.utxo.token.cache.entities.AvailTokenQueryResult
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey
import java.math.BigDecimal
import javax.persistence.EntityManager

interface UtxoTokenRepository {

    /**
     * Retrieves a set of tokens
     */
    fun findTokens(
        entityManager: EntityManager,
        poolKey: TokenPoolKey,
        ownerHash: String?,
        regexTag: String?,
        maxTokens: Int
    ): AvailTokenQueryResult

    /**
     * Returns the total balance based on the filtering criteria
     */
    fun queryBalance(
        entityManager: EntityManager,
        poolKey: TokenPoolKey,
        ownerHash: String?,
        regexTag: String?
    ): BigDecimal
}
