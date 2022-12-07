package net.corda.ledger.utxo.flow.impl.persistence

import net.corda.data.ledger.persistence.ComponentPosition
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

/**
 * [UtxoLedgerPersistenceService] allows to insert and find UTXO signed transactions in the persistent store provided
 * by the platform.
 */
interface UtxoLedgerPersistenceService {
    /**
     * Persist a [UtxoSignedTransaction] to the store.
     *
     * @param transaction Consensual signed transaction to persist.
     * @param transaction UTXO signed transaction to persist.
     * @param transactionStatus Transaction's status
     * @param relevantStates Positions or transaction components related to relevant states.
     *
     * @return list of [CordaPackageSummary] for missing CPKs (that were not linked)
     *
     * @throws CordaPersistenceException if an error happens during persist operation.
     */
    @Suspendable
    fun persist(
        transaction: UtxoSignedTransaction,
        transactionStatus: TransactionStatus,
        relevantStates: List<ComponentPosition> = emptyList()
    ): List<CordaPackageSummary>

    @Suspendable
    fun updateStatus(id: SecureHash, transactionStatus: TransactionStatus)

    /**
     * Persist a [UtxoSignedTransaction] to the store.
     *
     * @param transaction UTXO signed transaction to persist.
     * @param transactionStatus Transaction's status
     *
     * @return list of [CordaPackageSummary] for missing CPKs (that were not linked)
     *
     * @throws CordaPersistenceException if an error happens during persist operation.
     */
    @Suspendable
    fun persistIfDoesNotExist(
        transaction: UtxoSignedTransaction,
        transactionStatus: TransactionStatus
    ): Pair<TransactionExistenceStatus, List<CordaPackageSummary>>

    /**
     * Find a UTXO signed transaction in the persistence context given it's [id].
     *
     * @param id UTXO signed transaction ID.
     *
     * @return The found UTXO signed transaction, null if it could not be found in the persistence context.
     *
     * @throws CordaPersistenceException if an error happens during find operation.
     */
    @Suspendable
    fun find(id: SecureHash, transactionStatus: TransactionStatus = TransactionStatus.VERIFIED): UtxoSignedTransaction?
}