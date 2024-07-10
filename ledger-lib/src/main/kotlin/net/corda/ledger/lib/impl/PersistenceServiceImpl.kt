package net.corda.ledger.lib.impl

import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.TransactionStatus.Companion.toTransactionStatus
import net.corda.ledger.lib.PersistenceService
import net.corda.ledger.lib.impl.stub.serialization.StubSerializationService
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.ledger.utxo.data.transaction.SignedLedgerTransactionContainer
import net.corda.ledger.utxo.data.transaction.UtxoFilteredTransactionAndSignaturesImpl
import net.corda.ledger.utxo.flow.impl.persistence.TransactionExistenceStatus
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedLedgerTransaction
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedLedgerTransactionImpl
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoLedgerTransactionFactory
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoSignedTransactionFactory
import net.corda.ledger.utxo.flow.impl.transaction.filtered.UtxoFilteredTransactionImpl
import net.corda.orm.utils.transaction
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionAndSignatures
import java.security.PublicKey
import java.time.Instant
import javax.persistence.EntityManagerFactory


/**
 * Instantiate this on sandbox creation and inject the serialization service from there
 *
 * UtxoSignedTransactionFactoryImpl
 * UtxoLedgerTransactionFactoryImpl -- we need to instantiate these somehow, either stub dependencies or create real objects
 */
