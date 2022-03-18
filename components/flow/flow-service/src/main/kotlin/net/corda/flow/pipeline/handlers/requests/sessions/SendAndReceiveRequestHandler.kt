package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.event.session.SessionData
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.factory.SessionEventFactory
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.session.manager.SessionManager
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.nio.ByteBuffer
import java.time.Instant

@Component(service = [FlowRequestHandler::class])
class SendAndReceiveRequestHandler @Activate constructor(
    @Reference(service = SessionManager::class)
    private val sessionManager: SessionManager,
    @Reference(service = SessionEventFactory::class)
    private val sessionEventFactory: SessionEventFactory,
) : FlowRequestHandler<FlowIORequest.SendAndReceive> {

    override val type = FlowIORequest.SendAndReceive::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.SendAndReceive): WaitingFor {
        return WaitingFor(net.corda.data.flow.state.waiting.SessionData(request.sessionToPayload.keys.toList()))
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.SendAndReceive): FlowEventContext<Any> {
        val checkpoint = context.checkpoint
        val now = Instant.now()

        for ((sessionId, payload) in request.sessionToPayload) {
            val sessionState = checkpoint.getSession(sessionId)
            val (initiatingIdentity, initiatedIdentity) = getInitiatingAndInitiatedParties(
                sessionState, checkpoint.flowKey.identity
            )
            val updatedSessionState = sessionManager.processMessageToSend(
                key = checkpoint.flowId,
                sessionState = checkpoint.getSessionState(sessionId),
                event = sessionEventFactory.create(sessionId, now,SessionData(ByteBuffer.wrap(payload))),
                instant = now
            )

            checkpoint.putSessionState(updatedSessionState)
        }

        return context
    }
}