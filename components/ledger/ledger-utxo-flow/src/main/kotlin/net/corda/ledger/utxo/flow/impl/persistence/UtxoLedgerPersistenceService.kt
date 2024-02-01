package net.corda.ledger.utxo.flow.impl.persistence

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedLedgerTransaction
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleProof
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
     * Find a [UtxoSignedTransaction] in the persistence context given it's [id] and return it with the status it is stored with.
     *
     * @param id transaction ID.
     * @param transactionStatus filter for this status.
     *
     * @return The found [UtxoSignedTransaction] and its status, null if it could not be found in the persistence context.
     *
     * @throws CordaPersistenceException if an error happens during find operation.
     */
    @Suspendable
    fun findSignedTransactionWithStatus(
        id: SecureHash,
        transactionStatus: TransactionStatus
    ): Pair<UtxoSignedTransaction?, TransactionStatus>?

    /**
     * Find transactions with the given [ids] that are present in the persistence context and return their IDs and statuses.
     *
     * @param ids IDs of transactions to find.
     *
     * @return A list of the transaction IDs found and their statuses.
     */
    @Suspendable
    fun findTransactionIdsAndStatuses(ids: Collection<SecureHash>): Map<SecureHash, TransactionStatus>

    /**
     * Find a verified [UtxoSignedLedgerTransaction] in the persistence context given it's [id]. This involves resolving its input and
     * reference state and fetching the transaction's signatures.
     *
     * @param id transaction ID.
     *
     * @return The found [UtxoSignedLedgerTransaction], null if it could not be found in the persistence context.
     *
     * @throws CordaPersistenceException if an error happens during find operation.
     */
    @Suspendable
    fun findSignedLedgerTransaction(id: SecureHash): UtxoSignedLedgerTransaction?

    /**
     * Find a [UtxoSignedLedgerTransaction] in the persistence context given it's [id] and return it with the status it is stored with.
     * This involves resolving its input and reference state and fetching the transaction's signatures.
     *
     * @param id transaction ID.
     * @param transactionStatus filter for this status.
     *
     * @return The found [UtxoSignedLedgerTransaction] and its status, null if it could not be found in the persistence context.
     *
     * @throws CordaPersistenceException if an error happens during find operation.
     */
    @Suspendable
    fun findSignedLedgerTransactionWithStatus(
        id: SecureHash,
        transactionStatus: TransactionStatus
    ): Pair<UtxoSignedLedgerTransaction?, TransactionStatus>?

    /**
     * Find the [MerkleProof]s that were created from the transaction with ID [transactionId].
     *
     * @param transactionId ID of the transaction the Merkle proofs were created from
     * @param groupIndex Component group index the Merkle proofs were created from
     */
    @Suspendable
    fun findMerkleProofs(
        transactionId: SecureHash,
        groupIndex: Int
    ): List<MerkleProof>

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

    /**
     * Persist a [MerkleProof] if it does not exist in the store already.
     *
     * @param transactionId ID of the transaction that the Merkle proof belongs to
     * @param groupIndex index of the component group that the Merkle proof belongs to
     * @param merkleProof the Merkle proof object to persist
     */
    @Suspendable
    fun persistMerkleProofIfDoesNotExist(
        transactionId: SecureHash,
        groupIndex: Int,
        merkleProof: MerkleProof
    )

    @Suspendable
    fun persistTransactionSignatures(id: SecureHash, startingIndex: Int, signatures: List<DigitalSignatureAndMetadata>)
}
