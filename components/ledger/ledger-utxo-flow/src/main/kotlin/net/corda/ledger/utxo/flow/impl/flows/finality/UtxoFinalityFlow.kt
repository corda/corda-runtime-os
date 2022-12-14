package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainSenderFlow
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoLedgerTransactionVerifier
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import java.security.PublicKey

@CordaSystemFlow
class UtxoFinalityFlow(
    private val signedTransaction: UtxoSignedTransactionInternal,
    private val sessions: List<FlowSession>
) : SubFlow<UtxoSignedTransaction> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var transactionSignatureService: TransactionSignatureService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var persistenceService: UtxoLedgerPersistenceService

    @CordaInject
    lateinit var serializationService: SerializationService

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(): UtxoSignedTransaction {

        verify(signedTransaction)

        // TODO Check there is at least one state

        // Check if the sessions' counterparties are all available and have keys.
        val sessionPublicKeys = sessions.map { session ->
            val member = memberLookup.lookup(session.counterparty)
                ?: throw CordaRuntimeException(
                    "A session with ${session.counterparty} exists but the member no longer exists in the membership group"
                )
            session to member
        }.associate { (session, memberInfo) ->
            session to memberInfo.ledgerKeys.ifEmpty {
                throw CordaRuntimeException(
                    "A session with ${session.counterparty} exists but the member does not have any active ledger keys"
                )
            }
        }

        // TODO [CORE-8655] Should this also be a [CordaRuntimeException]? Or make the others [IllegalArgumentException]s?
        val missingSignatories = signedTransaction.getMissingSignatories()
        // Check if all missing signing keys are covered by the sessions.
        require(sessionPublicKeys.values.flatten().containsAll(missingSignatories)) {
            "Required signatures $missingSignatories but ledger keys for the passed in sessions are $sessionPublicKeys"
        }

        persistenceService.persist(signedTransaction, TransactionStatus.UNVERIFIED)

        // TODO [CORE-7032] Use [FlowMessaging] bulk send and receives instead of the sends and receives in the loop below

        var signedByParticipantsTransaction = signedTransaction

        sessions.forEach { session ->
            // TODO Use [FlowMessaging.sendAll] and [FlowMessaging.receiveAll] anyway
            log.debug { "Requesting signature from ${session.counterparty} for signed transaction ${signedTransaction.id}" }
            session.send(signedTransaction)

            flowEngine.subFlow(TransactionBackchainSenderFlow(session))

            val signaturesPayload = try {
                session.receive<Payload<List<DigitalSignatureAndMetadata>>>()
            } catch (e: CordaRuntimeException) {
                log.warn("Failed to receive signature from ${session.counterparty} for signed transaction ${signedTransaction.id}")
                throw e
            }

            val signatures = signaturesPayload.getOrThrow { failure ->
                val message = "Failed to receive signature from ${session.counterparty} for signed transaction " +
                        "${signedTransaction.id} with message: ${failure.message}"
                log.debug { message }
                CordaRuntimeException(message)
            }

            log.debug { "Received signatures from ${session.counterparty} for signed transaction ${signedTransaction.id}" }

            requireCorrectReceivedSigningKeys(
                signatures,
                missingSignatories,
                ledgerKeys = sessionPublicKeys[session]!!,
                session
            )

            signatures.forEach { signature ->
                try {
                    transactionSignatureService.verifySignature(signedTransaction.id, signature)
                    log.debug {
                        "Successfully verified signature from ${session.counterparty} of $signature for signed transaction " +
                                "${signedTransaction.id}"
                    }
                } catch (e: Exception) {
                    log.warn(
                        "Failed to verify signature from ${session.counterparty} of $signature for signed transaction " +
                                "${signedTransaction.id}. Message: ${e.message}"
                    )

                    throw e
                }
                signedByParticipantsTransaction =
                    signedByParticipantsTransaction.addSignature(signature)
                log.debug { "Added signature from ${session.counterparty} of $signature for signed transaction ${signedTransaction.id}" }
            }
        }

        persistenceService.persist(signedByParticipantsTransaction, TransactionStatus.VERIFIED)
        log.debug { "Recorded signed transaction ${signedTransaction.id}" }

        // TODO Consider removing
        for (session in sessions) {
            // Split send and receive since we have to use [FlowMessaging.sendAll] and [FlowMessaging.receiveAll] anyway
            session.send(signedByParticipantsTransaction)
            // Do we want a situation where a boolean can be received to execute some sort of failure logic?
            // Or would that always be covered by an exception as it always indicates something wrong occurred.
            // Returning a context map might be appropriate in case we want to do any sort of handling in the future
            // without having to worry about backwards compatibility.
            session.receive<Unit>()
            log.debug { "${session.counterparty} received and acknowledged storage of signed transaction ${signedTransaction.id}" }
        }

        if (sessions.isNotEmpty()) {
            log.debug { "All sessions received and acknowledged storage of signed transaction ${signedTransaction.id}" }
        }

        return signedByParticipantsTransaction
    }

    private fun verify(signedTransaction: UtxoSignedTransaction) {
        UtxoLedgerTransactionVerifier(signedTransaction.toLedgerTransaction()).verify()
    }

    private fun requireCorrectReceivedSigningKeys(
        signatures: List<DigitalSignatureAndMetadata>,
        missingSignatories: Set<PublicKey>,
        ledgerKeys: List<PublicKey>,
        session: FlowSession
    ) {
        val receivedSigningKeys = signatures.map { it.by }
        val expectedSigningKeys = missingSignatories.intersect(ledgerKeys.toSet())
        if (receivedSigningKeys.toSet() != expectedSigningKeys) {
            throw CordaRuntimeException(
                "A session with ${session.counterparty} did not return the signatures with the expected keys. " +
                        "Expected: $expectedSigningKeys But received: $receivedSigningKeys"
            )
        }
    }
}
