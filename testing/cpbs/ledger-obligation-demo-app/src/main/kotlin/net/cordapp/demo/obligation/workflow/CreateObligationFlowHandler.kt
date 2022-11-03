package net.cordapp.demo.utxo.workflow

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

class CreateObligationFlowHandler(private val session: FlowSession) : SubFlow<UtxoSignedTransaction> {

    @Suspendable
    override fun call(): UtxoSignedTransaction {
        return session.receive(UtxoSignedTransaction::class.java)
    }

    @InitiatedBy(CreateObligationFlow.FLOW_PROTOCOL)
    private class Handler : ResponderFlow {

        @CordaInject
        private lateinit var flowEngine: FlowEngine

        @Suspendable
        override fun call(session: FlowSession) {

            val createObligationFlowHandler = CreateObligationFlowHandler(session)

            flowEngine.subFlow(createObligationFlowHandler)
        }
    }
}