class PersistenceServiceImpl(
    // TODO WIRE
    private val utxoPersistenceService: UtxoPersistenceService,
    // FIXME is it ok to depend on this?
    private val utxoSignedTransactionFactory: UtxoSignedTransactionFactory,
    // FIXME is it ok to depend on this?
    private val utxoLedgerTransactionFactory: UtxoLedgerTransactionFactory,
    private val entityManagerFactory: EntityManagerFactory
) : PersistenceService {

    // This is OK, no extra dependency
    override fun findSignedTransaction(id: SecureHash, transactionStatus: TransactionStatus): UtxoSignedTransaction? {
        val (transaction, _) = utxoPersistenceService.findSignedTransaction(id.toString(), transactionStatus)
        return transaction?.toSignedTransaction()
    }

    // This is OK, no extra dependency
    override fun findSignedTransactionWithStatus(
        id: SecureHash,
        transactionStatus: TransactionStatus
    ): Pair<UtxoSignedTransaction?, TransactionStatus>? {
        val (transaction, status) = utxoPersistenceService.findSignedTransaction(id.toString(), transactionStatus)
        return status?.let {
            transaction?.toSignedTransaction() to it.toTransactionStatus()
        }
    }

    // This is OK, no extra dependency
    override fun findSignedTransactionIdsAndStatuses(ids: Collection<SecureHash>): Map<SecureHash, TransactionStatus> {
        return utxoPersistenceService.findSignedTransactionIdsAndStatuses(ids.map { it.toString() }).mapValues {
            it.value.toTransactionStatus()
        }
    }

    // This is OK, no extra dependency
    override fun findSignedLedgerTransactionWithStatus(
        id: SecureHash,
        transactionStatus: TransactionStatus
    ): Pair<UtxoSignedLedgerTransaction?, TransactionStatus>? {
        val (transaction, status) = utxoPersistenceService.findSignedLedgerTransaction(id.toString(), transactionStatus)
        return status?.let {
            transaction?.toSignedLedgerTransaction() to it.toTransactionStatus()
        }
    }

    // This is OK, no extra dependency
    override fun findSignedLedgerTransaction(id: SecureHash): UtxoSignedLedgerTransaction? {
        return findSignedLedgerTransactionWithStatus(id, TransactionStatus.VERIFIED)?.first
    }

    override fun findFilteredTransactionsAndSignatures(
        stateRefs: List<StateRef>,
        notaryKey: PublicKey,
        notaryName: MemberX500Name
    ): Map<SecureHash, UtxoFilteredTransactionAndSignatures> {
        return utxoPersistenceService.findFilteredTransactionsAndSignatures(stateRefs).mapValues {
            UtxoFilteredTransactionAndSignaturesImpl(
                it.value.first as UtxoFilteredTransaction, // TODO check if this cast is OK
                it.value.second.toSet()
            )
        }
    }

    // This is OK, no extra dependency
    override fun updateStatus(id: SecureHash, transactionStatus: TransactionStatus) {
        utxoPersistenceService.updateStatus(id.toString(), transactionStatus)
    }

    // This is OK, no extra dependency
    override fun persistTransactionSignatures(id: SecureHash, signatures: Set<DigitalSignatureAndMetadata>) {
        utxoPersistenceService.persistTransactionSignatures(id.toString(), signatures.map { it.signature.bytes })
    }

    // TODO Check if having UtxoFilteredTransactionImpl as a dependency is acceptable (probably not?)
    override fun persistFilteredTransactionsAndSignatures(
        filteredTransactionsAndSignatures: List<UtxoFilteredTransactionAndSignatures>,
        inputStateRefs: List<StateRef>,
        referenceStateRefs: List<StateRef>
    ) {
        utxoPersistenceService.persistFilteredTransactions(
            filteredTransactionsAndSignatures.associate {
                (it.filteredTransaction as UtxoFilteredTransactionImpl).filteredTransaction to it.signatures
            },
            inputStateRefs,
            referenceStateRefs,
            "account=TODO" // TODO how to populate?
        )
    }

    // This is OK, no extra dependency
    override fun findTransactionsWithStatusCreatedBetweenTime(
        status: TransactionStatus,
        from: Instant,
        until: Instant,
        limit: Int
    ): List<SecureHash> {
        return utxoPersistenceService.findTransactionsWithStatusCreatedBetweenTime(status, from, until, limit)
    }

    // This is OK, no extra dependency
    override fun incrementTransactionRepairAttemptCount(id: SecureHash) {
        utxoPersistenceService.incrementTransactionRepairAttemptCount(id.toString())
    }

    // TODO Check if having UtxoSignedTransactionInternal as a dependency is acceptable (probably not?)
    //  Also check how to populate UTXO token map
    //  Also check how to pass in a proper serialization service
    override fun persist(
        transaction: UtxoSignedTransaction,
        transactionStatus: TransactionStatus,
        visibleStatesIndexes: List<Int>
    ): Instant {
        return entityManagerFactory.transaction { em ->
            utxoPersistenceService.persistTx(
                transaction,
                (transaction as UtxoSignedTransactionInternal).wireTransaction,
                transactionStatus,
                visibleStatesIndexes,
                emptyMap(), // TODO how to populate?
                StubSerializationService() // TODO how to pass in a proper serialization service?
            ) { block -> block(em) }
        }
    }

    // TODO Check if having UtxoSignedTransactionInternal as a dependency is acceptable (probably not?)
    //  Also check how to populate UTXO token map
    //  Also check how to pass in a proper serialization service
    override fun persistIfDoesNotExist(
        transaction: UtxoSignedTransaction,
        transactionStatus: TransactionStatus,
        visibleStatesIndexes: List<Int>
    ): TransactionExistenceStatus {
        val status = utxoPersistenceService.persistTxIfDoesNotExist(
            transaction,
            (transaction as UtxoSignedTransactionInternal).wireTransaction,
            transactionStatus,
            visibleStatesIndexes,
            emptyMap(), // TODO how to populate?
            StubSerializationService() // TODO how to pass in a proper serialization service?
        )

        return status.let {
            when (status) {
                "" -> TransactionExistenceStatus.DOES_NOT_EXIST
                "U" -> TransactionExistenceStatus.UNVERIFIED
                "V" -> TransactionExistenceStatus.VERIFIED
                else -> throw IllegalStateException("Invalid status $status")
            }
        }
    }

    private fun UtxoSignedTransaction.toContainer(): SignedTransactionContainer {
        return (this as UtxoSignedTransactionInternal).run {
            SignedTransactionContainer(wireTransaction, signatures)
        }
    }

    private fun SignedLedgerTransactionContainer.toSignedLedgerTransaction(): UtxoSignedLedgerTransaction {
        return UtxoSignedLedgerTransactionImpl(
            utxoLedgerTransactionFactory.create(
                wireTransaction,
                serializedInputStateAndRefs,
                serializedReferenceStateAndRefs
            ),
            utxoSignedTransactionFactory.create(wireTransaction, signatures)
        )
    }

    private fun SignedTransactionContainer.toSignedTransaction(): UtxoSignedTransaction {
        return utxoSignedTransactionFactory.create(wireTransaction, signatures)
    }
}
