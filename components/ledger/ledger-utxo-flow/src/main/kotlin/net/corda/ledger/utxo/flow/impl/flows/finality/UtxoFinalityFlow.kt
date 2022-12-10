package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.sendAndReceive
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

@CordaSystemFlow
class UtxoFinalityFlow(
    private val initialTransaction: UtxoSignedTransactionInternal,
    private val sessions: List<FlowSession>
) : SubFlow<UtxoSignedTransaction> {

    private val transactionId = initialTransaction.id

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
    lateinit var flowMessaging: FlowMessaging

    @Suspendable
    override fun call(): UtxoSignedTransaction {

        log.trace("Starting finality flow for transaction: $transactionId")
        persistenceService.persist(initialTransaction, TransactionStatus.UNVERIFIED)
        log.debug("Recorded transaction with initial signatures $transactionId")

        val signaturesPayloads = try {
            sessions.associateWith {
                log.debug(
                    "Requesting signatures from ${it.counterparty} for transaction $transactionId"
                )
                it.sendAndReceive<Payload<List<DigitalSignatureAndMetadata>>>(initialTransaction)
            }
        } catch (e: CordaRuntimeException) {
            log.warn(
                "Failed to receive signatures from ${
                    sessions.map { it.counterparty }.joinToString("|")
                } for transaction $transactionId"
            )
            throw e
        }

        log.debug("Processing other parties' signature payloads")
        val signaturesReceivedFromSessions: MutableMap<FlowSession, List<DigitalSignatureAndMetadata>> = mutableMapOf()
        var transaction = initialTransaction
        signaturesPayloads.forEach { (session, signaturesPayload) ->
            signaturesReceivedFromSessions[session] = signaturesPayload.getOrThrow { failure ->
                val message = "Failed to receive signatures from ${session.counterparty} for transaction " +
                        "$transactionId with message: ${failure.message}"
                log.debug("Processed ${session.counterparty}'s reply for transaction $transactionId: $message")
                CordaRuntimeException(message)
            }

            log.debug(
                "Received ${signaturesReceivedFromSessions[session]!!.size} signatures from ${session.counterparty} for transaction $transactionId"
            )
            if (signaturesReceivedFromSessions[session]!!.isEmpty()){   // Q: do we need this check?
                val message = "Received 0 signatures from ${session.counterparty} for transaction $transactionId."
                log.warn(message)
                throw CordaRuntimeException(message)
            }

            signaturesReceivedFromSessions[session]!!.forEach { signature ->
                transaction = verifyAndAddSignature(transaction, signature)
            }
        }

        log.debug("Verifying all signatures for transaction $transactionId.")
        transaction.verifySignatures()
        persistenceService.persist(transaction, TransactionStatus.UNVERIFIED)
        log.debug("Recorded transaction with all parties' signatures $transactionId")

        // Distribute new signatures
        flowMessaging.sendAllMap(sessions.associateWith { session ->
            transaction.signatures.filter {
                it !in initialTransaction.signatures &&             // These have already been distributed with the first go
                it !in signaturesReceivedFromSessions[session]!!    // These came from that party
            }
        })

        // TODO Notarisation
        // TODO Verify Notary signature
        persistenceService.persist(transaction, TransactionStatus.VERIFIED)
        log.debug("Recorded verified (notarised) transaction $transactionId")

        // Distribute notary signatures  - TODO
        flowMessaging.sendAll(listOf<List<DigitalSignatureAndMetadata>>(), sessions.toSet())

        log.trace("Finalisation of transaction $transactionId has been finished.")

        return transaction
    }

    @Suspendable
    private fun verifyAndAddSignature(
        transaction: UtxoSignedTransactionInternal,
        signature: DigitalSignatureAndMetadata
    ):UtxoSignedTransactionInternal {
        try {
            log.debug("Verifying signature($signature) of transaction: $transactionId")
            transactionSignatureService.verifySignature(transactionId, signature)
        } catch (e: Exception) {
            log.warn(
                "Failed to verify transaction's signature($signature) from party: ${signature.by} for transaction " +
                        "$transactionId. Message: ${e.message}"
            )
            throw e
        }
        return transaction.addSignature(signature).also {
            log.debug("Added signature($signature) from ${signature.by} for transaction $transactionId")
        }
    }
}
