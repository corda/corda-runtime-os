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
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.cordapp.demo.utxo.contract.ObligationContract
import net.cordapp.demo.utxo.contract.ObligationState
import net.cordapp.demo.utxo.getParty
import net.cordapp.demo.utxo.initiateFlows
import net.cordapp.demo.utxo.messages.CreateObligationRequestMessage
import net.cordapp.demo.utxo.messages.CreateObligationResponseMessage

class CreateObligationFlow(
    private val obligation: ObligationState,
    private val notary: Party,
    private val sessions: Set<FlowSession>
) : SubFlow<UtxoSignedTransaction> {

    internal companion object {
        const val FLOW_PROTOCOL = "create-obligation-flow"
    }

    @CordaInject
    private lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(): UtxoSignedTransaction {

        val transaction = utxoLedgerService
            .getTransactionBuilder()
            .setNotary(notary)
            .addOutputState(obligation)
            .addCommand(ObligationContract.Create)
            .addSignatories(listOf(obligation.issuer))

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

            val request = requestBody.getRequestBodyAs(jsonService, CreateObligationRequestMessage::class.java)

            val issuer = memberLookup.lookup(request.issuer)
                ?: throw IllegalArgumentException("Unknown issuer: ${request.issuer}.")

            val holder = memberLookup.lookup(request.holder)
                ?: throw IllegalArgumentException("Unknown holder: ${request.holder}.")

            val notary = memberLookup.lookup(request.notary)?.getParty()
                ?: throw IllegalArgumentException("Unknown notary: ${request.notary}.")

            val sessions = flowMessaging.initiateFlows(holder)

            val issuerKey = issuer.ledgerKeys.first()

            val holderKey = holder.ledgerKeys.first()

            val obligationState = ObligationState(issuerKey, holderKey, request.amount)

            val createObligationFlow = CreateObligationFlow(obligationState, notary, sessions)

            val transaction = flowEngine.subFlow(createObligationFlow)

            val response = CreateObligationResponseMessage(transaction.id)

            return jsonService.format(response)
        }
    }
}
