package net.corda.ledger.utxo.flow.impl.flows.repair

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.transaction.TransactionMissingSignaturesException
import net.corda.ledger.utxo.flow.impl.flows.finality.getVisibleStateIndexes
import net.corda.ledger.utxo.flow.impl.flows.repair.UtxoLedgerRepairFlow.RepairedTransactionResult.Invalid
import net.corda.ledger.utxo.flow.impl.flows.repair.UtxoLedgerRepairFlow.RepairedTransactionResult.NotNotarized
import net.corda.ledger.utxo.flow.impl.flows.repair.UtxoLedgerRepairFlow.RepairedTransactionResult.Notarized
import net.corda.ledger.utxo.flow.impl.flows.repair.UtxoLedgerRepairFlow.RepairedTransactionResult.Skipped
import net.corda.ledger.utxo.flow.impl.notary.PluggableNotaryService
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.utilities.debug
import net.corda.utilities.time.Clock
import net.corda.utilities.time.UTCClock
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.transaction.TransactionSignatureException
import net.corda.v5.ledger.notary.plugin.api.NotarizationType
import net.corda.v5.ledger.notary.plugin.core.NotaryExceptionFatal
import net.corda.v5.ledger.notary.plugin.core.NotaryExceptionGeneral
import net.corda.v5.ledger.notary.plugin.core.NotaryExceptionUnknown
import net.corda.v5.ledger.utxo.VisibilityChecker
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant

