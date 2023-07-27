package net.corda.ledger.utxo.flow.impl.persistence

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedLedgerTransaction
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
    fun findSignedTransaction(id: SecureHash, transactionStatus: TransactionStatus = TransactionStatus.VERIFIED): UtxoSignedTransaction?

    /**
     * Find a verified UTXO signed transaction in the persistence context given it's [id], resolve its state refs and convert it to a ledger
     * transaction.
     *
     * @param id UTXO signed transaction ID.
     *
     * @return The found UTXO ledger transaction, null if it could not be found in the persistence context.
     *
     * @throws CordaPersistenceException if an error happens during find operation.
     */
    @Suspendable
    fun findSignedLedgerTransaction(id: SecureHash): UtxoSignedLedgerTransaction?

    /**
     * Find a UTXO signed transaction in the persistence context given it's [id], resolve its state refs and convert it to a ledger
     * transaction.
     *
     * @param id UTXO signed transaction ID.
     * @param transactionStatus filter for this status.
     *
     * @return The found UTXO ledger transaction, null if it could not be found in the persistence context.
     *
     * @throws CordaPersistenceException if an error happens during find operation.
     */
    @Suspendable
    fun findSignedLedgerTransactionWithStatus(id: SecureHash, transactionStatus: TransactionStatus): Pair<UtxoSignedLedgerTransaction?, TransactionStatus>?

    /**
     * Persist a [UtxoSignedTransaction] to the store.
     *
     * @param transaction UTXO signed transaction to persist.
     * @param transactionStatus Transaction's status
     * @param visibleStatesIndexes Indexes of visible states.
     *
     * @return list of [CordaPackageSummary] for missing CPKs (that were not linked)
     *
     * @throws CordaPersistenceException if an error happens during persist operation.
     */
    @Suspendable
    fun persist(
        transaction: UtxoSignedTransaction,
        transactionStatus: TransactionStatus,
        visibleStatesIndexes: List<Int> = emptyList()
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
}