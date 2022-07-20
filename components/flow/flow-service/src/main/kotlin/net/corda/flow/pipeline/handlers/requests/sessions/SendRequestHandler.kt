package net.corda.flow.pipeline.handlers.requests.sessions

import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowPlatformException
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.pipeline.handlers.requests.FlowRequestHandler
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.pipeline.sessions.FlowSessionStateException
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant

@Component(service = [FlowRequestHandler::class])
class SendRequestHandler @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory
) : FlowRequestHandler<FlowIORequest.Send> {

    private companion object {
        val log = contextLogger()
    }

    override val type = FlowIORequest.Send::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.Send): WaitingFor {
        return WaitingFor(net.corda.data.flow.state.waiting.Wakeup())
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.Send): FlowEventContext<Any> {
        val checkpoint = context.checkpoint

        try {
            flowSessionManager.validateSessionStates(checkpoint, request.sessionToPayload.keys)
            flowSessionManager.sendDataMessages(checkpoint, request.sessionToPayload, Instant.now()).forEach { updatedSessionState ->
                checkpoint.putSessionState(updatedSessionState)
            }
        } catch (e: FlowSessionStateException) {
            log.info("Failed to send session data for for session", e)
            throw FlowPlatformException(e.message, e)
        }

        val wakeup = flowRecordFactory.createFlowEventRecord(checkpoint.flowId, Wakeup())
        return context.copy(outputRecords = context.outputRecords + wakeup)
    }
}