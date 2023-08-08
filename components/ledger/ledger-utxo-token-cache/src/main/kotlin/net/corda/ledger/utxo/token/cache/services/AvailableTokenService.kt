package net.corda.ledger.utxo.token.cache.services

import net.corda.ledger.utxo.token.cache.entities.AvailTokenQueryResult
import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.ledger.utxo.token.cache.entities.TokenBalance
import net.corda.ledger.utxo.token.cache.entities.TokenPoolKey

/**
 * [AvailableTokenService] allows to insert and find UTXO signed transactions in the persistent store provided
 * by the platform.
 */
interface AvailableTokenService {

    /**
     * TODO - UPDATE THIS COMMENT
     * Find a UTXO signed transaction in the persistence context given it's [id].
     *
     * @param id UTXO signed transaction ID.
     * @param transactionStatus filter for this status.
     *
     * @return The found UTXO signed transaction, null if it could not be found in the persistence context.
     *
     * @throws CordaPersistenceException if an error happens during find operation.
     */
    fun findAvailTokens(poolKey: TokenPoolKey, ownerHash: String?, tagRegex: String?): AvailTokenQueryResult

    fun queryBalance(poolKey: TokenPoolKey, ownerHash: String?, tagRegex: String?, stateRefClaimedTokens: Collection<String>): TokenBalance

}