package net.corda.flow.maintenance

import net.corda.data.flow.FlowTimeout
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.flow.state.impl.CheckpointMetadataKeys.STATE_META_SESSION_EXPIRY_KEY
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.STATE_TYPE
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.constants.MessagingMetadataKeys.PROCESSING_FAILURE
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.FLOW_TIMEOUT_TOPIC
import net.corda.schema.Schemas.ScheduledTask
import net.corda.schema.configuration.FlowConfig.PROCESSING_MAX_IDLE_TIME
import net.corda.schema.configuration.FlowConfig.SESSION_TIMEOUT_WINDOW
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Automatically scheduled by Corda for cleaning up timed out flows.
 * A flow will be picked up to be timed out if and only if any of the following conditions is true:
 *  - Flow has at least one opened session that timed out.
 *  - Flow hasn't been updated within the configured maximum idle time.
 *  - Flow processing marked as failed by the messaging layer (key [PROCESSING_FAILURE] set).
 *
 * This processor is responsible for deciding which flows to time out based on the above conditions, so it makes sense
 * for the time out reason to be set here as well. The generated [FlowTimeout] records are later picked up by the
 * [TimeoutEventCleanupProcessor], which is responsible for cleaning up [Checkpoint]s and signaling other parts of the
 * system.
 *
 * TODO - Execute a single State Manager API call once all filters are supported at once.
 */
class FlowTimeoutTaskProcessor(
    private val stateManager: StateManager,
    config: SmartConfig,
    private val now: () -> Instant = Instant::now
) : DurableProcessor<String, ScheduledTaskTrigger> {
    companion object {
        private val logger = LoggerFactory.getLogger(FlowTimeoutTaskProcessor::class.java)
        const val MAX_IDLE_TIME_ERROR_MESSAGE =
            "Flow has been 'RUNNING' without updates for longer than the configured '${PROCESSING_MAX_IDLE_TIME}' milliseconds"
        const val SESSION_EXPIRED_ERROR_MESSAGE =
            "Flow has at least one open session that has not received messages for longer than the configured " +
                "'${SESSION_TIMEOUT_WINDOW}' milliseconds"
        const val PROCESS_FAILURE_ERROR_MESSAGE =
            "Flow encountered an unrecoverable error while processing an event. Please check the logs and ensure that " +
                "there are no flaws in the Corda Application"
    }

    override val keyClass = String::class.java
    override val valueClass = ScheduledTaskTrigger::class.java
    private val maxIdleTimeMilliseconds = config.getLong(PROCESSING_MAX_IDLE_TIME)

    private fun idleTimeOutExpired() =
        // Flows that have not been updated in at least [maxIdleTimeMilliseconds]
        stateManager.findUpdatedBetweenWithMetadataMatchingAll(
            IntervalFilter(
                Instant.EPOCH,
                now().minusMillis(maxIdleTimeMilliseconds)
            ),
            listOf(
                MetadataFilter(STATE_TYPE, Operation.Equals, Checkpoint::class.java.name),
            )
        ).map { kvp ->
            Record(
                FLOW_TIMEOUT_TOPIC,
                kvp.key,
                FlowTimeout().apply {
                    timeoutDateTime = now()
                    checkpointStateKey = kvp.value.key
                    reason = "$MAX_IDLE_TIME_ERROR_MESSAGE (${maxIdleTimeMilliseconds}ms)"
                }
            )
        }

    private fun sessionExpiredOrFailureSignaledByMessagingLayer() =
        // Flows timed out by the messaging layer + sessions timed out
        stateManager.findByMetadataMatchingAny(
            listOf(
                // Failure or time out signaled by the messaging layer
                MetadataFilter(PROCESSING_FAILURE, Operation.Equals, true),
                // Session expired
                MetadataFilter(STATE_META_SESSION_EXPIRY_KEY, Operation.LesserThan, now().epochSecond),
            )
        ).filter {
            it.value.metadata.containsKeyWithValue(STATE_TYPE, Checkpoint::class.java.name)
        }.map { kvp ->
            Record(
                FLOW_TIMEOUT_TOPIC,
                kvp.key,
                FlowTimeout().apply {
                    timeoutDateTime = now()
                    checkpointStateKey = kvp.value.key
                    reason = if (kvp.value.metadata.containsKeyWithValue(PROCESSING_FAILURE, true)) {
                        PROCESS_FAILURE_ERROR_MESSAGE
                    } else {
                        SESSION_EXPIRED_ERROR_MESSAGE
                    }
                }
            )
        }

    override fun onNext(events: List<Record<String, ScheduledTaskTrigger>>): List<Record<*, *>> {
        // Filter to the task that this processor cares about. There can be other tasks on this topic.
        return events.lastOrNull {
            it.key == ScheduledTask.SCHEDULED_TASK_NAME_SESSION_TIMEOUT
        }?.value?.let { trigger ->
            logger.trace("Processing trigger scheduled at {}", trigger.timestamp)
            val flowsToTimeOut = idleTimeOutExpired() + sessionExpiredOrFailureSignaledByMessagingLayer()

            if (flowsToTimeOut.isEmpty()) {
                logger.trace("No flows to time out")
                emptyList()
            } else {
                logger.debug { "Triggering cleanup of ${flowsToTimeOut.joinToString { it.key }}" }
                flowsToTimeOut
            }
        } ?: emptyList()
    }
}
