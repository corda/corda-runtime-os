package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.notary.plugin.factory.PluggableNotaryClientFlowFactory
import net.corda.ledger.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerifier
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
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
) : SubFlow<UtxoSignedTransaction>, UtxoFinalityBase() {

    private val transactionId = initialTransaction.id

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var pluggableNotaryClientFlowFactory: PluggableNotaryClientFlowFactory

    @Suspendable
    override fun call(): UtxoSignedTransaction {

        verify(initialTransaction)

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
                "Received ${signaturesReceivedFromSessions[session]!!.size} signatures from ${session.counterparty}" +
                        " for transaction $transactionId"
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

        // We just let the notary exceptions bubble up.
        val notarisationFlow = pluggableNotaryClientFlowFactory.create(transaction.notary, transaction)
        val notarySignatures = flowEngine.subFlow(notarisationFlow)
        if (notarySignatures.isEmpty()){
            throw CordaRuntimeException("Notary has not returned any signatures.")
        }
        notarySignatures.forEach { signature ->
            transaction = verifyAndAddSignature(transaction, signature)
        }

        persistenceService.persist(transaction, TransactionStatus.VERIFIED)
        log.debug("Recorded verified (notarised) transaction $transactionId")

        // Distribute notary signatures
        flowMessaging.sendAll(notarySignatures, sessions.toSet())

        log.trace("Finalisation of transaction $transactionId has been finished.")

        return transaction
    }
    private fun verify(signedTransaction: UtxoSignedTransaction) {
        UtxoLedgerTransactionVerifier(signedTransaction.toLedgerTransaction()).verifyContracts()
    }
}
