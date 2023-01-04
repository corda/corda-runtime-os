package net.corda.ledger.consensual.flow.impl.flows.finality

import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.consensual.flow.impl.persistence.ConsensualLedgerPersistenceService
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.consensual.flow.impl.transaction.ConsensualSignedTransactionInternal
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction

@CordaSystemFlow
class ConsensualFinalityFlow(
    private val initialTransaction: ConsensualSignedTransactionInternal,
    private val sessions: List<FlowSession>
) : SubFlow<ConsensualSignedTransaction> {

    private companion object {
        val log = contextLogger()
    }

    private val transactionId = initialTransaction.id

    @CordaInject
    lateinit var transactionSignatureService: TransactionSignatureService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var persistenceService: ConsensualLedgerPersistenceService

    @CordaInject
    lateinit var serializationService: SerializationService

    @Suspendable
    override fun call(): ConsensualSignedTransaction {

        // TODO Check there is at least one state

        persistUnverifiedTransaction()
        val fullySignedTransaction = receiveSignaturesAndAddToTransaction()
        persistFullySignedTransaction(fullySignedTransaction)
        sendFullySignedTransactionToCounterparties(fullySignedTransaction)

        if (sessions.isNotEmpty()) {
            log.debug { "All sessions received and acknowledged storage of transaction $transactionId" }
        }

        return fullySignedTransaction
    }

    @Suspendable
    private fun persistUnverifiedTransaction() {
        persistenceService.persist(initialTransaction, TransactionStatus.UNVERIFIED)
        log.debug { "Recorded transaction with initial signatures $transactionId" }
    }

    @Suspendable
    private fun receiveSignaturesAndAddToTransaction(): ConsensualSignedTransactionInternal {

        var transaction = initialTransaction

        // TODO [CORE-7032] Use [FlowMessaging] bulk send and receives instead of the sends and receives in the loop below
        sessions.forEach { session ->
            // TODO Use [FlowMessaging.sendAll] and [FlowMessaging.receiveAll] anyway
            log.debug { "Requesting signatures from ${session.counterparty} for transaction $transactionId" }
            session.send(initialTransaction)

            val signaturesPayload = try {
                session.receive<Payload<List<DigitalSignatureAndMetadata>>>()
            } catch (e: CordaRuntimeException) {
                log.warn("Failed to receive signature from ${session.counterparty} for transaction $transactionId")
                throw e
            }

            val signatures = signaturesPayload.getOrThrow { failure ->
                val message = "Failed to receive signature from ${session.counterparty} for transaction " +
                        "$transactionId with message: ${failure.message}"
                log.warn(message)
                CordaRuntimeException(message)
            }

            log.debug { "Received ${signatures.size} signatures from ${session.counterparty} for transaction $transactionId" }

            signatures.forEach { signature ->
                try {
                    transactionSignatureService.verifySignature(transactionId, signature)
                    log.debug {
                        "Successfully verified signature from ${session.counterparty} of $signature for transaction $transactionId"
                    }
                } catch (e: Exception) {
                    log.warn(
                        "Failed to verify signature from ${session.counterparty} of $signature for transaction $transactionId. Message: " +
                                e.message
                    )

                    throw e
                }
                transaction = transaction.addSignature(signature)
                log.debug { "Added signature from ${session.counterparty} of $signature for transaction $transactionId" }
            }
        }

        return transaction
    }

    @Suspendable
    private fun persistFullySignedTransaction(fullySignedTransaction: ConsensualSignedTransactionInternal) {
        persistenceService.persist(fullySignedTransaction, TransactionStatus.VERIFIED)
        log.debug { "Recorded transaction $transactionId" }
    }

    @Suspendable
    private fun sendFullySignedTransactionToCounterparties(fullySignedTransaction: ConsensualSignedTransactionInternal) {
        // TODO Consider removing
        for (session in sessions) {
            // Split send and receive since we have to use [FlowMessaging.sendAll] and [FlowMessaging.receiveAll] anyway
            session.send(fullySignedTransaction)
            // Do we want a situation where a boolean can be received to execute some sort of failure logic?
            // Or would that always be covered by an exception as it always indicates something wrong occurred.
            // Returning a context map might be appropriate in case we want to do any sort of handling in the future
            // without having to worry about backwards compatibility.
            session.receive<Unit>()
            log.debug { "${session.counterparty} received and acknowledged storage of transaction $transactionId" }
        }
    }
}
