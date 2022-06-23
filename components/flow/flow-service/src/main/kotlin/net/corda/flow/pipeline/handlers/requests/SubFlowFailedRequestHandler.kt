package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.flow.state.waiting.Wakeup
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.pipeline.sessions.FlowSessionStateException
import net.corda.flow.state.FlowCheckpoint
import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant

@Component(service = [FlowRequestHandler::class])
class SubFlowFailedRequestHandler @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory
) : FlowRequestHandler<FlowIORequest.SubFlowFailed> {

    private companion object {
        val log = contextLogger()
    }

    override val type = FlowIORequest.SubFlowFailed::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.SubFlowFailed): WaitingFor {
        return WaitingFor(Wakeup())
    }

    override fun postProcess(
        context: FlowEventContext<Any>,
        request: FlowIORequest.SubFlowFailed
    ): FlowEventContext<Any> {

        log.info("Sub-flow [${context.checkpoint.flowId}] failed", request.throwable)

        val checkpoint = context.checkpoint

        try {
            flowSessionManager.sendErrorMessages(
                checkpoint,
                getSessionsToError(checkpoint, request),
                request.throwable,
                Instant.now()
            ).map { updatedSessionState ->
                checkpoint.putSessionState(updatedSessionState)
            }
        } catch (e: FlowSessionStateException) {
            // TODO CORE-4850 Wakeup with error when session does not exist
            throw FlowFatalException(e.message, context, e)
        }

        val record = flowRecordFactory.createFlowEventRecord(checkpoint.flowId, net.corda.data.flow.event.Wakeup())
        return context.copy(outputRecords = context.outputRecords + record)
    }

    private fun getSessionsToError(checkpoint: FlowCheckpoint, request: FlowIORequest.SubFlowFailed): List<String> {
        val flowStackItem = request.flowStackItem
        val erroredSessions = flowSessionManager.getSessionsWithStatus(checkpoint, flowStackItem.sessionIds, SessionStateType.ERROR)
        val closedSessions = flowSessionManager.getSessionsWithStatus(checkpoint, flowStackItem.sessionIds, SessionStateType.CLOSED)
        return flowStackItem.sessionIds - (erroredSessions + closedSessions).map { it.sessionId }
    }
}