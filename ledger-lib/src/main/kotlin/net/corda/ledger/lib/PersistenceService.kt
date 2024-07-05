package net.corda.ledger.lib

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.utxo.flow.impl.persistence.TransactionExistenceStatus
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedLedgerTransaction
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionAndSignatures
import java.security.PublicKey
import java.time.Instant

// TODO Move this somewhere common
interface PersistenceService {
    @Suspendable
    fun findSignedTransaction(id: SecureHash, transactionStatus: TransactionStatus = TransactionStatus.VERIFIED): UtxoSignedTransaction?

    @Suspendable
    fun findSignedTransactionWithStatus(
        id: SecureHash,
        transactionStatus: TransactionStatus
    ): Pair<UtxoSignedTransaction?, TransactionStatus>?

    @Suspendable
    fun findSignedTransactionIdsAndStatuses(ids: Collection<SecureHash>): Map<SecureHash, TransactionStatus>

    @Suspendable
    fun findSignedLedgerTransaction(id: SecureHash): UtxoSignedLedgerTransaction?

    @Suspendable
    fun findSignedLedgerTransactionWithStatus(
        id: SecureHash,
        transactionStatus: TransactionStatus
    ): Pair<UtxoSignedLedgerTransaction?, TransactionStatus>?

    @Suspendable
    fun findFilteredTransactionsAndSignatures(
        stateRefs: List<StateRef>,
        notaryKey: PublicKey,
        notaryName: MemberX500Name
    ): Map<SecureHash, UtxoFilteredTransactionAndSignatures>

    @Suspendable
    fun persist(
        transaction: UtxoSignedTransaction,
        transactionStatus: TransactionStatus,
        visibleStatesIndexes: List<Int> = emptyList()
    ): Instant

    @Suspendable
    fun updateStatus(id: SecureHash, transactionStatus: TransactionStatus)

    @Suspendable
    fun persistIfDoesNotExist(
        transaction: UtxoSignedTransaction,
        transactionStatus: TransactionStatus,
        visibleStatesIndexes: List<Int> = emptyList()
    ): TransactionExistenceStatus

    @Suspendable
    fun persistTransactionSignatures(id: SecureHash, signatures: Set<DigitalSignatureAndMetadata>)

    @Suspendable
    fun persistFilteredTransactionsAndSignatures(
        filteredTransactionsAndSignatures: List<UtxoFilteredTransactionAndSignatures>,
        inputStateRefs: List<StateRef>,
        referenceStateRefs: List<StateRef>
    )

    @Suspendable
    fun findTransactionsWithStatusCreatedBetweenTime(
        status: TransactionStatus,
        from: Instant,
        until: Instant,
        limit: Int,
    ): List<SecureHash>

    @Suspendable
    fun incrementTransactionRepairAttemptCount(id: SecureHash)
}