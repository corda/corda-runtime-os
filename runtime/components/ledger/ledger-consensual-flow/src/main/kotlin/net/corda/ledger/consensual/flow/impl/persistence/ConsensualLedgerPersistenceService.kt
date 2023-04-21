package net.corda.ledger.consensual.flow.impl.persistence

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction

/**
 * [ConsensualLedgerPersistenceService] allows to insert and find consensual signed transactions in the persistent store provided
 * by the platform.
 */
interface ConsensualLedgerPersistenceService {
    /**
     * Persist a [ConsensualSignedTransaction] to the store.
     *
     * @param transaction Consensual signed transaction to persist.
     * @param transactionStatus Transaction's status
     *
     * @return list of [CordaPackageSummary] for missing CPKs (that were not linked)
     *
     * @throws CordaPersistenceException if an error happens during persist operation.
     */
    @Suspendable
    fun persist(transaction: ConsensualSignedTransaction, transactionStatus: TransactionStatus): List<CordaPackageSummary>

    /**
     * Find a consensual signed transaction in the persistence context given it's [id].
     *
     * @param id Consensual signed transaction ID.
     *
     * @return The found consensual signed transaction, null if it could not be found in the persistence context.
     *
     * @throws CordaPersistenceException if an error happens during find operation.
     */
    @Suspendable
    fun find(id: SecureHash): ConsensualSignedTransaction?
}
