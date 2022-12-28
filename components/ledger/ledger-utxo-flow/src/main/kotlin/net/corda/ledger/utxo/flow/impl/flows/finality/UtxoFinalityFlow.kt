package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.notary.plugin.factory.PluggableNotaryClientFlowFactory
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainSenderFlow
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
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
    lateinit var pluggableNotaryClientFlowFactory: PluggableNotaryClientFlowFactory

    @Suspendable
    override fun call(): UtxoSignedTransaction {

        log.trace("Starting finality flow for transaction: $transactionId")
        verifyTransaction(initialTransaction)

        persistenceService.persist(initialTransaction, TransactionStatus.UNVERIFIED)
        log.debug { "Recorded transaction with initial signatures $transactionId" }

        val signaturesPayloads = sessions.associateWith {
            try {
                log.debug { "Requesting signatures from ${it.counterparty} for transaction $transactionId" }
                it.send(initialTransaction)
                flowEngine.subFlow(TransactionBackchainSenderFlow(it))
                it.receive<Payload<List<DigitalSignatureAndMetadata>>>()
            } catch (e: CordaRuntimeException) {
                log.warn("Failed to receive signatures from ${it.counterparty} for transaction $transactionId")
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

            log.debug { "Received ${signatures.size} signatures from ${session.counterparty} for transaction $transactionId" }

            signatures.forEach { signature ->
                transaction = verifyAndAddSignature(transaction, signature)
            }
            session to signatures
        }.toMap()

        log.debug { "Verifying all signatures for transaction $transactionId." }
        transaction.verifySignatures() // TODO CORE-8935 Add better logging if the transaction is not fully signed
        val relevantStatesIndexes = transaction.getRelevantStatesIndexes(memberLookup.getMyLedgerKeys())
        persistenceService.persist(transaction, TransactionStatus.UNVERIFIED, relevantStatesIndexes)
        log.debug { "Recorded transaction with all parties' signatures $transactionId" }

        // Distribute new signatures
        val notSeenSignaturesBySessions = signaturesReceivedFromSessions.map { (session, signatures) ->
            session to transaction.signatures.filter {
                it !in initialTransaction.signatures &&             // These have already been distributed with the first go
                it !in signatures                                   // These came from that party
            }
        }.toMap()
        flowMessaging.sendAllMap(notSeenSignaturesBySessions)

        val notarisationFlow = pluggableNotaryClientFlowFactory.create(transaction.notary, transaction)
        val notarySignatures = try {
            flowEngine.subFlow(notarisationFlow)
        } catch (e: CordaRuntimeException) {
            val message = "Notarization failed with ${e.message}."
            flowMessaging.sendAll(Payload.Failure<List<DigitalSignatureAndMetadata>>(message), sessions.toSet())
            log.warn(message)
            throw e
        }
        if (notarySignatures.isEmpty()) {
            val message = "Notary has not returned any signatures."
            log.warn(message)
            flowMessaging.sendAll(Payload.Failure<List<DigitalSignatureAndMetadata>>(message), sessions.toSet())
            throw CordaRuntimeException(message)
        }
        notarySignatures.forEach { signature ->
            transaction = try {
                verifyAndAddNotarySignature(transaction, signature)
            } catch (e: Exception) {
                val message = e.message ?: "Notary signature verification failed."
                flowMessaging.sendAll(Payload.Failure<List<DigitalSignatureAndMetadata>>(message), sessions.toSet())
                throw e
            }
        }

        persistenceService.persist(transaction, TransactionStatus.VERIFIED)
        log.debug { "Recorded verified (notarised) transaction $transactionId" }

        // Distribute notary signatures
        flowMessaging.sendAll(Payload.Success(notarySignatures), sessions.toSet())

        log.trace { "Finalisation of transaction $transactionId has been finished." }

        return transaction
    }
}
