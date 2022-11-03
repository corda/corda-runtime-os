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
import net.cordapp.demo.utxo.messages.UpdateObligationRequestMessage
import net.cordapp.demo.utxo.messages.UpdateObligationResponseMessage

class UpdateObligationFlow(
    private val oldObligation: StateAndRef<ObligationState>,
    private val newObligation: ObligationState,
    private val sessions: Set<FlowSession>
) : SubFlow<UtxoSignedTransaction> {

    internal companion object {
        const val FLOW_PROTOCOL = "update-obligation-flow"
    }

    @CordaInject
    private lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(): UtxoSignedTransaction {

        val transaction = utxoLedgerService
            .getTransactionBuilder()
            .setNotary(oldObligation.state.notary)
            .addInputState(oldObligation)
            .addOutputState(newObligation)
            .addCommand(ObligationContract.Update)
            .addSignatories(listOf(newObligation.holder))

        val fullySignedTransaction = transaction.sign()

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

            val request = requestBody.getRequestBodyAs(jsonService, UpdateObligationRequestMessage::class.java)

            val oldObligation: StateAndRef<ObligationState> = TODO("Requires vault lookup mechanism.")

            val newObligation = oldObligation.state.contractState.settle(request.amountToSettle)

            val issuer = memberLookup.lookup(newObligation.issuer)
                ?: throw IllegalArgumentException("Unknown issuer: ${newObligation.issuer}.")

            val sessions = flowMessaging.initiateFlows(issuer)

            val updateObligationFlow = UpdateObligationFlow(oldObligation, newObligation, sessions)

            val transaction = flowEngine.subFlow(updateObligationFlow)

            val response = UpdateObligationResponseMessage(transaction.id)

            return jsonService.format(response)
        }
    }
}
