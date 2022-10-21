package net.corda.ledger.consensual.flow.impl.flows.finality

import net.corda.ledger.common.data.transaction.SignableData
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
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
import net.corda.v5.base.util.trace
import net.corda.v5.crypto.SignatureSpec
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction

class ConsensualFinalityFlow(
    private val signedTransaction: ConsensualSignedTransaction,
    private val sessions: List<FlowSession>
) : SubFlow<ConsensualSignedTransaction> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var digitalSignatureVerificationService: DigitalSignatureVerificationService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var serializationService: SerializationService

    @Suspendable
    override fun call(): ConsensualSignedTransaction {

        // TODO Check there is at least one state

        // Check if the sessions' counterparties are all available and have keys.
        val sessionPublicKeys = sessions.map { session ->
            session to (
                memberLookup.lookup(session.counterparty)
                    ?: throw CordaRuntimeException(
                        "A session with ${session.counterparty} exists but the member no longer exists in the membership group"
                    )

                )
        }.associate { (session, memberInfo) ->
            session to
                memberInfo.ledgerKeys.ifEmpty {
                    throw CordaRuntimeException(
                        "A session with ${memberInfo.name} exists but the member does not have any active ledger keys"
                    )
                }
        }

        // Should this also be a [CordaRuntimeException]? Or make the others [IllegalArgumentException]s?
        val missingSigningKeys = signedTransaction.getMissingSigningKeys()
        // Check if all missing signing keys are covered by the sessions.
        require(sessionPublicKeys.values.flatten().containsAll(missingSigningKeys)) {
            "Required signatures $missingSigningKeys but ledger keys for the passed in sessions are $sessionPublicKeys"
        }

        // TODO [CORE-7029] Record unfinalised transaction

        // TODO [CORE-7032] Use [FlowMessaging] bulk send and receives instead of the sends and receives in the loop below

        var signedByParticipantsTransaction = signedTransaction

        sessions.forEach{ session ->
            // TODO Use [FlowMessaging.sendAll] and [FlowMessaging.receiveAll] anyway
            log.debug { "Requesting signature from ${session.counterparty} for signed transaction ${signedTransaction.id}" }
            session.send(signedTransaction)

            val signatures = try {
                session.receive<List<DigitalSignatureAndMetadata>>()
            } catch (e: CordaRuntimeException) {
                log.warn(
                    "Failed to receive signature from ${session.counterparty} for signed transaction ${signedTransaction.id}"
                )
                throw e
            }
            log.debug { "Received signature from ${session.counterparty} for signed transaction ${signedTransaction.id}" }

            val receivedSigningKeys = signatures.map { it.by }
            if (receivedSigningKeys.toSet() != sessionPublicKeys[session]!!.toSet()) {
                throw CordaRuntimeException(
                    "A session with ${session.counterparty} did not return the signatures with the expected keys. " +
                            "Expected: ${sessionPublicKeys[session]} But received: $receivedSigningKeys"
                )
            }

            signatures.forEach {signature ->
                try {
                    // TODO Do not hardcode signature spec
                    val signedData = SignableData(signedTransaction.id, signature.metadata)
                    digitalSignatureVerificationService.verify(
                        publicKey = signature.by,
                        signatureSpec = SignatureSpec.ECDSA_SHA256,
                        signatureData = signature.signature.bytes,
                        clearData = serializationService.serialize(signedData).bytes
                    )
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
                signedByParticipantsTransaction = signedTransaction.addSignature(signature)
                log.trace {
                    "Added signature from ${session.counterparty} of $signature for signed transaction ${signedTransaction.id}"
                }
            }
        }

        // TODO [CORE-7055] Record the transaction

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
            log.debug {
                "${session.counterparty} received and acknowledged storage of signed transaction ${signedTransaction.id}"
            }
        }

        if (sessions.isNotEmpty()) {
            log.debug { "All sessions received and acknowledged storage of signed transaction ${signedTransaction.id}" }
        }

        return signedByParticipantsTransaction
    }
}