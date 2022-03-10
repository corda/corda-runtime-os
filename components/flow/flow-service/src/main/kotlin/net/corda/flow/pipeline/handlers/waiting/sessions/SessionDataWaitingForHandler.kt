package net.corda.flow.pipeline.handlers.waiting.sessions

import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.state.Checkpoint
import net.corda.data.flow.state.session.SessionState
import net.corda.data.flow.state.waiting.SessionData
import net.corda.flow.fiber.FlowContinuation
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.FlowProcessingException
import net.corda.flow.pipeline.handlers.getSerializationService
import net.corda.flow.pipeline.handlers.waiting.FlowWaitingForHandler
import net.corda.flow.pipeline.sandbox.FlowSandboxService
import net.corda.session.manager.SessionManager
import net.corda.v5.application.services.serialization.deserialize
import net.corda.virtualnode.toCorda
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowWaitingForHandler::class])
class SessionDataWaitingForHandler @Activate constructor(
    @Reference(service = FlowSandboxService::class)
    private val flowSandboxService: FlowSandboxService,
    @Reference(service = SessionManager::class)
    private val sessionManager: SessionManager
) : FlowWaitingForHandler<SessionData> {

    override val type = SessionData::class.java

    override fun runOrContinue(context: FlowEventContext<*>, waitingFor: SessionData): FlowContinuation {
        val checkpoint = context.checkpoint!!
        val receivedEvents = sessionManager.getReceivedEvents(checkpoint, waitingFor.sessionIds)
        return if (receivedEvents.size != waitingFor.sessionIds.size) {
            FlowContinuation.Continue
        } else {
            sessionManager.acknowledgeReceivedEvents(receivedEvents)
            FlowContinuation.Run(convertToIncomingPayloads(checkpoint, receivedEvents))
        }
    }

    private fun convertToIncomingPayloads(
        checkpoint: Checkpoint,
        receivedEvents: List<Pair<SessionState, SessionEvent>>
    ): Map<String, Any> {
        val serializationService = flowSandboxService.getSerializationService(checkpoint.flowKey.identity.toCorda())
        return receivedEvents.associate { (_, event) ->
            when (val sessionPayload = event.payload) {
                is net.corda.data.flow.event.session.SessionData -> Pair(
                    event.sessionId,
                    serializationService.deserialize(sessionPayload.payload.array())
                )
                else -> throw FlowProcessingException(
                    "Received events should be data messages but got ${sessionPayload::class.java.name} instead"
                )
            }
        }
    }
}