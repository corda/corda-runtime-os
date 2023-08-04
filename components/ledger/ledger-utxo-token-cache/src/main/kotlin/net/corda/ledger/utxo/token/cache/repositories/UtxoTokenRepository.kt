package net.corda.ledger.utxo.token.cache.repositories

import java.math.BigDecimal
import javax.persistence.EntityManager
import net.corda.ledger.utxo.token.cache.entities.AvailTokenQueryResult
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey

@Suppress("TooManyFunctions")
interface UtxoTokenRepository {

    /** Retrieves tokens */
    fun findTokens(
        entityManager: EntityManager,
        poolKey: TokenPoolKey,
        ownerHash: String?,
        regexTag: String?
    ): AvailTokenQueryResult

    fun queryAvailableBalance(
        entityManager: EntityManager,
        poolKey: TokenPoolKey,
        ownerHash: String?,
        regexTag: String?,
        stateRefClaimedTokens: Collection<String>
    ): BigDecimal

    fun queryTotalBalance(
        entityManager: EntityManager,
        poolKey: TokenPoolKey,
        ownerHash: String?,
        regexTag: String?
    ): BigDecimal
}
