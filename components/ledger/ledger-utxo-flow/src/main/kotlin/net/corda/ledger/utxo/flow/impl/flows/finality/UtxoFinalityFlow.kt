package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.common.flow.transaction.TransactionSignatureService
import net.corda.ledger.notary.plugin.factory.PluggableNotaryClientFlowFactory
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainSenderFlow
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoLedgerTransactionVerifier
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
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
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

        log.trace("Starting finality flow for transaction: $transactionId")
        persistenceService.persist(initialTransaction, TransactionStatus.UNVERIFIED)
        log.debug { "Recorded transaction with initial signatures $transactionId" }

        val signaturesPayloads = sessions.associateWith {
            try {
                log.debug {
                    "Requesting signatures from ${it.counterparty} for transaction $transactionId"
                }
                it.send(initialTransaction)
                flowEngine.subFlow(TransactionBackchainSenderFlow(session))
                it.receive<Payload<List<DigitalSignatureAndMetadata>>>()
            } catch (e: CordaRuntimeException) {
                log.warn(
                    "Failed to receive signatures from ${it.counterparty} for transaction $transactionId"
                )
                throw e
            }
        }

        var transaction = initialTransaction
        val signaturesReceivedFromSessions = signaturesPayloads.map { (session, signaturesPayload) ->
            val signatures = signaturesPayload.getOrThrow { failure ->
                val message = "Failed to receive signatures from ${session.counterparty} for transaction " +
                        "$transactionId with message: ${failure.message}"
                log.debug { message }
                CordaRuntimeException(message)
            }

            log.debug {
                "Received ${signatures.size} signatures from ${session.counterparty}" +
                        " for transaction $transactionId"
            }
            if (signatures.isEmpty()) {   // Q: do we need this check?
                val message = "Received 0 signatures from ${session.counterparty} for transaction $transactionId."
                log.warn(message)
                throw CordaRuntimeException(message)
            }

            signatures.forEach { signature ->
                transaction = verifyAndAddSignature(transaction, signature)
            }
            session to signatures
        }.toMap()

        log.debug { "Verifying all signatures for transaction $transactionId." }
        transaction.verifySignatures()
        persistenceService.persist(transaction, TransactionStatus.UNVERIFIED)
        log.debug { "Recorded transaction with all parties' signatures $transactionId" }

        // Distribute new signatures
        val notSeenSignaturesBySessions = signaturesReceivedFromSessions.map { (session, signatures) ->
            session to transaction.signatures.filter {
                it !in initialTransaction.signatures &&             // These have already been distributed with the first go
                it !in signatures                                   // These came from that party
            }
        }.toMap()
        flowMessaging.sendAllMap(notSeenSignaturesBySessions)

        // We just let the notary exceptions bubble up.
        val notarisationFlow = pluggableNotaryClientFlowFactory.create(transaction.notary, transaction)
        val notarySignatures = flowEngine.subFlow(notarisationFlow)
        if (notarySignatures.isEmpty()) {
            throw CordaRuntimeException("Notary has not returned any signatures.")
        }
        notarySignatures.forEach { signature ->
            transaction = verifyAndAddSignature(transaction, signature)
        }

        persistenceService.persist(transaction, TransactionStatus.VERIFIED)
        log.debug { "Recorded verified (notarised) transaction $transactionId" }

        // Distribute notary signatures
        flowMessaging.sendAll(notarySignatures, sessions.toSet())

        log.trace { "Finalisation of transaction $transactionId has been finished." }

        return transaction
    }
}
