package net.corda.flow.pipeline.handlers.requests.sessions

import java.time.Instant
import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.handlers.requests.sessions.service.InitiateFlowRequestService
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.pipeline.sessions.FlowSessionStateException
import net.corda.v5.base.util.contextLogger
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

    private companion object {
        val log = contextLogger()
    }

    override val type = FlowIORequest.Send::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.Send): WaitingFor {
        val sessionsNotInitiated = initiateFlowRequestService.getSessionsNotInitiated(context, request.sessionPayloads.keys)
        return if(sessionsNotInitiated.isNotEmpty()) {
            return WaitingFor(SessionConfirmation(sessionsNotInitiated.map { it.sessionId }, SessionConfirmationType.INITIATE))
        }
        else { WaitingFor(net.corda.data.flow.state.waiting.Wakeup()) }
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.Send): FlowEventContext<Any> {
        val checkpoint = context.checkpoint

        try {
            //generate init messages for sessions which do not exist yet
            initiateFlowRequestService.initiateFlowsNotInitiated(context, request.sessionPayloads.keys)

            //validate session states and send data messages
            val sessionIdToPayload = request.sessionPayloads.map { it.key.sessionId to it.value }.toMap()
            flowSessionManager.validateSessionStates(checkpoint, sessionIdToPayload.keys)
            flowSessionManager.sendDataMessages(checkpoint, sessionIdToPayload, Instant.now()).forEach { updatedSessionState ->
                checkpoint.putSessionState(updatedSessionState)
            }
        } catch (e: FlowSessionStateException) {
            log.info("Failed to send session data for session", e)
            throw FlowPlatformException(e.message, e)
        }

        val wakeup = flowRecordFactory.createFlowEventRecord(checkpoint.flowId, Wakeup())
        return context.copy(outputRecords = context.outputRecords + wakeup)
    }
}
