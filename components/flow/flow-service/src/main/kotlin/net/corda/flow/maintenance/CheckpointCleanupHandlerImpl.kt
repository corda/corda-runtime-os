package net.corda.flow.maintenance

import net.corda.data.flow.FlowInitiatorType
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.pipeline.exceptions.FlowMarkedForKillException
import net.corda.flow.pipeline.exceptions.FlowProcessingExceptionTypes.FLOW_FAILED
import net.corda.flow.pipeline.factory.FlowMessageFactory
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.pipeline.handlers.requests.helper.getRPCMapperKey
import net.corda.flow.pipeline.sessions.FlowSessionManager
import net.corda.flow.state.FlowCheckpoint
import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.FlowConfig
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.time.Instant

@Component(service = [CheckpointCleanupHandler::class])
class CheckpointCleanupHandlerImpl @Activate constructor(
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory,
    @Reference(service = FlowSessionManager::class)
    private val flowSessionManager: FlowSessionManager,
    @Reference(service = FlowMessageFactory::class)
    private val flowMessageFactory: FlowMessageFactory
) : CheckpointCleanupHandler {

    override fun cleanupCheckpoint(
        checkpoint: FlowCheckpoint,
        config: SmartConfig,
        exception: Exception
    ): List<Record<*, *>> {
        val time = Instant.now()
        val records = errorActiveSessions(checkpoint, config, exception, time) +
                cleanupSessions(checkpoint, config, time) +
                generateStatus(checkpoint, exception) +
                cleanupRpcFlowMapperState(checkpoint, config, time)
        checkpoint.markDeleted()
        return records
    }

    private fun errorActiveSessions(
        checkpoint: FlowCheckpoint,
        config: SmartConfig,
        exception: Exception,
        currentTime: Instant
    ): List<Record<*, *>> {
        val sessions = checkpoint.sessions.filterNot {
            it.status == SessionStateType.CLOSED || it.status == SessionStateType.ERROR
        }.map { it.sessionId }
        if (sessions.isNotEmpty()) {
            checkpoint.putSessionStates(
                flowSessionManager.sendErrorMessages(
                    checkpoint,
                    sessions,
                    exception,
                    currentTime
                )
            )
        }
        return flowSessionManager.getSessionErrorEventRecords(checkpoint, config, currentTime)
    }

    private fun cleanupSessions(
        checkpoint: FlowCheckpoint,
        config: SmartConfig,
        currentTime: Instant
    ): List<Record<*, *>> {
        val cleanupTimeWindow = config.getLong(FlowConfig.SESSION_FLOW_CLEANUP_TIME)
        val cleanupTime = currentTime.plusMillis(cleanupTimeWindow).toEpochMilli()
        return checkpoint.sessions.filterNot { it.hasScheduledCleanup }.map {
            it.hasScheduledCleanup = true
            flowRecordFactory.createFlowMapperEventRecord(
                it.sessionId,
                ScheduleCleanup(cleanupTime)
            )
        }
    }

    private fun generateStatus(checkpoint: FlowCheckpoint, exception: Exception): List<Record<*, *>> {
        return try {
            val status = when (exception) {
                is FlowMarkedForKillException -> {
                    flowMessageFactory.createFlowKilledStatusMessage(checkpoint, exception.message)
                }
                else -> {
                    flowMessageFactory.createFlowFailedStatusMessage(
                        checkpoint,
                        FLOW_FAILED,
                        exception.message ?: "No message provided"
                    )
                }
            }
            listOf(flowRecordFactory.createFlowStatusRecord(status))
        } catch (e: Exception) {
            listOf()
        }
    }

    private fun cleanupRpcFlowMapperState(
        checkpoint: FlowCheckpoint,
        config: SmartConfig,
        currentTime: Instant
    ): List<Record<*, *>> {
        return if (checkpoint.flowStartContext.initiatorType == FlowInitiatorType.RPC) {
            val cleanupWindow = config.getLong(FlowConfig.PROCESSING_FLOW_MAPPER_CLEANUP_TIME)
            val expiryTime = currentTime.plusMillis(cleanupWindow).toEpochMilli()
            listOf(flowRecordFactory.createFlowMapperEventRecord(
                getRPCMapperKey(checkpoint.flowKey),
                ScheduleCleanup(expiryTime)
            ))
        } else {
            listOf()
        }
    }
}