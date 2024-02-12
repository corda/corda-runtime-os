package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.v1

import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.data.transaction.verifyFilteredTransactionAndSignatures
import net.corda.ledger.utxo.flow.impl.flows.backchain.InvalidBackchainException
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainResolutionFlow
import net.corda.ledger.utxo.flow.impl.flows.backchain.dependencies
import net.corda.ledger.utxo.flow.impl.flows.common.TransactionAndFilteredDependencyPayload
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
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.NotarySignatureVerificationService
import net.corda.v5.ledger.utxo.VisibilityChecker
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.membership.GroupParametersLookup
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

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var groupParametersLookup: GroupParametersLookup

    @CordaInject
    lateinit var notarySignatureVerificationService: NotarySignatureVerificationService

    @Suspendable
    override fun call(): UtxoSignedTransaction {

        @Suppress("unchecked_cast")
        val transactionPayload = session.receive(TransactionAndFilteredDependencyPayload::class.java)
                as TransactionAndFilteredDependencyPayload<UtxoSignedTransactionInternal>

        val receivedTransaction = transactionPayload.transaction

        requireNotNull(receivedTransaction) {
            "Didn't receive a transaction from counterparty."
        }

        val transactionDependencies = receivedTransaction.dependencies
        val filteredDependencies = transactionPayload.filteredDependencies

        if (transactionDependencies.isNotEmpty()) {
            if (!filteredDependencies.isNullOrEmpty()) {
                try {

                    // If we have filtered dependencies then we need to perform filtered transaction verification
                    require(filteredDependencies.size == transactionDependencies.size) {
                        "The number of filtered transactions received didn't match the number of dependencies."
                    }

                    val groupParameters = groupParametersLookup.currentGroupParameters
                    val notary =
                        requireNotNull(groupParameters.notaries.firstOrNull { it.name == receivedTransaction.notaryName }) {
                            "Notary from initial transaction \"${receivedTransaction.notaryName}\" " +
                                    "cannot be found in group parameter notaries."
                        }

                    // Verify the received filtered transactions
                    filteredDependencies.forEach {
                        it.verifyFilteredTransactionAndSignatures(notary, notarySignatureVerificationService)
                    }

                    // Persist the verified filtered transactions
                    ledgerPersistenceService.persistFilteredTransactionsAndSignatures(filteredDependencies)
                } catch (e: Exception) {

                }
            }
        } else {
            // If we have no filtered dependencies then we need to perform backchain resolution
            try {
                flowEngine.subFlow(TransactionBackchainResolutionFlow(transactionDependencies, session))
            } catch (e: InvalidBackchainException) {
                val message = "Invalid transaction: ${receivedTransaction.id} found during back-chain resolution."
                log.warn(message, e)
                session.send(Payload.Failure<List<DigitalSignatureAndMetadata>>(message))
                throw e
            }
        }

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
        session.send(Payload.Success("Successfully received transaction."))

        return receivedTransaction
    }
}