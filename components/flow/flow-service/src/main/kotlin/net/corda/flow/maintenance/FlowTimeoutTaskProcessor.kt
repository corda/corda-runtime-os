package net.corda.flow.maintenance

import net.corda.data.flow.FlowTimeout
import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.flow.state.impl.CheckpointMetadataKeys.STATE_META_SESSION_EXPIRY_KEY
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.constants.MessagingMetadataKeys
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.FLOW_TIMEOUT_TOPIC
import net.corda.schema.Schemas.ScheduledTask
import net.corda.schema.configuration.FlowConfig
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Automatically scheduled by Corda for cleaning up timed out flows. A flow is timed out if and only if any of the
 * following conditions is true:
 *  - Flow has at least one opened session that timed out.
 *  - Flow hasn't been updated within the configured maximum idle time.
 *  - Flow processing marked as failed by the messaging layer (key [MessagingMetadataKeys.PROCESSING_FAILURE] set).
 */
class FlowTimeoutTaskProcessor(
    private val stateManager: StateManager,
    config: SmartConfig,
    private val now: () -> Instant = Instant::now
) : DurableProcessor<String, ScheduledTaskTrigger> {
    companion object {
        private val logger = LoggerFactory.getLogger(FlowTimeoutTaskProcessor::class.java)
    }

    override val keyClass = String::class.java
    override val valueClass = ScheduledTaskTrigger::class.java
    private val maxIdleTimeMilliseconds = config.getLong(FlowConfig.PROCESSING_MAX_IDLE_TIME)

    override fun onNext(events: List<Record<String, ScheduledTaskTrigger>>): List<Record<*, *>> {
        // If we receive multiple, there's probably an issue somewhere, and we can ignore all but the last one.
        return events.lastOrNull {
            it.key == ScheduledTask.SCHEDULED_TASK_NAME_SESSION_TIMEOUT
        }?.value?.let { trigger ->
            logger.trace("Processing trigger scheduled at {}", trigger.timestamp)
            // TODO - temporary query
            // TODO - we must be able to limit by type of state
            val flowsToTimeOut =
                // Flows timed out by the messaging layer + sessions timed out
                stateManager.findByMetadataMatchingAny(
                    listOf(
                        // Time out signaled by the messaging layer
                        MetadataFilter(MessagingMetadataKeys.PROCESSING_FAILURE, Operation.Equals, true),
                        // Session expired
                        MetadataFilter(STATE_META_SESSION_EXPIRY_KEY, Operation.LesserThan, now().epochSecond),
                    )
                ) +
                    // Flows that have not been updated in at least [maxIdleTime] seconds
                    stateManager.updatedBetween(
                        IntervalFilter(
                            Instant.EPOCH,
                            now().minusMillis(maxIdleTimeMilliseconds)
                        )
                    )

            if (flowsToTimeOut.isEmpty()) {
                logger.trace("No flows to time out")
                emptyList()
            } else {
                logger.debug { "Trigger cleanup of $flowsToTimeOut" }
                flowsToTimeOut.map { kvp ->
                    Record(FLOW_TIMEOUT_TOPIC, kvp.key, FlowTimeout(kvp.value.key, now()))
                }
            }
        } ?: emptyList()
    }
}
