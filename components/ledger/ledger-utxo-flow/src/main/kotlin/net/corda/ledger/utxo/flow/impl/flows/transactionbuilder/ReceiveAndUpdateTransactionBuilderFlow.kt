package net.corda.ledger.utxo.flow.impl.flows.transactionbuilder

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
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CordaSystemFlow
class ReceiveAndUpdateTransactionBuilderFlow(
    private val session: FlowSession,
    private val originalTransactionBuilder: UtxoTransactionBuilderInternal
) : SubFlow<UtxoTransactionBuilder> {

    @CordaInject
    lateinit var flowEngine: FlowEngine

    private companion object {
        val log: Logger = LoggerFactory.getLogger(ReceiveAndUpdateTransactionBuilderFlow::class.java)
    }

    @Suspendable
    override fun call(): UtxoTransactionBuilder {
        log.trace { "Starting receive and update transaction builder flow" }

        log.trace { "Waiting for transaction builder proposal from ${session.counterparty}." }
        val receivedTransactionBuilder = session.receive(UtxoTransactionBuilderContainer::class.java)

        val updatedTransactionBuilder = originalTransactionBuilder.append(receivedTransactionBuilder)

        log.trace { "Transaction builder proposals have been applied. Result: $updatedTransactionBuilder" }

        val newTransactionIds = receivedTransactionBuilder.dependencies

        if (newTransactionIds.isEmpty()) {
            log.trace { "There are no new states transferred, therefore no backchains need to be resolved." }
        } else {
            flowEngine.subFlow(TransactionBackchainResolutionFlow(newTransactionIds, session))
        }

        return updatedTransactionBuilder
    }
}
