 package net.corda.flow.pipeline.handlers.waiting.sessions

import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.CounterPartyFlowInfo
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import net.corda.flow.pipeline.handlers.waiting.FlowWaitingForHandler
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.osgi.service.component.annotations.Component

 /**
  * This handler decides whether the flow fiber is allowed to resume after requesting counter party flow info.
  * If the status is in CREATED it means that this is an initiating flow and the counterparty has no responded yet so the properties will
  * not have been received yet.
  * If the status is ERROR then we return an error to the flow fiber as this session should not be interacted with.
  * Any other state means that the SessionInit/SessionConfirm messages have been transmitted so the data can be retrieved from the
  * [SessionState]
  */
 @Component(service = [FlowWaitingForHandler::class])
class CounterpartyFlowInfoWaitingForHandler : FlowWaitingForHandler<CounterPartyFlowInfo> {

    override val type = CounterPartyFlowInfo::class.java

    override fun runOrContinue(context: FlowEventContext<*>, waitingFor: CounterPartyFlowInfo): FlowContinuation {
        val checkpoint = context.checkpoint

        val sessionId = waitingFor.sessionId
        val sessionState = checkpoint.getSessionState(sessionId) ?: throw FlowPlatformException("Failed to get counterparty info " +
                "as the session does not exist.")

        return when(sessionState.status) {
            SessionStateType.CREATED -> FlowContinuation.Continue
            SessionStateType.ERROR -> {
                FlowContinuation.Error(
                    CordaRuntimeException(
                        "Failed to get counterparty info due to sessions with errorred status."
                    )
                )
            }
            else -> FlowContinuation.Run(Unit)
        }
    }


 }
