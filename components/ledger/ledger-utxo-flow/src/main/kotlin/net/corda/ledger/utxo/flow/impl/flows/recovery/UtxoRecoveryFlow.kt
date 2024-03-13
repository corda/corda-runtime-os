package net.corda.ledger.utxo.flow.impl.flows.recovery

import net.corda.flow.state.asFlowContext
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.transaction.TransactionMissingSignaturesException
import net.corda.ledger.notary.worker.selection.NotaryVirtualNodeSelectorService
import net.corda.ledger.utxo.flow.impl.flows.finality.getVisibleStateIndexes
import net.corda.ledger.utxo.flow.impl.notary.PluggableNotarySelector
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.utilities.debug
import net.corda.utilities.minutes
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionSignatureException
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow
import net.corda.v5.ledger.notary.plugin.core.NotaryExceptionFatal
import net.corda.v5.ledger.notary.plugin.core.NotaryExceptionUnknown
import net.corda.v5.ledger.utxo.VisibilityChecker
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.PrivilegedExceptionAction
import java.time.Duration
import java.time.Instant

class UtxoRecoveryFlow(private val from: Instant, private val until: Instant, private val duration: Duration) : SubFlow<Int> {

    private companion object {
        const val QUERY_LIMIT = 100
        val MAX_DURATION_WITHOUT_SUSPENDING = 2.minutes
        val log: Logger = LoggerFactory.getLogger(UtxoRecoveryFlow::class.java)
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var virtualNodeSelectorService: NotaryVirtualNodeSelectorService

    @CordaInject
    lateinit var pluggableNotarySelector: PluggableNotarySelector

    @CordaInject
    lateinit var persistenceService: UtxoLedgerPersistenceService

    @CordaInject
    lateinit var visibilityChecker: VisibilityChecker

    @Suspendable
    override fun call(): Int {
        log.info("Starting recovery flow of missing notarized transactions. Recovering transactions that occurred between $from to $until")

        flowEngine.flowContextProperties.asFlowContext.platformProperties["corda.notary.check"] = "true"

        val startTime = System.currentTimeMillis()
        val endTime = Instant.ofEpochMilli(startTime).plus(duration)
        var lastRecovery = Instant.now()
        var numberOfRecoveredFlows = 0
        var exceededDuration = false
        var exceededLastNotarizationTime = false

        var transactionsToRecover = findTransactionsToRecover()
        var firstUnrecoverableTransaction: SecureHash? = null

        pagingLoop@ while (transactionsToRecover.isNotEmpty()) {
            for (id in transactionsToRecover) {
                val now = Instant.now()
                if (now.isAfter(endTime)) {
                    exceededDuration = true
                    break@pagingLoop
                }
                if (now.isAfter(lastRecovery.plus(MAX_DURATION_WITHOUT_SUSPENDING))) {
                    exceededLastNotarizationTime = true
                    break@pagingLoop
                }
                // As we update the [recovery_attempt_count] each time we attempt to recover a transaction, if the very first unrecoverable
                // transaction is seen again in [transactionsToRecover] then it means that we have looped all the way round and reached
                // an already visited transaction but with an incremented [recovery_attempt_count] compared to the previous visit.
                if (id == firstUnrecoverableTransaction) {
                    break@pagingLoop
                }

                val (isRecovered, isSkipped) = potentiallyRecoverTransaction(id)
                if (isRecovered) {
                    numberOfRecoveredFlows++
                    lastRecovery = Instant.now()
                } else if (!isSkipped) {
                    if (firstUnrecoverableTransaction == null) {
                        firstUnrecoverableTransaction = id
                    }
                    // We do not need to worry about concurrent calls for the same transaction from a separate recovery flow run, because
                    // the transaction has technically had an attempted recovery in both flows.
                    persistenceService.incrementRecoveryAttemptCount(id)
                }
            }
            if (transactionsToRecover.size >= QUERY_LIMIT) {
                transactionsToRecover = findTransactionsToRecover()
            }
        }
        if (exceededDuration) {
            log.info(
                "Completed recovery flow of $numberOfRecoveredFlows missing notarized transactions that occurred between $from to " +
                    "$until but exceeded recovery duration of $duration. There may be more transactions to recover. Time taken " +
                    "${Duration.ofMillis(System.currentTimeMillis() - startTime)}."
            )
        } else if (exceededLastNotarizationTime) {
            log.info(
                "Completed recovery flow of $numberOfRecoveredFlows missing notarized transactions that occurred between $from to " +
                    "$until but exceeded the duration of $MAX_DURATION_WITHOUT_SUSPENDING without notarizing a transaction. There may " +
                    "be more transactions to recover. Time taken ${Duration.ofMillis(System.currentTimeMillis() - startTime)}."
            )
        } else {
            log.info(
                "Completed recovery flow of $numberOfRecoveredFlows missing notarized transactions that occurred between $from to " +
                    "$until. Time taken ${Duration.ofMillis(System.currentTimeMillis() - startTime)}."
            )
        }
        return numberOfRecoveredFlows
    }

    @Suspendable
    fun findTransactionsToRecover(): List<SecureHash> {
        return persistenceService.findTransactionsWithStatusCreatedBeforeTime(
            TransactionStatus.UNVERIFIED,
            from,
            until,
            QUERY_LIMIT
        )
    }

    @Suspendable
    private fun potentiallyRecoverTransaction(id: SecureHash): RecoveredTransactionResult {
        val transaction = persistenceService.findSignedTransaction(id, TransactionStatus.UNVERIFIED)

        if (transaction == null) {
            log.warn("Transaction $id is no longer unverified, skipping from recovery flow")
            return RecoveredTransactionResult(isRecovered = false, isSkipped = true)
        }
        transaction as UtxoSignedTransactionInternal

        try {
            transaction.verifySignatorySignatures()
        } catch (e: TransactionMissingSignaturesException) {
            log.info("Transaction $id is missing non-notary signatures, skipping from recovery flow")
            return RecoveredTransactionResult(isRecovered = false, isSkipped = true)
        }

        try {
            // We wouldn't have stored a transaction with an invalid signature and kept it as unverified.
            // So we can use this API and disregard the invalid signatures possibility.
            transaction.verifyAttachedNotarySignature()
            return RecoveredTransactionResult(isRecovered = false, isSkipped = true)
        } catch (_: TransactionSignatureException) {
            // Empty as we continue recovering this transaction.
        }

        val isRecovered =  notarize(transaction)?.let { notarizedTransaction ->
            persistNotarizedTransaction(notarizedTransaction)
            true
        } ?: false

        return RecoveredTransactionResult(isRecovered, isSkipped = false)
    }

    // Not sure whether to return nulls or throw exceptions
    @Suppress("ThrowsCount")
    @Suspendable
    private fun notarize(
        transaction: UtxoSignedTransactionInternal
    ): UtxoSignedTransactionInternal? {
        val notary = transaction.notaryName
        val notarizationFlow = newPluggableNotaryClientFlowInstance(transaction)

        log.info(
            "Recovering transaction ${transaction.id}. Sending it for notarisation using using pluggable notary client flow of " +
                "${notarizationFlow::class.java.name} with notary $notary"
        )

        val notarySignatures = try {
            flowEngine.subFlow(notarizationFlow)
        } catch (e: CordaRuntimeException) {
            if (e is NotaryExceptionUnknown && e.message?.contains("UniquenessCheckErrorNotPreviouslyNotarizedException") == true) {
                log.info("Transaction ${transaction.id} has not been previously notarized by notary $notary, skipping from recovery flow")
                return null
            } else if (e is NotaryExceptionFatal) {
                persistInvalidTransaction(transaction)
                log.warn("Notarization of transaction ${transaction.id} failed permanently with ${e.message}.")
            } else {
                log.warn("Notarization of transaction ${transaction.id} failed with ${e.message}.")
            }
            return null
        }

        if (log.isTraceEnabled) {
            log.trace(
                "Received ${notarySignatures.size} signature(s) from notary $notary after requesting notarization of transaction " +
                    transaction.id
            )
        }

        if (notarySignatures.isEmpty()) {
            val message = "Notary $notary did not return any signatures after requesting notarization of transaction ${transaction.id}"
            log.warn(message)
            persistInvalidTransaction(transaction)
            return null
        }

        var notarizedTransaction = transaction
        notarySignatures.forEach { signature ->
            try {
                notarizedTransaction = verifyAndAddNotarySignature(notarizedTransaction, signature)
            } catch (e: Exception) {
                log.warn(e.message ?: "Notary signature verification of transaction ${transaction.id} failed.")
                return null
            }
        }

        if (log.isDebugEnabled) {
            log.debug(
                "Successfully notarized transaction ${transaction.id} using notary $notary and received ${notarySignatures.size} " +
                    "signature(s)"
            )
        }

        return notarizedTransaction
    }

    // Gets a new notary client plugin flow instance. This is done in a non-suspendable
    // function to avoid trying (and failing) to serialize the objects used internally.
    private fun newPluggableNotaryClientFlowInstance(
        transaction: UtxoSignedTransactionInternal
    ): PluggableNotaryClientFlow {
        val pluggableNotaryDetails = pluggableNotarySelector.get(transaction.notaryName)
        @Suppress("deprecation", "removal")
        return java.security.AccessController.doPrivileged(
            PrivilegedExceptionAction {
                pluggableNotaryDetails.flowClass.getConstructor(
                    UtxoSignedTransaction::class.java,
                    MemberX500Name::class.java
                ).newInstance(
                    transaction,
                    virtualNodeSelectorService.selectVirtualNode(transaction.notaryName)
                )
            }
        )
    }

    @Suspendable
    private fun verifyAndAddNotarySignature(
        transaction: UtxoSignedTransactionInternal,
        signature: DigitalSignatureAndMetadata
    ): UtxoSignedTransactionInternal {
        try {
            transaction.verifyNotarySignature(signature)
            log.debug {
                "Successfully verified signature($signature) by notary ${transaction.notaryName} for transaction ${transaction.id}"
            }
        } catch (e: Exception) {
            val message = "Failed to verify transaction's signature($signature) by notary ${transaction.notaryName} for " +
                "transaction ${transaction.id}. Message: ${e.message}"
            log.warn(message)
            persistInvalidTransaction(transaction)
            throw e
        }
        return transaction.addSignature(signature)
    }

    @Suspendable
    private fun persistNotarizedTransaction(transaction: UtxoSignedTransactionInternal) {
        val visibleStatesIndexes = transaction.getVisibleStateIndexes(visibilityChecker)
        persistenceService.persist(transaction, TransactionStatus.VERIFIED, visibleStatesIndexes)
        log.info("Recovered transaction ${transaction.id}. The transaction has been stored in the vault as notarized.")
    }

    @Suspendable
    private fun persistInvalidTransaction(transaction: UtxoSignedTransaction) {
        persistenceService.persist(transaction, TransactionStatus.INVALID)
        log.info("Recorded transaction as invalid: ${transaction.id}")
    }

    private data class RecoveredTransactionResult(val isRecovered: Boolean, val isSkipped: Boolean)
}
