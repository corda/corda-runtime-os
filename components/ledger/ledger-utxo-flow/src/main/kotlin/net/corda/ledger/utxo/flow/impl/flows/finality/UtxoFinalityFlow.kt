package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.uncheckedCast
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
    lateinit var flowMessaging: FlowMessaging

    @Suspendable
    override fun call(): UtxoSignedTransaction {

        /*REVERT*/log.info( "XXX1" )
        persistenceService.persist(signedTransaction, TransactionStatus.UNVERIFIED)
        /*REVERT*/log.info( "Recorded transaction with initial signatures ${signedTransaction.id}" )

        /*REVERT*/log.info(
            "Requesting signatures from ${
                sessions.map { it.counterparty }.joinToString("|")
            } for transaction ${signedTransaction.id}"
        )
        flowMessaging.sendAll(signedTransaction, sessions.toSet())

        /*REVERT*/log.info( "Waiting for other parties signature payloads" )
        val signaturesPayloads = try {
            flowMessaging.receiveAllMap<Payload<List<DigitalSignatureAndMetadata>>>(sessions.toSet())
        } catch (e: CordaRuntimeException) {
            log.warn(
                "Failed to receive signatures from ${
                    sessions.map { it.counterparty }.joinToString("|")
                } for transaction ${signedTransaction.id}"
            )
            throw e
        }

        /*REVERT*/log.info( "Processing other parties signature payloads" )
        var signedByParticipantsTransaction = signedTransaction
        val signaturesReceivedBySessions: MutableMap<FlowSession, List<DigitalSignatureAndMetadata>> = mutableMapOf()
        signaturesPayloads.forEach { (session, signaturesPayload) ->
            signaturesReceivedBySessions[session] = signaturesPayload.getOrThrow { failure ->
                val message = "Failed to receive signature from ${session.counterparty} for transaction " +
                        "${signedTransaction.id} with message: ${failure.message}"
                /*REVERT*/log.info( message )
                CordaRuntimeException(message)
            }

            /*REVERT*/log.info( "Received signatures from ${session.counterparty} for transaction ${signedTransaction.id}" )

            signaturesReceivedBySessions[session]!!.forEach { signature ->
                try {
                    transactionSignatureService.verifySignature(signedTransaction.id, signature)
                    /*REVERT*/log.info(
                        "Successfully verified signature from ${session.counterparty} of $signature for transaction " +
                                "${signedTransaction.id}"
                    )
                } catch (e: Exception) {
                    log.warn(
                        "Failed to verify signature from ${session.counterparty} of $signature for transaction " +
                                "${signedTransaction.id}. Message: ${e.message}"
                    )

                    throw e
                }
                signedByParticipantsTransaction =
                    signedByParticipantsTransaction.addSignature(signature)
                /*REVERT*/log.info( "Added signature($signature) from ${session.counterparty} for transaction ${signedTransaction.id}" )
            }
        }

        /*REVERT*/log.info( "Verifying all signatures and whether there are any missing ones." )
        signedByParticipantsTransaction.verifySignatures()
        persistenceService.persist(signedByParticipantsTransaction, TransactionStatus.UNVERIFIED)
        /*REVERT*/log.info( "Recorded transaction with all other parties's signatures ${signedTransaction.id}" )

        // Distribute new signatures
        flowMessaging.sendAllMap(sessions.associateWith {session ->
            signedByParticipantsTransaction.signatures.filter {
                it !in signedTransaction.signatures &&              // These have already been distributed with the first go
                it !in signaturesReceivedBySessions[session]!!      // These came from that party
            }
        })

        // TODO Notarisation
        // TODO Verify Notary signature
        persistenceService.persist(signedByParticipantsTransaction, TransactionStatus.VERIFIED)
        /*REVERT*/log.info( "Recorded verified (notarised) transaction ${signedTransaction.id}" )

        // Distribute notary signatures  - TODO
        flowMessaging.sendAll(listOf<List<DigitalSignatureAndMetadata>>(), sessions.toSet())

        // TODO Remove this?
        if (sessions.isNotEmpty()) {
            /*REVERT*/log.info( "All sessions received and acknowledged storage of transaction ${signedTransaction.id}" )
        }

        return signedByParticipantsTransaction
    }
}

// todo: move this
// receiveAll does not return the sessions, so we cannot use session.counterparty
// receiveAllMap needs the casts.
@Suspendable
inline fun <reified R : Any> FlowMessaging.receiveAllMap(sessions: Set<FlowSession>): Map<FlowSession, R> {
    return uncheckedCast(receiveAllMap(sessions.associateWith { R::class.java }))
}