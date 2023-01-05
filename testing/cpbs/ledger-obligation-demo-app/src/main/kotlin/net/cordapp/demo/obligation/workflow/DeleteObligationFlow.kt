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
import net.cordapp.demo.obligation.messages.DeleteObligationRequestMessage
import net.cordapp.demo.obligation.messages.DeleteObligationResponseMessage
import java.time.Instant

class DeleteObligationFlow(
    private val obligation: StateAndRef<ObligationState>, private val sessions: Set<FlowSession>
) : SubFlow<UtxoSignedTransaction> {

    internal companion object {
        const val FLOW_PROTOCOL = "delete-obligation-flow"
        val log = contextLogger()
    }

    @CordaInject
    private lateinit var utxoLedgerService: UtxoLedgerService

    @CordaInject
    private lateinit var memberLookup: MemberLookup

    @Suspendable
    override fun call(): UtxoSignedTransaction {

        val transaction =
            utxoLedgerService.getTransactionBuilder()
                .setNotary(obligation.state.notary)
                .addInputState(obligation.ref)
                .addCommand(ObligationContract.Delete())
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(1.days.toMillis()))
                .addSignatories(listOf(obligation.state.contractState.issuer, obligation.state.contractState.holder))

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
            log.info("DeleteObligationFlow: starting.")

            val request = requestBody.getRequestBodyAs<DeleteObligationRequestMessage>(jsonMarshallingService)

            val oldObligation: StateAndRef<ObligationState> =
                utxoLedgerService.findUnconsumedStatesByType(ObligationState::class.java)
                    .first { it.state.contractState.id == request.id }

            val issuer = requireNotNull(memberLookup.lookup(oldObligation.state.contractState.issuer)) {
                "Unknown issuer: ${oldObligation.state.contractState.issuer}."
            }

            val holder = requireNotNull(memberLookup.lookup(oldObligation.state.contractState.holder)) {
                "Unknown holder: ${oldObligation.state.contractState.holder}."
            }

            // Since both parties can start this flow, we initiate the session with our counterparty.
            val myInfo = memberLookup.myInfo()
            val counterParty = listOf(issuer, holder).single { it != myInfo }
            val sessions = flowMessaging.initiateFlows(counterParty)

            val deleteObligationFlow = DeleteObligationFlow(oldObligation, sessions)

            val transaction = flowEngine.subFlow(deleteObligationFlow)

            val response = DeleteObligationResponseMessage(transaction.id.toString())

            log.info("DeleteObligationFlow: finishing.")
            return jsonMarshallingService.format(response)
        }
    }
}
