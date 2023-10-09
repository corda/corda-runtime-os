package net.corda.flow.maintenance

import net.corda.data.flow.FlowTimeout
import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Flow.FLOW_TIMEOUT_TOPIC
import net.corda.schema.Schemas.ScheduledTask
import org.slf4j.LoggerFactory
import java.time.Instant

class SessionTimeoutTaskProcessor(
    private val stateManager: StateManager,
    private val now: () -> Instant = Instant::now
) : DurableProcessor<String, ScheduledTaskTrigger> {
    companion object {
        private val logger = LoggerFactory.getLogger(SessionTimeoutTaskProcessor::class.java)
        // TODO - this may need to move out somewhere else.
        const val STATE_META_SESSION_EXPIRY_KEY = "session.expiry"
    }
    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<ScheduledTaskTrigger>
        get() = ScheduledTaskTrigger::class.java

    override fun onNext(events: List<Record<String, ScheduledTaskTrigger>>): List<Record<*, *>> {
        // If we receive multiple, there's probably an issue somewhere, and we can ignore all but the last one.
        return events.lastOrNull { it.key == ScheduledTask.SCHEDULED_TASK_NAME_SESSION_TIMEOUT }?.value?.let { trigger ->
            logger.trace("Processing trigger scheduled at {}", trigger.timestamp)
            // TODO - temporary query
            // TODO - we must be able to specify additional filters so we can limit to selecting those sessions that are still open
            // TODO - we must be able to limit by type of state
            val checkpoints = stateManager.findByMetadata(
                MetadataFilter(STATE_META_SESSION_EXPIRY_KEY, Operation.LesserThan, now().epochSecond)
            )
            if (checkpoints.isEmpty()) {
                logger.trace("No flows to time out")
                emptyList()
            } else {
                // TODO - take log message out when everything plumbed in.
                logger.info("Trigger cleanup of $checkpoints")
                checkpoints.map { kvp ->
                    Record(FLOW_TIMEOUT_TOPIC, kvp.key,
                        FlowTimeout(
                            kvp.value.key,
                            Instant.ofEpochSecond(kvp.value.metadata[STATE_META_SESSION_EXPIRY_KEY] as Long))
                    )
                }
            }
        } ?: emptyList()
    }
}
