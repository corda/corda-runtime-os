package net.corda.ledger.utxo.flow.impl.flows.finality

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.common.flow.transaction.TransactionMissingSignaturesException
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

    @Suppress("ComplexMethod")
    @Suspendable
    override fun call(): UtxoSignedTransaction {
        log.trace("Starting finality flow for transaction: $transactionId")
        verifyTransaction(initialTransaction)
        persistUnverifiedTransaction()
        sendTransactionAndBackchain()
        val (transaction, signaturesReceivedFromSessions) = receiveSignaturesAndAddToTransaction()
        verifyAllReceivedSignatures(transaction, signaturesReceivedFromSessions)
        persistTransactionWithCounterpartySignatures(transaction)
        sendUnseenSignaturesToCounterparties(transaction, signaturesReceivedFromSessions)
        val (notarizedTransaction, notarySignatures) = notarize(transaction)
        persistNotarizedTransaction(notarizedTransaction)
        sendNotarySignaturesToCounterparties(notarySignatures)
        log.trace { "Finalisation of transaction $transactionId has been finished." }
        return transaction
    }

    @Suspendable
    private fun persistUnverifiedTransaction() {
        persistenceService.persist(initialTransaction, TransactionStatus.UNVERIFIED)
        log.debug { "Recorded transaction with initial signatures $transactionId" }
    }

    @Suspendable
    private fun sendTransactionAndBackchain() {
        sessions.forEach {
            it.send(initialTransaction)
            flowEngine.subFlow(TransactionBackchainSenderFlow(it))
        }
    }

    @Suspendable
    private fun receiveSignaturesAndAddToTransaction(): Pair<UtxoSignedTransactionInternal, Map<FlowSession, List<DigitalSignatureAndMetadata>>> {
        val signaturesPayloads = sessions.associateWith {
            try {
                log.debug { "Requesting signatures from ${it.counterparty} for transaction $transactionId" }
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
                log.warn(message)
                CordaRuntimeException(message)
            }

            log.debug {
                "Received ${signatures.size} signatures from ${session.counterparty}" +
                        " for transaction $transactionId"
            }

            signatures.forEach { signature ->
                transaction = verifyAndAddSignature(transaction, signature)
            }
            session to signatures
        }.toMap()

        return transaction to signaturesReceivedFromSessions
    }

    private fun verifyAllReceivedSignatures(
        transaction: UtxoSignedTransactionInternal,
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
            throw TransactionMissingSignaturesException(transactionId, e.missingSignatories, message)
        }
    }

    @Suspendable
    private fun persistTransactionWithCounterpartySignatures(transaction: UtxoSignedTransactionInternal) {
        val relevantStatesIndexes = transaction.getRelevantStatesIndexes(memberLookup.getMyLedgerKeys())
        persistenceService.persist(transaction, TransactionStatus.UNVERIFIED, relevantStatesIndexes)
        log.debug { "Recorded transaction with all parties' signatures $transactionId" }
    }

    @Suspendable
    private fun sendUnseenSignaturesToCounterparties(
        transaction: UtxoSignedTransactionInternal,
        signaturesReceivedFromSessions: Map<FlowSession, List<DigitalSignatureAndMetadata>>
    ) {
        val notSeenSignaturesBySessions = signaturesReceivedFromSessions.map { (session, signatures) ->
            session to transaction.signatures.filter {
                it !in initialTransaction.signatures &&             // These have already been distributed with the first go
                it !in signatures                                   // These came from that party
            }
        }.toMap()
        flowMessaging.sendAllMap(notSeenSignaturesBySessions)
    }

    @Suspendable
    private fun notarize(
        transaction: UtxoSignedTransactionInternal
    ): Pair<UtxoSignedTransactionInternal, List<DigitalSignatureAndMetadata>> {
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

        var notarizedTransaction = transaction
        notarySignatures.forEach { signature ->
            notarizedTransaction = try {
                verifyAndAddNotarySignature(transaction, signature)
            } catch (e: Exception) {
                val message = e.message ?: "Notary signature verification failed."
                flowMessaging.sendAll(Payload.Failure<List<DigitalSignatureAndMetadata>>(message), sessions.toSet())
                throw e
            }
        }

        return notarizedTransaction to notarySignatures
    }

    @Suspendable
    private fun persistNotarizedTransaction(transaction: UtxoSignedTransactionInternal) {
        persistenceService.persist(transaction, TransactionStatus.VERIFIED)
        log.debug { "Recorded verified (notarised) transaction $transactionId" }
    }

    @Suspendable
    private fun sendNotarySignaturesToCounterparties(notarySignatures: List<DigitalSignatureAndMetadata>) {
        flowMessaging.sendAll(Payload.Success(notarySignatures), sessions.toSet())
    }
}
