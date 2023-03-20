package net.corda.flow.pipeline.handlers.requests.sessions

import java.time.Instant
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.requests.sessions.service.InitiateFlowRequestService
import net.corda.flow.pipeline.handlers.waiting.sessions.PROTOCOL_MISMATCH_HINT
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.pipeline.sessions.FlowSessionStateException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [FlowRequestHandler::class])
class SendRequestHandler @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory,
    @Reference(service = InitiateFlowRequestService::class)
    private val initiateFlowRequestService: InitiateFlowRequestService,
) : FlowRequestHandler<FlowIORequest.Send> {

    override val type = FlowIORequest.Send::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.Send): WaitingFor {
        val sessionsNotInitiated = initiateFlowRequestService.getSessionsNotInitiated(context, request.sessionPayloads.keys)
        return if (sessionsNotInitiated.isNotEmpty()) {
            return WaitingFor(SessionConfirmation(sessionsNotInitiated.map { it.sessionId }, SessionConfirmationType.INITIATE))
        } else {
            WaitingFor(net.corda.data.flow.state.waiting.Wakeup())
        }
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.Send): FlowEventContext<Any> {
        val checkpoint = context.checkpoint

        try {
            //generate init messages for sessions which do not exist yet
            initiateFlowRequestService.initiateFlowsNotInitiated(context, request.sessionPayloads.keys)

            val sessionIdToPayload = request.sessionPayloads.map { it.key.sessionId to it.value }.toMap()
            checkpoint.putSessionStates(flowSessionManager.sendDataMessages(checkpoint, sessionIdToPayload, Instant.now()))
        } catch (e: FlowSessionStateException) {
            throw FlowPlatformException("Failed to send: ${e.message}. $PROTOCOL_MISMATCH_HINT", e)
        }

        val wakeup = flowRecordFactory.createFlowEventRecord(checkpoint.flowId, Wakeup())
        return context.copy(outputRecords = context.outputRecords + wakeup)
    }
}
