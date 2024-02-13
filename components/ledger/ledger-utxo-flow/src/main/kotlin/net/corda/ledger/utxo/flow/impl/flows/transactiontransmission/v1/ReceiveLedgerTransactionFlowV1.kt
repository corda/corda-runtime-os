package net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.v1

import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.flow.flows.Payload
import net.corda.ledger.utxo.data.transaction.WrappedUtxoWireTransaction
import net.corda.ledger.utxo.data.transaction.verifyFilteredTransactionAndSignatures
import net.corda.ledger.utxo.flow.impl.flows.backchain.InvalidBackchainException
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainResolutionFlow
import net.corda.ledger.utxo.flow.impl.flows.transactiontransmission.common.UtxoTransactionPayload
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.factory.UtxoLedgerTransactionFactory
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.NotarySignatureVerificationService
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.corda.v5.membership.GroupParametersLookup
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CordaSystemFlow
class ReceiveLedgerTransactionFlowV1(
    private val session: FlowSession
) : SubFlow<UtxoLedgerTransaction> {

    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var utxoLedgerTransactionFactory: UtxoLedgerTransactionFactory

    @CordaInject
    lateinit var serializationService: SerializationService

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var groupParametersLookup: GroupParametersLookup

    @CordaInject
    lateinit var notarySignatureVerificationService: NotarySignatureVerificationService

    @CordaInject
    lateinit var ledgerPersistenceService: UtxoLedgerPersistenceService

    @Suspendable
    override fun call(): UtxoLedgerTransaction {
        @Suppress("unchecked_cast")
        val transactionPayload = session.receive(UtxoTransactionPayload::class.java)
                as UtxoTransactionPayload<WireTransaction>

        val receivedTransaction = transactionPayload.transaction

        requireNotNull(receivedTransaction) {
            "Didn't receive a transaction from counterparty."
        }

        val wrappedUtxoWireTransaction = WrappedUtxoWireTransaction(receivedTransaction, serializationService)

        val transactionDependencies = wrappedUtxoWireTransaction.dependencies
        val filteredDependencies = transactionPayload.filteredDependencies

        if (transactionDependencies.isNotEmpty()) {
            if (filteredDependencies.isNullOrEmpty()) {
                // If we have dependencies but no filtered dependencies then we need to perform backchain resolution
                try {
                    flowEngine.subFlow(TransactionBackchainResolutionFlow(transactionDependencies, session))
                } catch (e: InvalidBackchainException) {
                    val message = "Invalid transaction: ${receivedTransaction.id} found during back-chain resolution."
                    log.warn(message, e)
                    session.send(Payload.Failure<List<DigitalSignatureAndMetadata>>(message))
                    throw e
                }
            } else {
                // If we have dependencies and filtered dependencies then we need to perform filtered transaction verification
                require(filteredDependencies.size == transactionDependencies.size) {
                    "The number of filtered transactions received didn't match the number of dependencies."
                }

                val groupParameters = groupParametersLookup.currentGroupParameters
                val notary =
                    requireNotNull(groupParameters.notaries.firstOrNull { it.name == wrappedUtxoWireTransaction.notaryName }) {
                        "Notary from initial transaction \"${wrappedUtxoWireTransaction.notaryName}\" " +
                                "cannot be found in group parameter notaries."
                    }

                // Verify the received filtered transactions
                filteredDependencies.forEach {
                    it.verifyFilteredTransactionAndSignatures(notary, notarySignatureVerificationService)
                }

                // Persist the verified filtered transactions
                ledgerPersistenceService.persistFilteredTransactionsAndSignatures(filteredDependencies)
            }
        }

        session.send(Payload.Success("Successfully received transaction."))

        return utxoLedgerTransactionFactory.create(receivedTransaction)
    }
}

