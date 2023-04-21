package net.corda.flow.pipeline.handlers.requests.sessions

import java.time.Instant
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.waiting.SessionData
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
class SendAndReceiveRequestHandler @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory,
    @Reference(service = InitiateFlowRequestService::class)
    private val initiateFlowRequestService: InitiateFlowRequestService,
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
        initiateFlowRequestService.initiateFlowsNotInitiated(context, request.sessionToInfo.keys)

        val hasReceivedEvents = try {
            val sessionIdToPayload = request.sessionToInfo.mapKeys { it.key.sessionId }
            checkpoint.putSessionStates(flowSessionManager.sendDataMessages(checkpoint, sessionIdToPayload, Instant.now()))
            flowSessionManager.hasReceivedEvents(checkpoint, sessionIdToPayload.keys.toList())
        } catch (e: FlowSessionStateException) {
            throw FlowPlatformException("Failed to send/receive: ${e.message}. $PROTOCOL_MISMATCH_HINT", e)
        }

        return if (hasReceivedEvents) {
            val record = flowRecordFactory.createFlowEventRecord(checkpoint.flowId, Wakeup())
            context.copy(outputRecords = context.outputRecords + listOf(record))
        } else {
            context
        }
    }
}
