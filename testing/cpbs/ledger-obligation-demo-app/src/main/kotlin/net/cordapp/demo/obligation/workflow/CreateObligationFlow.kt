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
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.cordapp.demo.obligation.contract.ObligationContract
import net.cordapp.demo.obligation.contract.ObligationState
import net.cordapp.demo.obligation.initiateFlows
import net.cordapp.demo.obligation.messages.CreateObligationRequestMessage
import net.cordapp.demo.obligation.messages.CreateObligationResponseMessage
import java.time.Instant

class CreateObligationFlow(
    private val obligation: ObligationState,
    private val notary: Party,
    private val sessions: Set<FlowSession>,
    private val fromDayOffset: Int,
    private val toDayOffset: Int,
) : SubFlow<UtxoSignedTransaction> {

    internal companion object {
        const val FLOW_PROTOCOL = "create-obligation-flow"
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
            .setNotary(notary)
            .addOutputState(obligation)
            .addCommand(ObligationContract.Create)
            .setTimeWindowBetween(
                Instant.now().plusMillis(fromDayOffset.days.toMillis()),
                Instant.now().plusMillis(toDayOffset.days.toMillis())
            )
            .addSignatories(listOf(obligation.issuer))

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

        @Suspendable
        override fun call(requestBody: RPCRequestData): String {
            log.info("CreateObligationFlow: starting.")

            val request = requestBody.getRequestBodyAs<CreateObligationRequestMessage>(jsonMarshallingService)

            val issuer = memberLookup.lookup(request.issuer)
                ?: throw IllegalArgumentException("Unknown issuer: ${request.issuer}.")

            val holder = memberLookup.lookup(request.holder)
                ?: throw IllegalArgumentException("Unknown holder: ${request.holder}.")

            // TODO CORE-6173 use proper notary keyand NotaryLookup service here.
            val notary = memberLookup.lookup(request.notary)?.let { _ ->
                val notaryKey = memberLookup.lookup().single {
                    it.memberProvidedContext["corda.notary.service.name"] == request.notaryService.toString()
                }.ledgerKeys[0]
                Party(request.notaryService, notaryKey)
            }
                ?: throw IllegalArgumentException("Unknown notary: ${request.notaryService}.")

            val sessions = flowMessaging.initiateFlows(holder)

            val issuerKey = issuer.ledgerKeys.first()

            val holderKey = holder.ledgerKeys.first()

            val obligationState = ObligationState(issuerKey, holderKey, request.amount)

            val createObligationFlow =
                CreateObligationFlow(obligationState, notary, sessions, request.fromDayOffset, request.toDayOffset)

            val transaction = flowEngine.subFlow(createObligationFlow)

            val response = CreateObligationResponseMessage(transaction.id, obligationState.id)

            log.info("CreateObligationFlow: finishing.")
            return jsonMarshallingService.format(response)
        }
    }
}
