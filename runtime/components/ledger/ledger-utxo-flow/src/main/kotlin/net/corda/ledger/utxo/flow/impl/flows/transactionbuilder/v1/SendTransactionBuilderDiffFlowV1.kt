package net.corda.ledger.utxo.flow.impl.flows.transactionbuilder.v1

import net.corda.ledger.utxo.flow.impl.flows.backchain.TransactionBackchainSenderFlow
import net.corda.ledger.utxo.flow.impl.transaction.UtxoTransactionBuilderContainer
import net.corda.sandbox.CordaSystemFlow
import net.corda.utilities.trace
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@CordaSystemFlow
class SendTransactionBuilderDiffFlowV1(
    private val transactionBuilder: UtxoTransactionBuilderContainer,
    private val session: FlowSession
) : SubFlow<Unit> {

    @CordaInject
    lateinit var flowEngine: FlowEngine

    private companion object {
        val log: Logger = LoggerFactory.getLogger(SendTransactionBuilderDiffFlowV1::class.java)
    }

    @Suspendable
    override fun call() {
        log.trace { "Starting send transaction builder flow." }

        log.trace { "Sending proposed transaction builder components to ${session.counterparty}." }
        session.send(transactionBuilder)

        val newTransactionIds = transactionBuilder.dependencies

        if (newTransactionIds.isEmpty()) {
            log.trace { "There are no new states transferred, therefore no backchains need to be resolved." }
        } else {
            flowEngine.subFlow(TransactionBackchainSenderFlow(newTransactionIds, session))
        }
    }
}
