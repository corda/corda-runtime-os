package net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.v1

import net.corda.ledger.utxo.data.transaction.verifyFilteredTransactionAndSignatures
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainResolutionFlow
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderContainer
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderInternal
import net.corda.sandbox.CordaSystemFlow
import net.corda.utilities.trace
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.NotarySignatureVerificationService
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import net.corda.v5.membership.GroupParametersLookup
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CordaSystemFlow
class ReceiveAndUpdateTransactionBuilderFlowV1(
    private val session: FlowSession,
    private val originalTransactionBuilder: UtxoTransactionBuilderInternal
) : SubFlow<UtxoTransactionBuilder> {

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var groupParametersLookup: GroupParametersLookup

    @CordaInject
    lateinit var notarySignatureVerificationService: NotarySignatureVerificationService

    @CordaInject
    lateinit var persistenceService: UtxoLedgerPersistenceService

    private companion object {
        val log: Logger = LoggerFactory.getLogger(ReceiveAndUpdateTransactionBuilderFlowV1::class.java)
    }

    @Suspendable
    override fun call(): UtxoTransactionBuilder {
        log.trace { "Starting receive and update transaction builder flow" }

        log.trace { "Waiting for transaction builder proposal from ${session.counterparty}." }
        val receivedTransactionBuilder = session.receive(UtxoTransactionBuilderContainer::class.java)

        val updatedTransactionBuilder = originalTransactionBuilder.append(receivedTransactionBuilder)

        log.trace { "Transaction builder proposals have been applied. Result: $updatedTransactionBuilder" }

        val notaryName = requireNotNull(originalTransactionBuilder.notaryName) {
            "Notary name on transaction builder must not be null."
        }

        val notaryInfo = requireNotNull(notaryLookup.lookup(notaryName)) {
            "Could not find notary service with name: $notaryName"
        }

        val newTransactionIds = receivedTransactionBuilder.dependencies

        if (notaryInfo.isBackchainRequired) {
            if (newTransactionIds.isEmpty()) {
                log.trace { "There are no new states transferred, therefore no backchains need to be resolved." }
            } else {
                flowEngine.subFlow(TransactionBackchainResolutionFlow(newTransactionIds, session))
            }
        } else {
            val receivedFilteredTransactions = receivedTransactionBuilder.filteredDependencies
            require(receivedFilteredTransactions.size == newTransactionIds.size) {
                "The number of filtered transactions received didn't match the number of dependencies."
            }

            val groupParameters = groupParametersLookup.currentGroupParameters
            val notary = requireNotNull(groupParameters.notaries.first { it.name == updatedTransactionBuilder.notaryName }) {
                "Notary from initial transaction \"${updatedTransactionBuilder.notaryName}\" " +
                        "cannot be found in group parameter notaries."
            }

            // Verify the received filtered transactions
            receivedFilteredTransactions.forEach {
                it.verifyFilteredTransactionAndSignatures(notary, notarySignatureVerificationService)
            }

            // Persist the verified filtered transactions
            persistenceService.persistFilteredTransactionsAndSignatures(receivedFilteredTransactions)
        }

        return updatedTransactionBuilder
    }
}
