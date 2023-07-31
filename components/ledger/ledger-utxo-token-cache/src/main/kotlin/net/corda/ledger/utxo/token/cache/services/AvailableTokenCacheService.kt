package net.corda.ledger.utxo.token.cache.services

import net.corda.data.ledger.utxo.token.selection.key.TokenPoolCacheKey
import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

/**
 * [AvailableTokenCacheService] allows to insert and find UTXO signed transactions in the persistent store provided
 * by the platform.
 */
interface AvailableTokenCacheService {

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
    @Suspendable
    fun find(poolKey: TokenPoolCacheKey)
}