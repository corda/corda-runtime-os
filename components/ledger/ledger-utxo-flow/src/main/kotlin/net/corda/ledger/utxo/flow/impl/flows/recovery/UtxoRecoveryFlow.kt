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

        val endTime = Instant.now().plus(duration)
        var lastNotarization = Instant.now()
        var numberOfRecoveredFlows = 0
        var exceededDuration = false
        var exceededLastNotarizationTime = false

        val ids = persistenceService.findTransactionsWithStatusCreatedBeforeTime(TransactionStatus.UNVERIFIED, from, until)
        for (id in ids) {

            val now = Instant.now()
            if (now.isAfter(endTime)) {
                exceededDuration = true
                break
            }
            if (now.isAfter(lastNotarization.plus(MAX_DURATION_WITHOUT_SUSPENDING))) {
                exceededLastNotarizationTime = true
                break
            }

            val transaction = persistenceService.findSignedTransaction(id, TransactionStatus.UNVERIFIED)

            if (transaction == null) {
                log.warn("Transaction $id is no longer unverified, skipping from recovery flow")
                continue
            }
            transaction as UtxoSignedTransactionInternal

            try {
                transaction.verifySignatorySignatures()
            } catch (e: TransactionMissingSignaturesException) {
                log.info("Transaction $id is missing non-notary signatures, skipping from recovery flow")
                continue
            }

            try {
                // We wouldn't have stored a transaction with an invalid signature and kept it as unverified.
                // So we can use this API and disregard the invalid signatures possibility.
                transaction.verifyAttachedNotarySignature()
                continue
            } catch (_: TransactionSignatureException) {
                // Empty as we continue recovering this transaction.
            }

            notarize(transaction)?.let { notarizedTransaction ->
                persistNotarizedTransaction(notarizedTransaction)
                numberOfRecoveredFlows++
                lastNotarization = Instant.now()
            }
        }
        if (exceededDuration) {
            log.info(
                "Completed recovery flow of $numberOfRecoveredFlows missing notarized transactions that occurred between $from to " +
                    "$until but exceeded recovery duration of $duration. There may be more transactions to recover."
            )
        } else if(exceededLastNotarizationTime) {
            log.info(
                "Completed recovery flow of $numberOfRecoveredFlows missing notarized transactions that occurred between $from to " +
                    "$until but exceeded the duration of $MAX_DURATION_WITHOUT_SUSPENDING without notarizing a transaction. There may " +
                    "be more transactions to recover."
            )
        } else {
            log.info(
                "Completed recovery flow of $numberOfRecoveredFlows missing notarized transactions that occurred between $from to $until"
            )
        }
        return numberOfRecoveredFlows
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
            }
            if (e is NotaryExceptionFatal) {
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
}
