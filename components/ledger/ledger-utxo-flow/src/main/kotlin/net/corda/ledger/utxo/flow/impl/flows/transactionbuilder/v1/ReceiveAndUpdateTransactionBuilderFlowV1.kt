package net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.v1

import net.corda.ledger.utxo.data.transaction.verifyFilteredTransactionAndSignatures
import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainResolutionFlow
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
import net.corda.v5.ledger.utxo.transaction.filtered.UtxoFilteredTransactionAndSignatures
import net.corda.v5.membership.GroupParametersLookup
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CordaSystemFlow
class ReceiveAndUpdateTransactionBuilderFlowV1(
    private val session: FlowSession,
    private val originalTransactionBuilder: UtxoTransactionBuilderInternal,
    private val notaryLookup: NotaryLookup,
    private val groupParametersLookup: GroupParametersLookup,
    private val notarySignatureVerificationService: NotarySignatureVerificationService
) : SubFlow<UtxoTransactionBuilder> {

    @CordaInject
    lateinit var flowEngine: FlowEngine

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

        val notaryInfo = updatedTransactionBuilder.notaryName?.let {
            notaryLookup.lookup(it)
        }

        val newTransactionIds = receivedTransactionBuilder.dependencies

        if (notaryInfo == null || notaryInfo.isBackchainRequired) {
            if (newTransactionIds.isEmpty()) {
                log.trace { "There are no new states transferred, therefore no backchains need to be resolved." }
            } else {
                flowEngine.subFlow(TransactionBackchainResolutionFlow(newTransactionIds, session))
            }
        } else {
            val receivedFilteredTransactions = session.receive(List::class.java)
                .filterIsInstance<UtxoFilteredTransactionAndSignatures>()

            require(receivedFilteredTransactions.size == newTransactionIds.size) {
                "The number of filtered transactions received didn't match the number of dependencies."
            }

            val groupParameters = groupParametersLookup.currentGroupParameters
            val notary = requireNotNull(groupParameters.notaries.first { it.name == updatedTransactionBuilder.notaryName }) {
                "Notary from initial transaction \"${updatedTransactionBuilder.notaryName}\" " +
                        "cannot be found in group parameter notaries."
            }

            receivedFilteredTransactions.forEach {
                it.verifyFilteredTransactionAndSignatures(notary, notarySignatureVerificationService)
            }

            // TODO Store filtered transactions?
        }

        return updatedTransactionBuilder
    }
}
