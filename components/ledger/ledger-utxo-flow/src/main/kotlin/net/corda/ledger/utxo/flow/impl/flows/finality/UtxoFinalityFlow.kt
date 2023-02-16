package net.corda.ledger.utxo.flow.impl.flows.finality

import com.r3.corda.notary.plugin.common.NotaryException
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.common.flow.transaction.TransactionMissingSignaturesException
import net.corda.ledger.notary.plugin.factory.PluggableNotaryClientFlowFactory
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainSenderFlow
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderInternal
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import net.corda.v5.ledger.common.transaction.TransactionNoAvailableKeysException
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CordaSystemFlow
class UtxoFinalityFlow(
    private val transactionBuilder: UtxoTransactionBuilder,
    private val sessions: List<FlowSession>
) : UtxoFinalityBase() {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(UtxoFinalityFlow::class.java)
    }

    override val log: Logger = UtxoFinalityFlow.log


    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var pluggableNotaryClientFlowFactory: PluggableNotaryClientFlowFactory

    @Suspendable
    override fun call(): UtxoSignedTransaction {

        val initialTransaction = verifyAndSignTransactionBuilder()

        log.trace("Beginning finalization for transaction: ${initialTransaction.id}")
        verifyExistingSignatures(initialTransaction)
        verifyTransaction(initialTransaction)

        // Initial verifications passed, the transaction can be saved in the database.
        persistUnverifiedTransaction(initialTransaction)

        sendTransactionAndBackchainToCounterparties(initialTransaction)
        val (transaction, signaturesReceivedFromSessions) = receiveSignaturesAndAddToTransaction(initialTransaction)
        verifyAllReceivedSignatures(transaction, signaturesReceivedFromSessions)
        persistTransactionWithCounterpartySignatures(transaction)
        sendUnseenSignaturesToCounterparties(initialTransaction, transaction, signaturesReceivedFromSessions)
        val (notarizedTransaction, notarySignatures) = notarize(transaction)
        persistNotarizedTransaction(notarizedTransaction)
        sendNotarySignaturesToCounterparties(notarySignatures)
        log.trace { "Finalisation of transaction ${initialTransaction.id} has been finished." }
        return notarizedTransaction
    }

    @Suspendable
    private fun verifyAndSignTransactionBuilder(): UtxoSignedTransactionInternal {
        return try {
            (transactionBuilder as UtxoTransactionBuilderInternal).toSignedTransaction()
        } catch (e: Exception) {
            when (e) {
                is IllegalStateException -> log.warn(
                    "Failed to verify platform checks and initially sign transaction with content $transactionBuilder"
                )
                is TransactionNoAvailableKeysException -> log.warn(
                    "No keys available to initially sign transaction with content $transactionBuilder"
                )
            }
            throw e
        }
    }

    @Suspendable
    private fun persistUnverifiedTransaction(initialTransaction: UtxoSignedTransactionInternal) {
        persistenceService.persist(initialTransaction, TransactionStatus.UNVERIFIED)
        log.debug { "Recorded transaction with initial signatures ${initialTransaction.id}" }
    }

    @Suspendable
    private fun sendTransactionAndBackchainToCounterparties(initialTransaction: UtxoSignedTransactionInternal) {
        sessions.forEach {
            it.send(initialTransaction)
            flowEngine.subFlow(TransactionBackchainSenderFlow(initialTransaction, it))
        }
    }

    @Suppress("MaxLineLength")
    @Suspendable
    private fun receiveSignaturesAndAddToTransaction(initialTransaction: UtxoSignedTransactionInternal): Pair<UtxoSignedTransactionInternal, Map<FlowSession, List<DigitalSignatureAndMetadata>>> {
        val transactionId = initialTransaction.id
        val signaturesPayloads = sessions.associateWith { session ->
            try {
                log.debug { "Requesting signatures from ${session.counterparty} for transaction $transactionId" }
                session.receive<Payload<List<DigitalSignatureAndMetadata>>>()
            } catch (e: CordaRuntimeException) {
                log.warn("Failed to receive signatures from ${session.counterparty} for transaction $transactionId")
                persistInvalidTransaction(initialTransaction)
                throw e
            }
        }

        var transaction = initialTransaction
        val signaturesReceivedFromSessions = signaturesPayloads.map { (session, signaturesPayload) ->

            val signatures = when (signaturesPayload) {
                is Payload.Success -> signaturesPayload.value
                is Payload.Failure<*> -> {
                    val message = "Failed to receive signatures from ${session.counterparty} for transaction " +
                            "$transactionId with message: ${signaturesPayload.message}"
                    log.warn(message)
                    persistInvalidTransaction(initialTransaction)
                    throw CordaRuntimeException(message)
                }
            }

            log.debug { "Received ${signatures.size} signature(s) from ${session.counterparty} for transaction $transactionId" }

            signatures.forEach { signature ->
                transaction = verifyAndAddSignature(transaction, signature)
                log.debug {
                    "Added signature by ${signature.by.encoded} (encoded) from ${session.counterparty} of $signature for transaction " +
                            transactionId
                }
            }
            session to signatures
        }.toMap()

        return transaction to signaturesReceivedFromSessions
    }

    @Suspendable
    private fun verifyAllReceivedSignatures(
        transaction: UtxoSignedTransactionInternal,
        signaturesReceivedFromSessions: Map<FlowSession, List<DigitalSignatureAndMetadata>>
    ) {
        val transactionId = transaction.id
        log.debug { "Verifying all signatures for transaction $transactionId." }

        try {
            transaction.verifySignatures()
        } catch (e: TransactionMissingSignaturesException) {
            val counterpartiesToSignatoriesMessages = signaturesReceivedFromSessions.map { (session, signatures) ->
                "${session.counterparty} provided ${signatures.size} signature(s) to satisfy the signatories (encoded) " +
                        signatures.map { it.by.encoded }
            }
            val counterpartiesToSignatoriesMessage = if (counterpartiesToSignatoriesMessages.isNotEmpty()) {
                "\n${counterpartiesToSignatoriesMessages.joinToString(separator = "\n")}"
            } else {
                "[]"
            }
            val message = "Transaction $transactionId is missing signatures for signatories (encoded) " +
                    "${e.missingSignatories.map { it.encoded }}. The following counterparties provided signatures while finalizing " +
                    "the transaction: $counterpartiesToSignatoriesMessage"
            log.warn(message)
            persistInvalidTransaction(transaction)
            throw TransactionMissingSignaturesException(transactionId, e.missingSignatories, message)
        }
    }

    @Suspendable
    private fun persistTransactionWithCounterpartySignatures(transaction: UtxoSignedTransactionInternal) {
        persistenceService.persist(transaction, TransactionStatus.UNVERIFIED)
        log.debug { "Recorded transaction with all parties' signatures ${transaction.id}" }
    }

    @Suspendable
    private fun sendUnseenSignaturesToCounterparties(
        initialTransaction: UtxoSignedTransactionInternal,
        transaction: UtxoSignedTransactionInternal,
        signaturesReceivedFromSessions: Map<FlowSession, List<DigitalSignatureAndMetadata>>
    ) {
        val notSeenSignaturesBySessions = signaturesReceivedFromSessions.map { (session, signatures) ->
            session to transaction.signatures.filter {
                it !in initialTransaction.signatures &&             // These have already been distributed with the first go
                        it !in signatures                                   // These came from that party
            }
        }.toMap()
        log.trace { "Sending updated signatures to counterparties for transaction ${transaction.id}" }
        flowMessaging.sendAllMap(notSeenSignaturesBySessions)
        log.debug { "Sent updated signatures to counterparties for transaction ${transaction.id}" }
    }

    @Suppress("ThrowsCount")
    @Suspendable
    private fun notarize(
        transaction: UtxoSignedTransactionInternal
    ): Pair<UtxoSignedTransactionInternal, List<DigitalSignatureAndMetadata>> {
        val transactionId = transaction.id
        val notary = transaction.notary
        val notarizationFlow = pluggableNotaryClientFlowFactory.create(notary, transaction)

        // `log.trace {}` and `log.debug {}` are not used in this method due to a Quasar issue.
        if (log.isTraceEnabled) {
            log.trace(
                "Notarizing transaction $transactionId using pluggable notary client flow of ${notarizationFlow::class.java.name} with " +
                        "notary $notary"
            )
        }

        val notarySignatures = try {
            flowEngine.subFlow(notarizationFlow)
        } catch (e: CordaRuntimeException) {
            val (message, failureReason) = if (e is NotaryException) {
                persistInvalidTransaction(transaction)
                "Notarization failed permanently with ${e.message}." to FinalityNotarizationFailureType.UNRECOVERABLE
            } else {
                "Notarization failed with ${e.message}." to FinalityNotarizationFailureType.OTHER
            }

            flowMessaging.sendAll(
                Payload.Failure<List<DigitalSignatureAndMetadata>>(message, failureReason.value),
                sessions.toSet()
            )
            log.warn(message)
            throw e
        }

        if (log.isTraceEnabled) {
            log.trace(
                "Received ${notarySignatures.size} signature(s) from notary $notary after requesting notarization of transaction " +
                        transactionId
            )
        }

        if (notarySignatures.isEmpty()) {
            val message =
                "Notary $notary did not return any signatures after requesting notarization of transaction $transactionId"
            log.warn(message)
            persistInvalidTransaction(transaction)
            flowMessaging.sendAll(
                Payload.Failure<List<DigitalSignatureAndMetadata>>(
                    message,
                    FinalityNotarizationFailureType.UNRECOVERABLE.value
                ), sessions.toSet()
            )
            throw CordaRuntimeException(message)
        }
        var notarizedTransaction = transaction
        notarySignatures.forEach { signature ->
            notarizedTransaction = try {
                verifyAndAddNotarySignature(notarizedTransaction, signature)
            } catch (e: Exception) {
                val message = e.message ?: "Notary signature verification failed."
                flowMessaging.sendAll(
                    Payload.Failure<List<DigitalSignatureAndMetadata>>(
                        message,
                        FinalityNotarizationFailureType.UNRECOVERABLE.value
                    ), sessions.toSet()
                )
                throw e
            }
        }

        if (log.isDebugEnabled) {
            log.debug(
                "Successfully notarized transaction $transactionId using notary $notary and received ${notarySignatures.size} signature(s)"
            )
        }

        return notarizedTransaction to notarySignatures
    }

    @Suspendable
    private fun persistNotarizedTransaction(transaction: UtxoSignedTransactionInternal) {
        val relevantStatesIndexes = transaction.getRelevantStatesIndexes(memberLookup.getMyLedgerKeys())
        persistenceService.persist(transaction, TransactionStatus.VERIFIED, relevantStatesIndexes)
        log.debug { "Recorded notarised transaction ${transaction.id}" }
    }

    @Suspendable
    private fun sendNotarySignaturesToCounterparties(notarySignatures: List<DigitalSignatureAndMetadata>) {
        flowMessaging.sendAll(Payload.Success(notarySignatures), sessions.toSet())
    }
}
