package net.corda.flow.pipeline.handlers.requests

import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.output.FlowStates
import net.corda.data.flow.state.session.SessionStateType
import net.corda.data.flow.state.waiting.WaitingFor
import net.corda.flow.fiber.FlowIORequest
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.exceptions.FlowFatalException
import net.corda.flow.pipeline.exceptions.FlowProcessingExceptionTypes.FLOW_FAILED
import net.corda.flow.pipeline.factory.FlowMessageFactory
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.pipeline.handlers.requests.helper.recordFlowRuntimeMetric
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.pipeline.sessions.FlowSessionStateException
import net.corda.schema.configuration.FlowConfig.PROCESSING_FLOW_CLEANUP_TIME
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.time.Instant

@Suppress("Unused")
@Component(service = [FlowRequestHandler::class])
class FlowFailedRequestHandler @Activate constructor(
    @Reference(service = FlowMessageFactory::class)
    private val flowMessageFactory: FlowMessageFactory,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory,
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager
) : FlowRequestHandler<FlowIORequest.FlowFailed> {
    override val type = FlowIORequest.FlowFailed::class.java

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun getUpdatedWaitingFor(context: FlowEventContext<Any>, request: FlowIORequest.FlowFailed): WaitingFor {
        return WaitingFor(null)
    }

    override fun postProcess(context: FlowEventContext<Any>, request: FlowIORequest.FlowFailed): FlowEventContext<Any> {
        val checkpoint = context.checkpoint
        recordFlowRuntimeMetric(checkpoint, FlowStates.FAILED.toString())

        try {
            val sessionIds = checkpoint.sessions.map { it.sessionId }
            val sessionToError = sessionIds - flowSessionManager.getSessionsWithStatuses(
                checkpoint,
                sessionIds,
                setOf(SessionStateType.ERROR, SessionStateType.CLOSED)
            ).map { it.sessionId }.toSet()

            flowSessionManager.sendErrorMessages(
                checkpoint,
                sessionToError,
                request.exception,
                Instant.now()
            )
        } catch (e: FlowSessionStateException) {
            throw FlowFatalException(e.message, e)
        }

        val status = flowMessageFactory.createFlowFailedStatusMessage(
            checkpoint,
            FLOW_FAILED,
            request.exception.message ?: request.exception.javaClass.name
        )

        //When a flow is started by the REST api, a FlowKey is sent to the Flow mapper, so we send a cleanup event for this key.
        //Flows triggered by SessionInit don't have this FlowKey, so we do not send a cleanup event.
        val records = if (checkpoint.flowStartContext.initiatorType == FlowInitiatorType.RPC) {
            val flowCleanupTime = context.config.getLong(PROCESSING_FLOW_CLEANUP_TIME)
            Instant.now().plusMillis(flowCleanupTime).toEpochMilli()
            val expiryTime = Instant.now().plusMillis(flowCleanupTime).toEpochMilli()
            listOf(
                flowRecordFactory.createFlowStatusRecord(status),
                flowRecordFactory.createFlowMapperEventRecord(
                    checkpoint.flowKey.toString(),
                    ScheduleCleanup(expiryTime)
                )
            )
        } else {
            listOf(
                flowRecordFactory.createFlowStatusRecord(status)
            )
        }
        log.info("Flow [${checkpoint.flowId}] failed")
        checkpoint.markDeleted()
        return context.copy(outputRecords = context.outputRecords + records)
    }
}
