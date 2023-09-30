package net.corda.flow.maintenance

import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.SingleKeyFilter
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.ScheduledTask
import org.slf4j.LoggerFactory
import java.time.Instant

class SessionTimeoutTaskProcessor(
    private val stateManager: StateManager,
    private val now: () -> Instant = Instant::now
) : DurableProcessor<String, ScheduledTaskTrigger> {
    companion object {
        private val logger = LoggerFactory.getLogger(SessionTimeoutTaskProcessor::class.java)
    }
    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<ScheduledTaskTrigger>
        get() = ScheduledTaskTrigger::class.java

    override fun onNext(events: List<Record<String, ScheduledTaskTrigger>>): List<Record<*, *>> {
        // If we receive multiple, there's probably an issue somewhere, and we can ignore all but the last one.
        return events.lastOrNull { it.key == ScheduledTask.SCHEDULED_TASK_NAME_SESSION_TIMEOUT }?.value?.let { trigger ->
            logger.trace("Processing trigger scheduled at ${trigger.timestamp}")
            // TODO - temporary query
            // TODO - we must be able to specify additional filters so we can limit to selecting those sessions that are still open
            // TODO - we must be able to limit by type of state
            val checkpoints = stateManager.find(
                SingleKeyFilter("session.expiry", Operation.LesserThan, now())
            )
            if (checkpoints.isEmpty()) {
                logger.trace("No flows to time out")
                emptyList()
            } else {
                // TODO - return an avro message (schema TBC) for each checkpoint
                // TODO - define topic to publish message on
                logger.info("Trigger cleanup of $checkpoints")
                emptyList()
            }
        } ?: emptyList()
    }
}