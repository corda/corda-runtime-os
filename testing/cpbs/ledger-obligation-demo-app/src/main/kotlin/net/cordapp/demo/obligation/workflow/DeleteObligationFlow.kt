package net.cordapp.demo.utxo.workflow

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.cordapp.demo.utxo.contract.ObligationContract
import net.cordapp.demo.utxo.contract.ObligationState
import net.cordapp.demo.utxo.initiateFlows
import net.cordapp.demo.utxo.messages.DeleteObligationRequestMessage
import net.cordapp.demo.utxo.messages.DeleteObligationResponseMessage

class DeleteObligationFlow(
    private val obligation: StateAndRef<ObligationState>, private val sessions: Set<FlowSession>
) : SubFlow<UtxoSignedTransaction> {

    internal companion object {
        const val FLOW_PROTOCOL = "delete-obligation-flow"
    }

    @CordaInject
    private lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(): UtxoSignedTransaction {

        val transaction =
            utxoLedgerService.getTransactionBuilder().setNotary(obligation.state.notary).addInputState(obligation)
                .addCommand(ObligationContract.Delete)
                .addSignatories(listOf(obligation.state.contractState.issuer, obligation.state.contractState.holder))

        val partiallySignedTransaction = transaction.sign()

        // TODO : For now, just send them the partially signed transaction. We'll add counter-signing later.
        val fullySignedTransaction =
            sessions.map { it.sendAndReceive(UtxoSignedTransaction::class.java, partiallySignedTransaction) }.last()

        // TODO : For now, just send them the signed transaction. We'll add finality later.
        sessions.forEach { it.send(fullySignedTransaction) }

        return fullySignedTransaction
    }

    @InitiatingFlow(FLOW_PROTOCOL)
    class Initiator : RPCStartableFlow {

        @CordaInject
        private lateinit var flowEngine: FlowEngine

        @CordaInject
        private lateinit var flowMessaging: FlowMessaging

        @CordaInject
        private lateinit var jsonService: JsonMarshallingService

        @CordaInject
        private lateinit var memberLookup: MemberLookup

        @Suspendable
        override fun call(requestBody: RPCRequestData): String {

            val request = requestBody.getRequestBodyAs(jsonService, DeleteObligationRequestMessage::class.java)

            val oldObligation: StateAndRef<ObligationState> = TODO("Requires vault lookup mechanism.")

            val issuer = memberLookup.lookup(oldObligation.state.contractState.issuer)
                ?: throw IllegalArgumentException("Unknown issuer: ${oldObligation.state.contractState.issuer}.")

            // TODO : If this flow can be called by either party, we need the flow session for the counter-party.
            val sessions = flowMessaging.initiateFlows(issuer)

            val deleteObligationFlow = DeleteObligationFlow(oldObligation, sessions)

            val transaction = flowEngine.subFlow(deleteObligationFlow)

            val response = DeleteObligationResponseMessage(transaction.id)

            return jsonService.format(response)
        }
    }
}
