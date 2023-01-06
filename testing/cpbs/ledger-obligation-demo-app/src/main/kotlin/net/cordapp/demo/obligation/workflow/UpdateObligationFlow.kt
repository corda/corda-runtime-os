package net.cordapp.demo.obligation.workflow

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.days
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.cordapp.demo.obligation.contract.ObligationContract
import net.cordapp.demo.obligation.contract.ObligationState
import net.cordapp.demo.obligation.initiateFlows
import net.cordapp.demo.obligation.messages.UpdateObligationRequestMessage
import net.cordapp.demo.obligation.messages.UpdateObligationResponseMessage
import java.time.Instant

class UpdateObligationFlow(
    private val oldObligation: StateAndRef<ObligationState>,
    private val newObligation: ObligationState,
    private val sessions: Set<FlowSession>
) : SubFlow<UtxoSignedTransaction> {

    internal companion object {
        const val FLOW_PROTOCOL = "update-obligation-flow"
        val log = contextLogger()
    }

    @CordaInject
    private lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    private lateinit var memberLookup: MemberLookup

    @Suspendable
    override fun call(): UtxoSignedTransaction {

        val transaction = utxoLedgerService
            .getTransactionBuilder()
            .setNotary(oldObligation.state.notary)
            .addInputState(oldObligation.ref)
            .addOutputState(newObligation)
            .addCommand(ObligationContract.Update())
            .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(1.days.toMillis()))
            .addSignatories(listOf(newObligation.holder))

        @Suppress("DEPRECATION")
        val initiallySignedTransaction = transaction.toSignedTransaction(memberLookup.myInfo().ledgerKeys.first())

        val fullySignedSignedTransaction = utxoLedgerService.finalize(
            initiallySignedTransaction,
            sessions.toList()
        )

        return fullySignedSignedTransaction
    }

    @InitiatingFlow(FLOW_PROTOCOL)
    class Initiator : RPCStartableFlow {

        @CordaInject
        private lateinit var flowEngine: FlowEngine

        @CordaInject
        private lateinit var flowMessaging: FlowMessaging

        @CordaInject
        private lateinit var jsonMarshallingService: JsonMarshallingService

        @CordaInject
        private lateinit var memberLookup: MemberLookup

        @CordaInject
        private lateinit var utxoLedgerService: UtxoLedgerService

        @Suspendable
        override fun call(requestBody: RPCRequestData): String {
            log.info("UpdateObligationFlow: starting.")

            val request = requestBody.getRequestBodyAs<UpdateObligationRequestMessage>(jsonMarshallingService)

            val oldObligation: StateAndRef<ObligationState> =
                utxoLedgerService.findUnconsumedStatesByType(ObligationState::class.java)
                    .firstOrNull { it.state.contractState.id == request.id }
                    ?: throw IllegalArgumentException("Obligation not found: ${request.id}.")

            val newObligation = oldObligation.state.contractState.settle(request.amountToSettle)

            val issuer =
                requireNotNull(memberLookup.lookup(newObligation.issuer)) { "Unknown issuer: ${newObligation.issuer}." }

            val sessions = flowMessaging.initiateFlows(issuer)

            val transaction = flowEngine.subFlow(UpdateObligationFlow(oldObligation, newObligation, sessions))

            if(request.doubleSpend) {
                val anotherSessions = flowMessaging.initiateFlows(issuer)
                val anotherNewObligation = oldObligation.state.contractState.settle(request.amountToSettle)

                flowEngine.subFlow(UpdateObligationFlow(oldObligation, anotherNewObligation, anotherSessions))
            }

            val response = UpdateObligationResponseMessage(transaction.id.toString())

            log.info("UpdateObligationFlow: finishing.")
            return jsonMarshallingService.format(response)
        }
    }
}
