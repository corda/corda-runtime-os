package net.cordapp.demo.obligation.workflow

import net.corda.v5.application.flows.*
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
import net.cordapp.demo.obligation.workflow.CreateObligationFlow.Initiator
import java.time.Instant

/**
 * Represents a sub-flow for creating an obligation.
 *
 * Note that this flow is not annotated with [InitiatingFlow]. The adopted design pattern for this flow is to
 * encapsulate and implement transaction behavior as a sub-flow, promoting re-usability. Effectively this sub-flow
 * can be treated as either an end-to-end business process (via its [Initiator] flow), or as a sub-process within a
 * larger end-to-end business process.
 *
 * TODO : Should these be property or param?...they are properties, but they only appear as params in a public API.
 * @property obligation The obligation to create.
 * @property notary The notary to use for all future obligation transactions relating to this obligation.
 * @property sessions The provided flow sessions for any state counter-parties.
 * @property fromDayOffset TODO
 * @property toDayOffset TODO
 *
 * TODO : I have not documented private lateinit var properties, since they are not visible on the public API.
 */
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

        val from = Instant.now().plusMillis(fromDayOffset.days.toMillis())
        val to = Instant.now().plusMillis(toDayOffset.days.toMillis())

        val transaction = utxoLedgerService
            .getTransactionBuilder()
            .setNotary(notary)
            .addOutputState(obligation)
            .addCommand(ObligationContract.Create())
            .setTimeWindowBetween(from, to)
            .addSignatories(listOf(obligation.debtor))

        @Suppress("DEPRECATION")
        val signedTransaction = transaction.toSignedTransaction(memberLookup.myInfo().ledgerKeys.first())

        return utxoLedgerService.finalize(signedTransaction, sessions.toList())
    }

    /**
     * Represents an initiating flow for creating an obligation.
     *
     * Note that this flow is annotated with [InitiatingFlow]. The adopted design pattern for this flow is to
     * encapsulate and implement the behavior to accept an obligation creation request as an end-to-end business
     * process. This calls [CreateObligationFlow] as a sub-flow, passing in the obligation, required sessions and
     * notary.
     */
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

            val debtor = requireNotNull(memberLookup.lookup(request.debtor)) {
                "Unknown debtor: ${request.debtor}."
            }

            val creditor = requireNotNull(memberLookup.lookup(request.creditor)) {
                "Unknown creditor: ${request.creditor}."
            }

            // TODO CORE-6173 use proper notary key and NotaryLookup service here.
            val notary = memberLookup.lookup(request.notary)?.let {
                val notaryKey = memberLookup.lookup().single {
                    it.memberProvidedContext["corda.notary.service.name"] == request.notaryService.toString()
                }.ledgerKeys[0]

                Party(request.notaryService, notaryKey)
            } ?: throw IllegalArgumentException("Unknown notary: ${request.notaryService}.")

            val sessions = flowMessaging.initiateFlows(creditor)

            val debtorKey = debtor.ledgerKeys.first()

            val creditorKey = creditor.ledgerKeys.first()

            val obligationState = ObligationState(creditorKey, debtorKey, request.amount)

            val createObligationFlow = CreateObligationFlow(
                obligationState,
                notary,
                sessions,
                request.fromDayOffset,
                request.toDayOffset
            )

            val transaction = flowEngine.subFlow(createObligationFlow)

            val response = CreateObligationResponseMessage(transaction.id.toString(), obligationState.id)

            log.info("CreateObligationFlow: finishing.")

            return jsonMarshallingService.format(response)
        }
    }
}
