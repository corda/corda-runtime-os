package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.SessionEvent
import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.handlers.addOrReplaceSession
import net.corda.flow.pipeline.handlers.getSession
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.requests.requireCheckpoint
import net.corda.session.manager.SessionManager
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.nio.ByteBuffer
import java.time.Instant

@Component(service = [FlowRequestHandler::class])
class SendAndReceiveRequestHandler @Activate constructor(
    @Reference(service = SessionManager::class)
    private val sessionManager: SessionManager
) : FlowRequestHandler<FlowIORequest.SendAndReceive> {

    override val type = FlowIORequest.SendAndReceive::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.SendAndReceive): WaitingFor {
        return WaitingFor(net.corda.data.flow.state.waiting.SessionData(request.sessionToPayload.keys.toList()))
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.SendAndReceive): FlowEventContext<Any> {
        val checkpoint = requireCheckpoint(context)

        val now = Instant.now()

        for ((sessionId, payload) in request.sessionToPayload) {

            val updatedSessionState = sessionManager.processMessageToSend(
                key = checkpoint.flowKey.flowId,
                sessionState = checkpoint.getSession(sessionId),
                event = SessionEvent.newBuilder()
                    .setSessionId(sessionId)
                    .setMessageDirection(MessageDirection.OUTBOUND)
                    .setTimestamp(now)
                    .setSequenceNum(null)
                    .setReceivedSequenceNum(0)
                    .setOutOfOrderSequenceNums(listOf(0))
                    .setPayload(SessionData(ByteBuffer.wrap(payload)))
                    .build(),
                instant = now
            )

            checkpoint.addOrReplaceSession(updatedSessionState)
        }

        return context
    }
}