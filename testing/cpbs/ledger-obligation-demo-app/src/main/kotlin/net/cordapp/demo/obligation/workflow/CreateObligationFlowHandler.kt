package net.cordapp.demo.obligation.workflow

import net.corda.v5.application.flows.*
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.cordapp.demo.obligation.workflow.CreateObligationFlowHandler.Handler

/**
 * Represents a handler for the [CreateObligationFlow] flow.
 *
 * Note that this flow is not annotated with [InitiatedBy]. The adopted design pattern for this flow is to encapsulate
 * and implement transaction behavior as a sub-flow, promoting re-usability. Effectively this sub-flow can be treated
 * as a handler to either an end-to-end business process (via its [Handler] flow), or as a sub-process within a larger
 * end-to-end business process.
 *
 * @property session The session representing the counter-party who initiated the flow.
 */
class CreateObligationFlowHandler(private val session: FlowSession) : SubFlow<Unit> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    private lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call() {
        try {
            // TODO : Consider whether receiveFinality should have an overload that does not accept UtxoTransactionValidator.
            val finalizedSignedTransaction = utxoLedgerService.receiveFinality(session) {}
            log.info("Finalized tx: ${finalizedSignedTransaction.id}")
        } catch (e: Exception) {
            log.warn("CreateObligationFlowHandler finished exceptionally: ${e.message}", e)
        }
    }

    /**
     * Represents a flow handler which is initiated by the [CreateObligationFlow.Initiator] flow.
     *
     * Note that this flow is annotated with [InitiatedBy]. The adopted design pattern for this flow is to
     * encapsulate and implement the behavior to handle an obligation creation request as an end-to-end business
     * process. This calls [CreateObligationFlowHandler] as a sub-flow, passing in the flow session.
     */
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
