package net.corda.ledger.consensual.persistence

import net.corda.ledger.common.impl.transaction.CordaPackageSummary
import net.corda.ledger.consensual.impl.transaction.ConsensualSignedTransactionImpl
import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction

/**
 * [LedgerPersistenceService] allows to insert and find consensual signed transactions in the persistent store provided
 * by the platform.
 */
interface LedgerPersistenceService {
    /**
     * Persist a [ConsensualSignedTransactionImpl] to the store.
     *
     * @param transaction Consensual signed transaction to persist.
     *
     * @return list of [CordaPackageSummary] for missing CPKs (that were not linked)
     *
     * @throws CordaPersistenceException if an error happens during persist operation.
     */
    fun persist(transaction: ConsensualSignedTransaction): List<CordaPackageSummary>

    /**
     * Find a consensual signed transaction in the persistence context given it's [id].
     *
     * @param id Consensual signed transaction ID.
     *
     * @return The found consensual signed transaction, null if it could not be found in the persistence context.
     *
     * @throws CordaPersistenceException if an error happens during find operation.
     */
    fun find(id: SecureHash): ConsensualSignedTransaction?
}
