package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.state.waiting.SessionData
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.requests.sessions.service.GenerateSessionService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component(service = [FlowRequestHandler::class])
class ReceiveRequestHandler @Activate constructor(
    @Reference(service = GenerateSessionService::class)
    private val generateSessionService: GenerateSessionService,
) : FlowRequestHandler<FlowIORequest.Receive> {

    override val type = FlowIORequest.Receive::class.java

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.Receive): WaitingFor {
        log.info("FlowId ${context.checkpoint.flowId} called receive for ${request.sessions.map { it.sessionId }}. " +
                "setting getUpdatedWaitingFor")

        return WaitingFor(SessionData(request.sessions.map { it.sessionId }))
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.Receive): FlowEventContext<Any> {
        log.info("FlowId ${context.checkpoint.flowId} called receive for ${request.sessions}")
        //generate init messages for sessions which do not exist yet
        generateSessionService.generateSessions(context, request.sessions, true)
        return context
    }
}
