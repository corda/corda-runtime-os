package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.v1

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.flow.impl.flows.backchain.InvalidBackchainException
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainResolutionFlow
import net.corda.ledger.utxo.flow.impl.flows.backchain.dependencies
import net.corda.ledger.utxo.flow.impl.flows.finality.getVisibleStateIndexes
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoSignedTransactionInternal
import net.corda.ledger.utxo.flow.impl.transaction.verifier.UtxoLedgerTransactionVerificationService
import net.corda.sandbox.CordaSystemFlow
import net.corda.utilities.trace
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.VisibilityChecker
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CordaSystemFlow
class ReceiveTransactionFlowV1(
    private val session: FlowSession
) : SubFlow<UtxoSignedTransaction> {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(ReceiveTransactionFlowV1::class.java)
    }

    @CordaInject
    lateinit var transactionVerificationService: UtxoLedgerTransactionVerificationService

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var ledgerPersistenceService: UtxoLedgerPersistenceService

    @CordaInject
    lateinit var visibilityChecker: VisibilityChecker

    @Suspendable
    override fun call(): UtxoSignedTransaction {
        val transaction = session.receive(UtxoSignedTransactionInternal::class.java)

        val transactionDependencies = transaction.dependencies
        if (transactionDependencies.isNotEmpty()) {
            try {
                flowEngine.subFlow(TransactionBackchainResolutionFlow(transactionDependencies, session))
            } catch (e: InvalidBackchainException) {
                val message = "Invalid transaction: ${transaction.id} found during back-chain resolution."
                log.warn(message, e)
                session.send(Payload.Failure<List<DigitalSignatureAndMetadata>>(message))
                throw e
            }
        } else {
            log.trace {
                "Transaction with id ${transaction.id} has no dependencies so backchain resolution will not be performed."
            }
        }

        try {
            transaction.verifySignatorySignatures()
            transaction.verifyAttachedNotarySignature()
            transactionVerificationService.verify(transaction.toLedgerTransaction())
        } catch (e: Exception) {
            val message = "Failed to verify transaction and signatures of transaction: ${transaction.id}"
            log.warn(message, e)
            session.send(Payload.Failure<List<DigitalSignatureAndMetadata>>(message))
            throw CordaRuntimeException(message, e)
        }

        ledgerPersistenceService.persist(transaction, TransactionStatus.VERIFIED, transaction.getVisibleStateIndexes(visibilityChecker))
        session.send(Payload.Success("Successfully received transaction."))

        return transaction
    }
}