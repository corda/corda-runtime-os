package net.corda.ledger.utxo.flow.impl.flows.finality.v1

import net.corda.crypto.core.fullId
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.common.flow.transaction.TransactionMissingSignaturesException
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainSenderFlow
import net.corda.ledger.utxo.flow.impl.flows.backchain.dependencies
import net.corda.ledger.utxo.flow.impl.flows.finality.FinalityPayload
import net.corda.ledger.utxo.flow.impl.flows.finality.addTransactionIdToFlowContext
import net.corda.ledger.utxo.flow.impl.flows.finality.getVisibleStateIndexes
import net.corda.ledger.utxo.flow.impl.notary.PluggableNotaryDetails
import net.corda.ledger.utxo.flow.impl.notary.PluggableNotaryService
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionWithDependencies
import net.corda.sandbox.CordaSystemFlow
import net.corda.utilities.debug
import net.corda.utilities.info
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.notary.plugin.api.NotarizationType
import net.corda.v5.ledger.notary.plugin.core.NotaryExceptionFatal
import net.corda.v5.ledger.notary.plugin.core.NotaryExceptionGeneral
import net.corda.v5.ledger.notary.plugin.core.NotaryExceptionUnknown
import net.corda.v5.ledger.utxo.NotarySignatureVerificationService
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionAndSignatures
import net.corda.v5.membership.NotaryInfo
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * V1 changed slightly between 5.0 and 5.1.
 * (5.1's initial payload contains the number of parties to let bypass steps later not needed for two parties cases)
 * This change is not managed through flow versioning since flow interoperability is not supported between these versions.
 */

@CordaSystemFlow
class UtxoFinalityFlowV1(
    private val initialTransaction: UtxoSignedTransactionInternal,
    private val sessions: List<FlowSession>,
    private val pluggableNotaryDetails: PluggableNotaryDetails
) : UtxoFinalityBaseV1() {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(UtxoFinalityFlowV1::class.java)
    }

    override val log: Logger = UtxoFinalityFlowV1.log

    private val transactionId = initialTransaction.id

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var pluggableNotaryService: PluggableNotaryService

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    lateinit var notarySignatureVerificationService: NotarySignatureVerificationService

    @CordaInject
    lateinit var utxoLedgerPersistenceService: UtxoLedgerPersistenceService

    @Suspendable
    override fun call(): UtxoSignedTransaction {
        /*
         * if the number of sessions(counterparties) is more than one,
         * it should wait for additional signatures.
         * Otherwise, it can be skipped since there isn't unseen signatures
         */
        val transferAdditionalSignatures = sessions.size > 1

        addTransactionIdToFlowContext(flowEngine, transactionId)
        log.info { "Starting finality flow for transaction: $transactionId" }
        verifyExistingSignatures(initialTransaction)
        verifyTransaction(initialTransaction)

        // Initial verifications passed, the transaction can be saved in the database.
        persistUnverifiedTransaction()

        // Fetch the notary info and the transaction dependencies as filtered transactions
        val notaryInfo = getNotaryInfo()
        val filteredTransactionsAndSignatures = findFilteredTransactionsAndSignatures(notaryInfo)

        // Send the filtered dependencies to counterparties
        sendTransactionAndDependenciesToCounterparties(
            filteredTransactionsAndSignatures,
            notaryInfo,
            transferAdditionalSignatures
        )

        val (transaction, signaturesReceivedFromSessions, newSignatures) = receiveSignaturesAndAddToTransaction()
        verifyAllReceivedSignatures(transaction, signaturesReceivedFromSessions)
        persistCounterpartySignatures(transaction.id, newSignatures)

        if (transferAdditionalSignatures) {
            sendUnseenSignaturesToCounterparties(transaction, signaturesReceivedFromSessions)
        }

        val (notarizedTransaction, notarySignatures) = notarize(transaction, filteredTransactionsAndSignatures)
        persistNotarizedTransaction(notarizedTransaction)
        sendNotarySignaturesToCounterparties(notarySignatures)
        log.info { "Finalisation of transaction $transactionId has been finished." }
        return notarizedTransaction
    }

    @Suspendable
    private fun persistUnverifiedTransaction() {
        persistenceService.persist(initialTransaction, TransactionStatus.UNVERIFIED)
        log.info { "Recorded transaction with initial signatures $transactionId" }
    }

    // Send initialTransaction, transferAdditionalSignatures, and filteredTransactionsAndSignatures
    // to counterparties. filteredTransactionsAndSignatures will be null if the backchain is required since
    // we don't need to send dependency payload to check.
    @Suspendable
    private fun sendTransactionAndDependenciesToCounterparties(
        filteredTransactionsAndSignatures: List<UtxoFilteredTransactionAndSignatures>?,
        notaryInfo: NotaryInfo,
        transferAdditionalSignatures: Boolean
    ) {
        flowMessaging.sendAll(
            FinalityPayload(initialTransaction, transferAdditionalSignatures, filteredTransactionsAndSignatures),
            sessions.toSet()
        )

        if (notaryInfo.isBackchainRequired) {
            sessions.forEach {
                if (initialTransaction.dependencies.isNotEmpty()) {
                    flowEngine.subFlow(TransactionBackchainSenderFlow(initialTransaction.id, it))
                } else {
                    log.info {
                        "Transaction with id ${initialTransaction.id} has no dependencies so backchain resolution will not be performed."
                    }
                }
            }
        }
    }

    @Suspendable
    private fun findFilteredTransactionsAndSignatures(notaryInfo: NotaryInfo): List<UtxoFilteredTransactionAndSignatures>? {
        return if (!notaryInfo.isBackchainRequired) {
            utxoLedgerService.findFilteredTransactionsAndSignatures(initialTransaction).values.toList()
        } else {
            null
        }
    }

    @Suspendable
    private fun getNotaryInfo(): NotaryInfo = requireNotNull(notaryLookup.lookup(initialTransaction.notaryName)) {
        "Notary ${initialTransaction.notaryName} does not exist in the network"
    }

    @Suppress("MaxLineLength")
    @Suspendable
    private fun receiveSignaturesAndAddToTransaction(): TransactionAndReceivedSignatures {
        val signaturesPayloads = try {
            flowMessaging.receiveAllMap(
                sessions.associateWith { Payload::class.java }
            ).mapValues {
                @Suppress("unchecked_cast")
                it.value as Payload<List<DigitalSignatureAndMetadata>>
            }
        } catch (e: CordaRuntimeException) {
            log.warn("Failed to receive signatures from ${sessions.map { it.counterparty }} for transaction $transactionId")
            persistInvalidTransaction(initialTransaction)
            throw e
        }

        var transaction = initialTransaction
        val signaturesReceivedFromSessions = signaturesPayloads.map { (session, signaturesPayload) ->

            val signatures = when (signaturesPayload) {
                is Payload.Success -> signaturesPayload.value
                is Payload.Failure<*> -> {
                    val message = "Failed to receive signatures from ${session.counterparty} for transaction " +
                        "$transactionId with message: ${signaturesPayload.message}"
                    log.info { message }
                    persistInvalidTransaction(initialTransaction)
                    throw CordaRuntimeException(message)
                }
            }

            log.info { "Received ${signatures.size} signature(s) from ${session.counterparty} for transaction $transactionId" }

            signatures.forEach { signature ->
                transaction = verifyAndAddSignature(transaction, signature)
                log.info {
                    "Added signature $signature by (key id) ${signature.by} from ${session.counterparty} for transaction " +
                        transactionId
                }
            }
            session to signatures
        }.toMap()

        return TransactionAndReceivedSignatures(
            transaction,
            signaturesReceivedFromSessions,
            newSignatures = transaction.signatures.toSet() - initialTransaction.signatures.toSet()
        )
    }

    @Suspendable
    private fun verifyAllReceivedSignatures(
        transaction: UtxoSignedTransactionInternal,
        signaturesReceivedFromSessions: Map<FlowSession, List<DigitalSignatureAndMetadata>>
    ) {
        log.info { "Verifying all signatures for transaction $transactionId." }

        try {
            transaction.verifySignatorySignatures()
        } catch (e: TransactionMissingSignaturesException) {
            val counterpartiesToSignatoriesMessages = signaturesReceivedFromSessions.map { (session, signatures) ->
                "${session.counterparty} provided ${signatures.size} signature(s) to satisfy the signatories (key ids) " +
                    signatures.map { it.by }
            }
            val counterpartiesToSignatoriesMessage = if (counterpartiesToSignatoriesMessages.isNotEmpty()) {
                "\n${counterpartiesToSignatoriesMessages.joinToString(separator = "\n")}"
            } else {
                "[]"
            }
            val message = "Transaction $transactionId is missing signatures for signatories (key ids) " +
                "${e.missingSignatories.map { it.fullId() }}. The following counterparties provided signatures while finalizing " +
                "the transaction: $counterpartiesToSignatoriesMessage"
            log.info { message }
            persistInvalidTransaction(transaction)
            throw TransactionMissingSignaturesException(transactionId, e.missingSignatories, message)
        }
    }

    @Suspendable
    private fun persistCounterpartySignatures(id: SecureHash, newSignatures: Set<DigitalSignatureAndMetadata>) {
        persistenceService.persistTransactionSignatures(id, newSignatures)
        log.info { "Recorded transaction with all parties' signatures $transactionId" }
    }

    @Suspendable
    private fun sendUnseenSignaturesToCounterparties(
        transaction: UtxoSignedTransactionInternal,
        signaturesReceivedFromSessions: Map<FlowSession, List<DigitalSignatureAndMetadata>>,
    ) {
        val notSeenSignaturesBySessions = signaturesReceivedFromSessions.map { (session, signatures) ->
            session to transaction.signatures.filter {
                it !in initialTransaction.signatures && // These have already been distributed with the first go
                    it !in signatures // These came from that party
            }
        }.toMap()
        log.info { "Sending updated signatures to counterparties for transaction $transactionId" }
        flowMessaging.sendAllMap(notSeenSignaturesBySessions)
        log.info { "Sent updated signatures to counterparties for transaction $transactionId" }
    }

    @Suppress("ThrowsCount")
    @Suspendable
    private fun notarize(
        transaction: UtxoSignedTransactionInternal,
        filteredTransactionsAndSignatures: List<UtxoFilteredTransactionAndSignatures>?
    ): Pair<UtxoSignedTransactionInternal, List<DigitalSignatureAndMetadata>> {
        val notary = transaction.notaryName

        val notarize = @Suspendable { attemptNumber: Int ->
            val notarizationFlow = pluggableNotaryService.create(
                filteredTransactionsAndSignatures?.let {
                    UtxoSignedTransactionWithDependencies(transaction, it)
                } ?: transaction,
                pluggableNotaryDetails,
                NotarizationType.WRITE
            )
            // `log.trace {}` and `log.debug {}` are not used in this method due to a Quasar issue.
            if (log.isTraceEnabled) {
                log.info {
                    "Notarizing transaction $transactionId using pluggable notary client flow of " +
                        "${notarizationFlow::class.java.name} with notary $notary. Attempt number $attemptNumber"
                }
            }
            flowEngine.subFlow(notarizationFlow)
        }

        var notarySignatures: List<DigitalSignatureAndMetadata>
        var attemptNumber = 0

        while (true) {
            try {
                notarySignatures = notarize(++attemptNumber)
                break
            } catch (e: NotaryExceptionGeneral) {
                log.info {
                    "Received general error from notarization for transaction: ${transaction.id} on attempt: $attemptNumber. " +
                        "Error: ${e.message} Retrying notarisation."
                }
                continue
            } catch (e: NotaryExceptionUnknown) {
                log.info {
                    "Received unknown error from notarization for transaction: ${transaction.id} on attempt: $attemptNumber. " +
                        "Error: ${e.message} Retrying notarisation."
                }
                continue
            } catch (e: CordaRuntimeException) {
                val (message, failureReason) = if (e is NotaryExceptionFatal) {
                    persistInvalidTransaction(transaction)
                    "Notarization failed permanently with ${e.message}." to FinalityNotarizationFailureType.FATAL
                } else {
                    "Notarization failed with ${e.message}." to FinalityNotarizationFailureType.UNKNOWN
                }

                flowMessaging.sendAll(
                    Payload.Failure<List<DigitalSignatureAndMetadata>>(message, failureReason.value),
                    sessions.toSet()
                )
                log.info { message }
                throw e
            }
        }

        if (log.isTraceEnabled) {
            log.info {
                "Received ${notarySignatures.size} signature(s) from notary $notary after requesting notarization of transaction " +
                    transactionId
            }
        }

        if (notarySignatures.isEmpty()) {
            val message =
                "Notary $notary did not return any signatures after requesting notarization of transaction $transactionId"
            log.info { message }
            persistInvalidTransaction(transaction)
            flowMessaging.sendAll(
                Payload.Failure<List<DigitalSignatureAndMetadata>>(
                    message,
                    FinalityNotarizationFailureType.FATAL.value
                ),
                sessions.toSet()
            )
            throw CordaRuntimeException(message)
        }
        var notarizedTransaction = transaction
        notarySignatures.forEach { signature ->
            try {
                notarizedTransaction = verifyAndAddNotarySignature(notarizedTransaction, signature)
            } catch (e: Exception) {
                val message = e.message ?: "Notary signature verification failed."
                flowMessaging.sendAll(
                    Payload.Failure<List<DigitalSignatureAndMetadata>>(
                        message,
                        FinalityNotarizationFailureType.FATAL.value
                    ),
                    sessions.toSet()
                )
                throw e
            }
        }

        if (log.isDebugEnabled) {
            log.debug {
                "Successfully notarized transaction $transactionId using notary $notary and received ${notarySignatures.size} signature(s)"
            }
        }

        return notarizedTransaction to notarySignatures
    }

    @Suspendable
    private fun persistNotarizedTransaction(transaction: UtxoSignedTransactionInternal) {
        val visibleStatesIndexes = transaction.getVisibleStateIndexes(visibilityChecker)
        val persistedAt = persistenceService.persist(transaction, TransactionStatus.VERIFIED, visibleStatesIndexes)
        log.debug { "Recorded notarized transaction $transactionId persisted at $persistedAt" }
    }

    @Suspendable
    private fun sendNotarySignaturesToCounterparties(notarySignatures: List<DigitalSignatureAndMetadata>) {
        flowMessaging.sendAll(Payload.Success(notarySignatures), sessions.toSet())
    }

    private data class TransactionAndReceivedSignatures(
        val transaction: UtxoSignedTransactionInternal,
        val sessionsToSignatures: Map<FlowSession, List<DigitalSignatureAndMetadata>>,
        val newSignatures: Set<DigitalSignatureAndMetadata>
    )
}
