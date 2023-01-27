package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.common.flow.transaction.TransactionMissingSignaturesException
import net.corda.ledger.notary.worker.selection.NotaryVirtualNodeSelectorService
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainSenderFlow
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.loggerFor
import net.corda.v5.base.util.trace
import net.corda.v5.ledger.notary.plugin.api.PluggableNotaryClientFlow
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import java.security.AccessController
import java.security.PrivilegedExceptionAction
import kotlin.reflect.full.primaryConstructor

@CordaSystemFlow
class UtxoFinalityFlow(
    private val initialTransaction: UtxoSignedTransactionInternal,
    private val sessions: List<FlowSession>,
    private val pluggableNotaryClientFlow: Class<PluggableNotaryClientFlow>
) : UtxoFinalityBase() {

    private companion object {
        val log = loggerFor<UtxoFinalityFlow>()
    }

    private val transactionId = initialTransaction.id

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var virtualNodeSelectorService: NotaryVirtualNodeSelectorService

    @Suspendable
    override fun call(): UtxoSignedTransaction {
        log.trace("Starting finality flow for transaction: $transactionId")
        verifyTransaction(initialTransaction)
        persistUnverifiedTransaction()
        sendTransactionAndBackchainToCounterparties()
        val (transaction, signaturesReceivedFromSessions) = receiveSignaturesAndAddToTransaction()
        verifyAllReceivedSignatures(transaction, signaturesReceivedFromSessions)
        persistTransactionWithCounterpartySignatures(transaction)
        sendUnseenSignaturesToCounterparties(transaction, signaturesReceivedFromSessions)
        val (notarizedTransaction, notarySignatures) = notarize(transaction)
        persistNotarizedTransaction(notarizedTransaction)
        sendNotarySignaturesToCounterparties(notarySignatures)
        log.trace { "Finalisation of transaction $transactionId has been finished." }
        return notarizedTransaction
    }

    @Suspendable
    private fun persistUnverifiedTransaction() {
        persistenceService.persist(initialTransaction, TransactionStatus.UNVERIFIED)
        log.debug { "Recorded transaction with initial signatures $transactionId" }
    }

    @Suspendable
    private fun sendTransactionAndBackchainToCounterparties() {
        sessions.forEach {
            it.send(initialTransaction)
            flowEngine.subFlow(TransactionBackchainSenderFlow(it))
        }
    }

    @Suppress("MaxLineLength")
    @Suspendable
    private fun receiveSignaturesAndAddToTransaction(): Pair<UtxoSignedTransactionInternal, Map<FlowSession, List<DigitalSignatureAndMetadata>>> {
        val signaturesPayloads = sessions.associateWith { session ->
            try {
                log.debug { "Requesting signatures from ${session.counterparty} for transaction $transactionId" }
                session.receive<Payload<List<DigitalSignatureAndMetadata>>>()
            } catch (e: CordaRuntimeException) {
                log.warn("Failed to receive signatures from ${session.counterparty} for transaction $transactionId")
                throw e
            }
        }

        var transaction = initialTransaction
        val signaturesReceivedFromSessions = signaturesPayloads.map { (session, signaturesPayload) ->
            val signatures = signaturesPayload.getOrThrow { failure ->
                val message = "Failed to receive signatures from ${session.counterparty} for transaction " +
                        "$transactionId with message: ${failure.message}"
                log.warn(message)
                CordaRuntimeException(message)
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

    private fun verifyAllReceivedSignatures(
        transaction: UtxoSignedTransactionInternal,
        signaturesReceivedFromSessions: Map<FlowSession, List<DigitalSignatureAndMetadata>>
    ) {
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
            throw TransactionMissingSignaturesException(transactionId, e.missingSignatories, message)
        }
    }

    @Suspendable
    private fun persistTransactionWithCounterpartySignatures(transaction: UtxoSignedTransactionInternal) {
        val relevantStatesIndexes = transaction.getRelevantStatesIndexes(memberLookup.getMyLedgerKeys())
        persistenceService.persist(transaction, TransactionStatus.UNVERIFIED, relevantStatesIndexes)
        log.debug { "Recorded transaction with all parties' signatures $transactionId" }
    }

    @Suspendable
    private fun sendUnseenSignaturesToCounterparties(
        transaction: UtxoSignedTransactionInternal,
        signaturesReceivedFromSessions: Map<FlowSession, List<DigitalSignatureAndMetadata>>
    ) {
        val notSeenSignaturesBySessions = signaturesReceivedFromSessions.map { (session, signatures) ->
            session to transaction.signatures.filter {
                it !in initialTransaction.signatures &&             // These have already been distributed with the first go
                it !in signatures                                   // These came from that party
            }
        }.toMap()
        log.trace { "Sending updated signatures to counterparties for transaction $transactionId" }
        flowMessaging.sendAllMap(notSeenSignaturesBySessions)
        log.debug { "Sent updated signatures to counterparties for transaction $transactionId" }
    }

    @Suppress("ThrowsCount")
    @Suspendable
    private fun notarize(
        transaction: UtxoSignedTransactionInternal
    ): Pair<UtxoSignedTransactionInternal, List<DigitalSignatureAndMetadata>> {
        val notary = transaction.notary

        val notarizationFlow = newPluggableNotaryClientFlowInstance(transaction)

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
            val message = "Notarization failed with ${e.message}."
            flowMessaging.sendAll(Payload.Failure<List<DigitalSignatureAndMetadata>>(message), sessions.toSet())
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
            val message = "Notary $notary did not return any signatures after requesting notarization of transaction $transactionId"
            log.warn(message)
            flowMessaging.sendAll(Payload.Failure<List<DigitalSignatureAndMetadata>>(message), sessions.toSet())
            throw CordaRuntimeException(message)
        }
        var notarizedTransaction = transaction
        notarySignatures.forEach { signature ->
            notarizedTransaction = try {
                verifyAndAddNotarySignature(notarizedTransaction, signature)
            } catch (e: Exception) {
                val message = e.message ?: "Notary signature verification failed."
                flowMessaging.sendAll(Payload.Failure<List<DigitalSignatureAndMetadata>>(message), sessions.toSet())
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

    // Gets a new notary client plugin flow instance. This is done in a non-suspendable
    // function to avoid trying (and failing) to serialize the objects used internally.
    @VisibleForTesting
    internal fun newPluggableNotaryClientFlowInstance(
        transaction: UtxoSignedTransactionInternal
    ) : PluggableNotaryClientFlow {
        return AccessController.doPrivileged(PrivilegedExceptionAction {
            pluggableNotaryClientFlow.kotlin.primaryConstructor!!.call(
                transaction, virtualNodeSelectorService.selectVirtualNode(transaction.notary)
            )
        })
    }

    @Suspendable
    private fun persistNotarizedTransaction(transaction: UtxoSignedTransactionInternal) {
        persistenceService.persist(transaction, TransactionStatus.VERIFIED)
        log.debug { "Recorded notarised transaction $transactionId" }
    }

    @Suspendable
    private fun sendNotarySignaturesToCounterparties(notarySignatures: List<DigitalSignatureAndMetadata>) {
        flowMessaging.sendAll(Payload.Success(notarySignatures), sessions.toSet())
    }
}
