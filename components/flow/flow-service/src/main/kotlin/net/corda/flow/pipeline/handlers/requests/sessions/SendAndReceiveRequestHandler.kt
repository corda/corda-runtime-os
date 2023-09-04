package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.state.waiting.SessionData
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.requests.sessions.service.GenerateSessionService
import net.corda.flow.pipeline.handlers.waiting.sessions.PROTOCOL_MISMATCH_HINT
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.pipeline.sessions.FlowSessionStateException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant

@Component(service = [FlowRequestHandler::class])
class SendAndReceiveRequestHandler @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager,
    @Reference(service = GenerateSessionService::class)
    private val generateSessionService: GenerateSessionService,
) : FlowRequestHandler<FlowIORequest.SendAndReceive> {

    override val type = FlowIORequest.SendAndReceive::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.SendAndReceive): WaitingFor {
        return WaitingFor(SessionData(request.sessionToInfo.map { it.key.sessionId }))
    }

    override fun postProcess(
        context: FlowEventContext<Any>,
        request: FlowIORequest.SendAndReceive
    ): FlowEventContext<Any> {
        val checkpoint = context.checkpoint

        //generate init messages for sessions which do not exist yet
        generateSessionService.generateSessions(context, request.sessionToInfo.keys)

        try {
            checkpoint.putSessionStates(flowSessionManager.sendDataMessages(checkpoint, request.sessionToInfo, Instant.now()))
        } catch (e: FlowSessionStateException) {
            throw FlowPlatformException("Failed to send/receive: ${e.message}. $PROTOCOL_MISMATCH_HINT", e)
        }

        return context
    }
}