@Suppress("LongParameterList")
class UtxoLedgerRepairFlow(
    private val from: Instant,
    private val until: Instant,
    private val endTime: Instant,
    private val clock: Clock = UTCClock(),
    private val queryLimit: Int = QUERY_LIMIT
) : SubFlow<UtxoLedgerRepairFlow.Result> {

    private companion object {
        const val QUERY_LIMIT = 100
        val log: Logger = LoggerFactory.getLogger(UtxoLedgerRepairFlow::class.java)
    }

    @VisibleForTesting
    constructor(
        from: Instant,
        until: Instant,
        endTime: Instant,
        clock: Clock = UTCClock(),
        flowEngine: FlowEngine,
        persistenceService: UtxoLedgerPersistenceService,
        pluggableNotaryService: PluggableNotaryService,
        visibilityChecker: VisibilityChecker,
        queryLimit: Int = QUERY_LIMIT
    ) : this(from, until, endTime, clock, queryLimit) {
        this.flowEngine = flowEngine
        this.persistenceService = persistenceService
        this.pluggableNotaryService = pluggableNotaryService
        this.visibilityChecker = visibilityChecker
    }

    @CordaInject
    private lateinit var flowEngine: FlowEngine

    @CordaInject
    private lateinit var persistenceService: UtxoLedgerPersistenceService

    @CordaInject
    private lateinit var pluggableNotaryService: PluggableNotaryService

    @CordaInject
    private lateinit var visibilityChecker: VisibilityChecker

    @Suppress("NestedBlockDepth")
    @Suspendable
    override fun call(): Result {
        var lastCallToNotaryTime = clock.instant()
        var numberOfNotarizedTransactions = 0
        var numberOfNotNotarizedTransactions = 0
        var numberOfInvalidTransactions = 0
        var numberOfSkippedTransactions = 0
        var exceededDuration = false
        var exceededLastNotarizationTime = false

        var transactionsToRepair = findTransactionsToRepair()
        var firstNotNotarizedTransaction: SecureHash? = null

        try {
            pagingLoop@ while (transactionsToRepair.isNotEmpty()) {
                for (id in transactionsToRepair) {
                    // As we update the [repair_attempt_count] each time we attempt to recover a transaction, if the very first
                    // unrecoverable transaction is seen again in [transactionsToRecover] then it means that we have looped all the way
                    // round and reached an already visited transaction but with an incremented [repair_attempt_count] compared to the
                    // previous visit.
                    if (id == firstNotNotarizedTransaction) {
                        break@pagingLoop
                    }

                    val result = potentiallyRepairTransaction(id, lastCallToNotaryTime)

                    if (result != Skipped) {
                        lastCallToNotaryTime = clock.instant()
                    }

                    when (result) {
                        Notarized -> numberOfNotarizedTransactions++
                        NotNotarized -> {
                            numberOfNotNotarizedTransactions++
                            if (firstNotNotarizedTransaction == null) {
                                firstNotNotarizedTransaction = id
                            }
                            // We do not need to worry about concurrent calls for the same transaction from a separate recovery flow run,
                            // because the transaction has technically had an attempted recovery in both flows.
                            checkDeadlinesNotExceeded(lastCallToNotaryTime)
                            persistenceService.incrementTransactionRepairAttemptCount(id)
                        }
                        Invalid -> numberOfInvalidTransactions++
                        Skipped -> numberOfSkippedTransactions++
                    }
                }

                transactionsToRepair = if (transactionsToRepair.size >= queryLimit) {
                    checkDeadlinesNotExceeded(lastCallToNotaryTime)
                    findTransactionsToRepair()
                } else {
                    emptyList()
                }
            }
        } catch (e: ExceededDurationException) {
            exceededDuration = true
        } catch (e: ExceededLastNotarizationTimeException) {
            exceededLastNotarizationTime = true
        }
        return Result(
            exceededDuration,
            exceededLastNotarizationTime,
            numberOfNotarizedTransactions,
            numberOfNotNotarizedTransactions,
            numberOfInvalidTransactions,
            numberOfSkippedTransactions
        )
    }

    @Suspendable
    fun findTransactionsToRepair(): List<SecureHash> {
        return persistenceService.findTransactionsWithStatusCreatedBetweenTime(
            TransactionStatus.UNVERIFIED,
            from,
            until,
            queryLimit
        )
    }

    @Suspendable
    private fun potentiallyRepairTransaction(id: SecureHash, lastCallToNotaryTime: Instant): RepairedTransactionResult {
        checkDeadlinesNotExceeded(lastCallToNotaryTime)
        val transaction = persistenceService.findSignedTransaction(id, TransactionStatus.UNVERIFIED)

        if (transaction == null) {
            log.warn("Transaction $id is no longer unverified, skipping from ledger repair")
            return Skipped
        }
        transaction as UtxoSignedTransactionInternal

        try {
            transaction.verifySignatorySignatures()
        } catch (e: TransactionMissingSignaturesException) {
            log.info("Transaction $id is missing non-notary signatures, skipping from ledger repair")
            return Skipped
        }

        try {
            // We wouldn't have stored a transaction with an invalid signature and kept it as unverified.
            // So we can use this API and disregard the invalid signatures possibility.
            transaction.verifyAttachedNotarySignature()
            log.warn(
                "Transaction $id is signed by the notary but stored as unverified, skipping from ledger repair as this is in " +
                    "an invalid state"
            )
            return Skipped
        } catch (_: TransactionSignatureException) {
            // Empty as we continue recovering this transaction.
        }

        checkDeadlinesNotExceeded(lastCallToNotaryTime)
        return notarize(transaction)
    }

    @Suppress("ThrowsCount")
    @Suspendable
    private fun notarize(
        transaction: UtxoSignedTransactionInternal
    ): RepairedTransactionResult {
        val notary = transaction.notaryName
        val notarizationFlow = pluggableNotaryService.create(
            transaction,
            pluggableNotaryService.get(transaction.notaryName),
            NotarizationType.CHECK
        )

        log.info(
            "Repairing transaction ${transaction.id}. Sending it for notarisation using using pluggable notary client flow of " +
                "${notarizationFlow::class.java.name} with notary $notary"
        )

        val notarySignatures = try {
            flowEngine.subFlow(notarizationFlow)
        } catch (e: Exception) {
            when (e) {
                is NotaryExceptionGeneral -> {
                    log.warn("Notarization check of transaction ${transaction.id} failed with ${e.message}")
                    return NotNotarized
                }
                is NotaryExceptionUnknown -> {
                    log.info(
                        "Transaction ${transaction.id} has not been previously notarized by notary $notary, skipping from ledger repair"
                    )
                    return NotNotarized
                }
                is NotaryExceptionFatal -> {
                    log.warn("Notarization check of transaction ${transaction.id} failed permanently with ${e.message}")
                    persistInvalidTransaction(transaction)
                    return Invalid
                }
                else -> {
                    log.warn("Notarization check of transaction ${transaction.id} failed with ${e.message}")
                    return NotNotarized
                }
            }
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
            return Invalid
        }

        var notarizedTransaction = transaction
        notarySignatures.forEach { signature ->
            try {
                notarizedTransaction = verifyAndAddNotarySignature(notarizedTransaction, signature)
            } catch (e: Exception) {
                log.warn(e.message ?: "Notary signature verification of transaction ${transaction.id} failed.")
                return Invalid
            }
        }

        if (log.isDebugEnabled) {
            log.debug(
                "Successfully notarized transaction ${transaction.id} using notary $notary and received ${notarySignatures.size} " +
                    "signature(s)"
            )
        }

        persistNotarizedTransaction(transaction)

        return Notarized
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
        log.info("Repaired transaction ${transaction.id}. The transaction has been stored in the vault as notarized.")
    }

    @Suspendable
    private fun persistInvalidTransaction(transaction: UtxoSignedTransaction) {
        persistenceService.persist(transaction, TransactionStatus.INVALID)
        log.info("Recorded transaction as invalid: ${transaction.id}")
    }

    private fun checkDeadlinesNotExceeded(lastCallToNotaryTime: Instant) {
        val now = clock.instant()
        if (now.isAfter(endTime)) {
            throw ExceededDurationException()
        }
        if (now.isAfter(lastCallToNotaryTime.plus(MAX_DURATION_WITHOUT_SUSPENDING))) {
            throw ExceededLastNotarizationTimeException()
        }
    }

    data class Result(
        val exceededDuration: Boolean,
        val exceededLastNotarizationTime: Boolean,
        val numberOfNotarizedTransactions: Int,
        val numberOfNotNotarizedTransactions: Int,
        val numberOfInvalidTransactions: Int,
        val numberOfSkippedTransactions: Int
    )

    private enum class RepairedTransactionResult {
        Notarized, NotNotarized, Invalid, Skipped
    }

    private class ExceededDurationException : IllegalStateException()
    private class ExceededLastNotarizationTimeException : IllegalStateException()
}
