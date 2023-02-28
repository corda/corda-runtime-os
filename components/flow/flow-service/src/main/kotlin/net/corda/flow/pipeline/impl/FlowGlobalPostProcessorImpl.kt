package net.corda.flow.pipeline.impl

import net.corda.data.flow.FlowKey
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.ScheduleCleanup
import net.corda.data.flow.output.FlowStatus
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.external.events.impl.ExternalEventManager
import net.corda.flow.pipeline.events.FlowEventContext
import net.corda.flow.pipeline.FlowGlobalPostProcessor
import net.corda.flow.pipeline.factory.FlowMessageFactory
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.messaging.api.records.Record
import net.corda.schema.configuration.FlowConfig.SESSION_FLOW_CLEANUP_TIME
import net.corda.session.manager.SessionManager
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.time.Instant

@Component(service = [FlowGlobalPostProcessor::class])
class FlowGlobalPostProcessorImpl @Activate constructor(
    @Reference(service = ExternalEventManager::class)
    private val externalEventManager: ExternalEventManager,
    @Reference(service = SessionManager::class)
    private val sessionManager: SessionManager,
    @Reference(service = FlowMessageFactory::class)
    private val flowMessageFactory: FlowMessageFactory,
    @Reference(service = FlowRecordFactory::class)
    private val flowRecordFactory: FlowRecordFactory
) : FlowGlobalPostProcessor {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun postProcess(context: FlowEventContext<Any>): FlowEventContext<Any> {
        val now = Instant.now()

        postProcessPendingPlatformError(context)

        val outputRecords = getSessionEvents(context, now) +
                getFlowMapperSessionCleanupEvents(context, now) +
                getExternalEvent(context, now) +
                postProcessRetries(context)

        return context.copy(outputRecords = context.outputRecords + outputRecords)
    }

    private fun getSessionEvents(context: FlowEventContext<Any>, now: Instant): List<Record<*, FlowMapperEvent>> {
        val checkpoint = context.checkpoint
        val doesCheckpointExist = checkpoint.doesExist
        return checkpoint.sessions
            .map { sessionState ->
                sessionManager.getMessagesToSend(
                    sessionState,
                    now,
                    context.config,
                    checkpoint.flowKey.identity
                )
            }
            .onEach { (updatedSessionState, _) ->
                if (doesCheckpointExist) {
                    checkpoint.putSessionState(updatedSessionState)
                }
            }
            .flatMap { (_, events) -> events }
            .map { event -> flowRecordFactory.createFlowMapperEventRecord(event.sessionId, event) }
    }

    private fun getFlowMapperSessionCleanupEvents(
        context: FlowEventContext<Any>,
        now: Instant
    ): List<Record<*, FlowMapperEvent>> {
        val flowCleanupTime = context.config.getLong(SESSION_FLOW_CLEANUP_TIME)
        val expiryTime = now.plusMillis(flowCleanupTime).toEpochMilli()
        return context.checkpoint.sessions
            .filterNot { sessionState -> sessionState.hasScheduledCleanup }
            .filter { sessionState -> sessionState.status == SessionStateType.CLOSED || sessionState.status == SessionStateType.ERROR }
            .onEach { sessionState -> sessionState.hasScheduledCleanup = true }
            .map { sessionState ->
                flowRecordFactory.createFlowMapperEventRecord(
                    sessionState.sessionId,
                    ScheduleCleanup(expiryTime)
                )
            }
    }

    private fun postProcessPendingPlatformError(context: FlowEventContext<Any>) {
        /**
         * If a platform error was previously reported to the user the error should now be cleared. If we have reached
         * the post-processing step we can assume the pending error has been processed.
         */
        context.checkpoint.clearPendingPlatformError()
    }

    /**
     * Check to see if any external events needs to be sent or resent due to no response being received within a given time period.
     */
    private fun getExternalEvent(context: FlowEventContext<Any>, now: Instant): List<Record<*, *>> {
        val config = context.config
        val externalEventState = context.checkpoint.externalEventState
        return if (externalEventState == null) {
            listOf()
        } else {
            externalEventManager.getEventToSend(externalEventState, now, config)
                .let { (updatedExternalEventState, record) ->
                    context.checkpoint.externalEventState = updatedExternalEventState
                    if (record != null) {
                        listOf(record)
                    } else {
                        listOf()
                    }
                }
        }
    }

    private fun postProcessRetries(context: FlowEventContext<Any>): List<Record<FlowKey, FlowStatus>> {
        /**
         * When the flow enters a retry state the flow status is updated to "RETRYING", this
         * needs to be set back when a retry clears, however we only need to do this if the flow
         * is still running, if it is now complete the status will have been updated already
         */

        val checkpoint = context.checkpoint

        // The flow was not in a retry state so nothing to do
        if (!checkpoint.inRetryState) {
            return listOf()
        }

        if (context.isRetryEvent) {
            // If we reach the post-processing step with a retry set we
            // assume whatever the previous retry was it has now cleared
            log.debug("The Flow was in a retry state that has now cleared.")
            checkpoint.markRetrySuccess()
        }

        // If the flow has been completed, no need to update the status
        if (!checkpoint.doesExist) {
            return listOf()
        }

        val status = flowMessageFactory.createFlowStartedStatusMessage(checkpoint)
        return listOf(flowRecordFactory.createFlowStatusRecord(status))
    }
}
