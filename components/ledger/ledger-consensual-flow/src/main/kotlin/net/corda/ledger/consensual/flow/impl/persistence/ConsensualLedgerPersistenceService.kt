package net.corda.ledger.consensual.flow.impl.persistence

import net.corda.ledger.common.data.transaction.CordaPackageSummary
import net.corda.ledger.consensual.data.transaction.ConsensualSignedTransactionContainer
import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.crypto.SecureHash

/**
 * [ConsensualLedgerPersistenceService] allows to insert and find consensual signed transactions in the persistent store provided
 * by the platform.
 */
interface ConsensualLedgerPersistenceService {
    /**
     * Persist a [ConsensualSignedTransactionContainer] to the store.
     *
     * @param transaction Consensual signed transaction to persist.
     * @param transactionStatus Transaction's status
     *
     * @return list of [CordaPackageSummary] for missing CPKs (that were not linked)
     *
     * @throws CordaPersistenceException if an error happens during persist operation.
     */
    fun persist(transaction: ConsensualSignedTransactionContainer, transactionStatus: String): List<CordaPackageSummary>

    /**
     * Find a consensual signed transaction in the persistence context given it's [id].
     *
     * @param id Consensual signed transaction ID.
     *
     * @return The found consensual signed transaction, null if it could not be found in the persistence context.
     *
     * @throws CordaPersistenceException if an error happens during find operation.
     */
    fun find(id: SecureHash): ConsensualSignedTransactionContainer?
}
