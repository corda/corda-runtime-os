package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.v1

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.flow.impl.flows.backchain.dependencies
import net.corda.ledger.utxo.flow.impl.flows.finality.getVisibleStateIndexes
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.common.TransactionDependencyResolutionFlow
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.common.UtxoTransactionPayload
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerificationService
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.VisibilityChecker
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.slf4j.LoggerFactory

@CordaSystemFlow
class ReceiveSignedTransactionFlowV1(
    private val session: FlowSession
) : SubFlow<UtxoSignedTransaction> {

    private companion object {
        val log = LoggerFactory.getLogger(ReceiveSignedTransactionFlowV1::class.java)
    }

    @CordaInject
    lateinit var transactionVerificationService: UtxoLedgerTransactionVerificationService

    @CordaInject
    lateinit var visibilityChecker: VisibilityChecker

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var ledgerPersistenceService: UtxoLedgerPersistenceService

    @Suspendable
    override fun call(): UtxoSignedTransaction {
        @Suppress("unchecked_cast")
        val transactionPayload = session.receive(UtxoTransactionPayload::class.java)
            as UtxoTransactionPayload<UtxoSignedTransactionInternal>

        val receivedTransaction = transactionPayload.transaction

        requireNotNull(receivedTransaction) {
            "Didn't receive a transaction from counterparty."
        }

        flowEngine.subFlow(
            TransactionDependencyResolutionFlow(
                session,
                receivedTransaction.id,
                receivedTransaction.notaryName,
                receivedTransaction.dependencies,
                transactionPayload.filteredDependencies
            )
        )

        try {
            receivedTransaction.verifySignatorySignatures()
            receivedTransaction.verifyAttachedNotarySignature()
            transactionVerificationService.verify(receivedTransaction.toLedgerTransaction())
        } catch (e: Exception) {
            val message = "Failed to verify transaction and signatures of transaction: ${receivedTransaction.id}"
            log.warn(message, e)
            session.send(Payload.Failure<List<DigitalSignatureAndMetadata>>(message))
            throw CordaRuntimeException(message, e)
        }

        ledgerPersistenceService.persist(
            receivedTransaction,
            TransactionStatus.VERIFIED,
            receivedTransaction.getVisibleStateIndexes(visibilityChecker)
        )
        session.send(Payload.Success(Unit))

        return receivedTransaction
    }
}
