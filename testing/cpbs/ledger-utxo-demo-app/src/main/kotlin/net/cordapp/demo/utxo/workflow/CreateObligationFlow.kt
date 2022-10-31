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
import net.corda.v5.base.util.contextLogger
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.cordapp.demo.utxo.contract.ObligationContract
import net.cordapp.demo.utxo.contract.ObligationState
import net.cordapp.demo.utxo.messages.CreateObligationRequestMessage
import net.cordapp.demo.utxo.messages.CreateObligationResponseMessage

class CreateObligationFlow(
    private val state: ObligationState,
    private val notary: Party,
    private val sessions: Set<FlowSession>
) : SubFlow<UtxoSignedTransaction> {

    private companion object {
        val logger = contextLogger()
    }

    @CordaInject
    private lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(): UtxoSignedTransaction {
        logger.info("Initializing test UTXO create flow")

        val transaction = utxoLedgerService.getTransactionBuilder().apply {

            // TODO : Oops! There is no mechanism to set the notary.
            // setNotary(notary)

            addOutputState(state)
            addCommand(ObligationContract.Create)
            addSignatories(listOf(state.issuer)) // We really should have a vararg option here!
        }

        // TODO : How are we handling finality?

        return transaction.sign()
    }

    @InitiatingFlow("utxo-create-flow")
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

            val issuer = memberLookup.lookup(request.issuer)?.ledgerKeys?.first()
                ?: throw IllegalArgumentException("Unknown issuer: ${request.issuer}.")

            val holder = memberLookup.lookup(request.holder)?.ledgerKeys?.first()
                ?: throw IllegalArgumentException("Unknown holder: ${request.issuer}.")

            val notary = memberLookup.lookup()

            val state = ObligationState(issuer, holder, request.amount)

            val transaction = flowEngine.subFlow(CreateObligationFlow(state, notary, sessions))

            val response = CreateObligationResponseMessage(transaction.id)

            return jsonService.format(response)
        }
    }
}