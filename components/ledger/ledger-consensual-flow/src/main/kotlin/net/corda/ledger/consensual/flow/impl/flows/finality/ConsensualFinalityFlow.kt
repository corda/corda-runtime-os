package net.corda.ledger.consensual.flow.impl.flows.finality

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.common.flow.transaction.TransactionMissingSignaturesException
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionInternal
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.debug
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CordaSystemFlow
class ConsensualFinalityFlow(
    private val initialTransaction: ConsensualSignedTransactionInternal,
    private val sessions: List<FlowSession>
) : ConsensualFinalityBase() {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(ConsensualFinalityFlow::class.java)
    }

    override val log: Logger = ConsensualFinalityFlow.log

    private val transactionId = initialTransaction.id

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @Suspendable
    override fun call(): ConsensualSignedTransaction {
        verifyExistingSignatures(initialTransaction)
        verifyTransaction(initialTransaction)

        // Initial verifications passed, the transaction can be saved in the database.
        persistUnverifiedTransaction()

        val (transaction, signaturesReceivedFromSessions) = receiveSignaturesAndAddToTransaction()
        verifyAllReceivedSignatures(transaction, signaturesReceivedFromSessions)
        persistTransactionWithCounterpartySignatures(transaction)
        sendUnseenSignaturesToCounterparties(transaction, signaturesReceivedFromSessions)

        if (sessions.isNotEmpty()) {
            log.debug { "All sessions received and acknowledged storage of transaction $transactionId" }
        }

        return transaction
    }

    @Suspendable
    private fun persistUnverifiedTransaction() {
        persistenceService.persist(initialTransaction, TransactionStatus.UNVERIFIED)
        log.debug { "Recorded transaction with initial signatures $transactionId" }
    }

    @Suppress("MaxLineLength")
    @Suspendable
    private fun receiveSignaturesAndAddToTransaction(): Pair<ConsensualSignedTransactionInternal, Map<FlowSession, List<DigitalSignatureAndMetadata>>>  {

        // TODO [CORE-7032] Use [FlowMessaging] bulk send and receives instead of the sends and receives in the loop below
        val signaturesPayloads = sessions.associateWith { session ->
            try {
                log.debug { "Requesting signatures from ${session.counterparty} for transaction $transactionId" }
                session.send(initialTransaction)
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

            log.debug { "Received ${signatures.size} signatures from ${session.counterparty} for transaction $transactionId" }

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
        transaction: ConsensualSignedTransactionInternal,
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
            persistInvalidTransaction(transaction)
            throw TransactionMissingSignaturesException(transactionId, e.missingSignatories, message)
        }
    }

    @Suspendable
    private fun persistTransactionWithCounterpartySignatures(transaction: ConsensualSignedTransactionInternal) {
        persistenceService.persist(transaction, TransactionStatus.VERIFIED)
        log.debug { "Recorded transaction with all parties' signatures $transactionId" }
    }

    @Suspendable
    private fun sendUnseenSignaturesToCounterparties(
        transaction: ConsensualSignedTransactionInternal,
        signaturesReceivedFromSessions: Map<FlowSession, List<DigitalSignatureAndMetadata>>
    ) {
        val notSeenSignaturesBySessions = signaturesReceivedFromSessions.map { (session, signatures) ->
            session to transaction.signatures.filter {
                it !in initialTransaction.signatures &&             // These have already been distributed with the first go
                it !in signatures                                   // These came from that party
            }
        }.toMap()
        flowMessaging.sendAllMap(notSeenSignaturesBySessions)
        log.debug { "Sent updated signatures to counterparties for transaction $transactionId" }
    }
}
