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
import java.security.PublicKey

@CordaSystemFlow
class ConsensualFinalityFlow(
    private val signedTransaction: ConsensualSignedTransactionInternal,
    private val sessions: List<FlowSession>
) : SubFlow<ConsensualSignedTransaction> {

    private companion object {
        val log = contextLogger()
    }

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
            log.debug { "All sessions received and acknowledged storage of transaction ${signedTransaction.id}" }
        }

        return fullySignedTransaction
    }

    @Suspendable
    private fun persistUnverifiedTransaction() {
        persistenceService.persist(signedTransaction, TransactionStatus.UNVERIFIED)
        log.debug { "Recorded transaction with initial signatures ${signedTransaction.id}" }
    }

    @Suspendable
    private fun receiveSignaturesAndAddToTransaction(): ConsensualSignedTransactionInternal {

        var signedByParticipantsTransaction = signedTransaction

        // TODO [CORE-7032] Use [FlowMessaging] bulk send and receives instead of the sends and receives in the loop below
        sessions.forEach { session ->
            // TODO Use [FlowMessaging.sendAll] and [FlowMessaging.receiveAll] anyway
            log.debug { "Requesting signatures from ${session.counterparty} for transaction ${signedTransaction.id}" }
            session.send(signedTransaction)

            val signaturesPayload = try {
                session.receive<Payload<List<DigitalSignatureAndMetadata>>>()
            } catch (e: CordaRuntimeException) {
                log.warn("Failed to receive signature from ${session.counterparty} for transaction ${signedTransaction.id}")
                throw e
            }

            val signatures = signaturesPayload.getOrThrow { failure ->
                val message = "Failed to receive signature from ${session.counterparty} for transaction " +
                        "${signedTransaction.id} with message: ${failure.message}"
                log.warn(message)
                CordaRuntimeException(message)
            }

            log.debug { "Received ${signatures.size} signatures from ${session.counterparty} for transaction ${signedTransaction.id}" }

            signatures.forEach { signature ->
                try {
                    transactionSignatureService.verifySignature(signedTransaction.id, signature)
                    log.debug {
                        "Successfully verified signature from ${session.counterparty} of $signature for transaction " +
                                "${signedTransaction.id}"
                    }
                } catch (e: Exception) {
                    log.warn(
                        "Failed to verify signature from ${session.counterparty} of $signature for transaction " +
                                "${signedTransaction.id}. Message: ${e.message}"
                    )

                    throw e
                }
                signedByParticipantsTransaction = signedByParticipantsTransaction.addSignature(signature)
                log.debug { "Added signature from ${session.counterparty} of $signature for transaction ${signedTransaction.id}" }
            }
        }

        return signedByParticipantsTransaction
    }

    @Suspendable
    private fun persistFullySignedTransaction(fullySignedTransaction: ConsensualSignedTransactionInternal) {
        persistenceService.persist(fullySignedTransaction, TransactionStatus.VERIFIED)
        log.debug { "Recorded transaction ${signedTransaction.id}" }
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
            log.debug { "${session.counterparty} received and acknowledged storage of transaction ${signedTransaction.id}" }
        }
    }
}
