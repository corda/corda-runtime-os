package net.cordapp.demo.utxo.workflow

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

class DeleteObligationFlowHandler(private val session: FlowSession) : SubFlow<UtxoSignedTransaction> {

    @Suspendable
    override fun call(): UtxoSignedTransaction {
        val partiallySignedTransaction = session.receive(UtxoSignedTransaction::class.java)

        // TODO : We need to sign it, but for now, just send it back.
        session.send(partiallySignedTransaction)

        return session.receive(UtxoSignedTransaction::class.java)
    }

    @InitiatedBy(DeleteObligationFlow.FLOW_PROTOCOL)
    private class Handler : ResponderFlow {

        @CordaInject
        private lateinit var flowEngine: FlowEngine

        @Suspendable
        override fun call(session: FlowSession) {

            val updateObligationFlowHandler = DeleteObligationFlowHandler(session)

            flowEngine.subFlow(updateObligationFlowHandler)
        }
    }
}
