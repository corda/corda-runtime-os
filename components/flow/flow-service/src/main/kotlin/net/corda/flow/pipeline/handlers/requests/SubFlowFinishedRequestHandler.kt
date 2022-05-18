package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.SessionConfirmation
import net.corda.data.flow.state.waiting.SessionConfirmationType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.data.flow.state.waiting.Wakeup
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.FlowEventContext
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.state.FlowCheckpoint
import net.corda.layeredpropertymap.LayeredPropertyMapFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant

@Component(service = [FlowRequestHandler::class])
class SubFlowFinishedRequestHandler @Activate constructor(
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory,
    @Reference(service = LayeredPropertyMapFactory::class)
    private val layeredPropertyMapFactory: LayeredPropertyMapFactory
) : FlowRequestHandler<FlowIORequest.SubFlowFinished> {

    override val type = FlowIORequest.SubFlowFinished::class.java

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.SubFlowFinished): WaitingFor {
        return if (doesSubFlowHaveSessions(request)) {
            WaitingFor(SessionConfirmation(request.flowStackItem.sessionIds, SessionConfirmationType.CLOSE))
        } else {
            WaitingFor(Wakeup())
        }
    }

    override fun postProcess(
        context: FlowEventContext<Any>,
        request: FlowIORequest.SubFlowFinished
    ): FlowEventContext<Any> {
        val checkpoint = context.checkpoint

        val doesSubFlowHaveSessions = doesSubFlowHaveSessions(request)

        if (doesSubFlowHaveSessions) {
            flowSessionManager.sendCloseMessages(checkpoint, request.flowStackItem.sessionIds, Instant.now())
                .map { updatedSessionState -> checkpoint.putSessionState(updatedSessionState) }
        }

        val shouldWakeup = !doesSubFlowHaveSessions || allSessionsAreClosed(checkpoint, request)

        if (shouldWakeup){
            val record = flowRecordFactory.createFlowEventRecord(checkpoint.flowId, net.corda.data.flow.event.Wakeup())
            return context.copy(outputRecords = context.outputRecords + record)
        }
        return context
    }

    private fun doesSubFlowHaveSessions(request: FlowIORequest.SubFlowFinished): Boolean {
        return request.flowStackItem.isInitiatingFlow && request.flowStackItem.sessionIds.isNotEmpty()
    }

    private fun allSessionsAreClosed(checkpoint: FlowCheckpoint, request: FlowIORequest.SubFlowFinished): Boolean {
        return flowSessionManager.doAllSessionsHaveStatus(
            checkpoint,
            request.flowStackItem.sessionIds,
            SessionStateType.CLOSED
        )
    }
}