package net.corda.processors.rest

import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas.Rest.REST_FLOW_STATUS_CLEANUP_TOPIC
import net.corda.schema.Schemas.ScheduledTask.SCHEDULE_TASK_NAME_FLOW_STATUS_CLEANUP
import net.corda.schema.configuration.ConfigKeys.REST_FLOW_STATUS_CLEANUP_TIME_MS
import net.corda.utilities.trace
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

class FlowStatusCleanupProcessor (
    config: SmartConfig,
    private val stateManager: StateManager,
    private val now: () -> Instant = Instant::now,
    private val batchSize: Int = BATCH_SIZE,
) : DurableProcessor<String, ScheduledTaskTrigger> {
    companion object {
        private val logger = LoggerFactory.getLogger(FlowStatusCleanupProcessor::class.java)
        private const val BATCH_SIZE = 200
    }

    override val keyClass: Class<String> = String::class.java
    override val valueClass: Class<ScheduledTaskTrigger> = ScheduledTaskTrigger::class.java
    private val cleanupTimeMilliseconds = config.getLong(REST_FLOW_STATUS_CLEANUP_TIME_MS)

    override fun onNext(events: List<Record<String, ScheduledTaskTrigger>>): List<Record<*, *>> {
        return events.lastOrNull { it.key == SCHEDULE_TASK_NAME_FLOW_STATUS_CLEANUP }?.value?.let { trigger ->
            logger.trace { "Processing flow status cleanup trigger scheduled at ${trigger.timestamp}" }

            staleFlowStatuses()
                .map{ it.key }
                .chunked(batchSize) { batch -> Record(REST_FLOW_STATUS_CLEANUP_TOPIC, UUID.randomUUID(), batch) }
        } ?: emptyList()
    }

    private fun staleFlowStatuses() =
        stateManager.findUpdatedBetweenWithMetadataMatchingAny(
            IntervalFilter(Instant.EPOCH, now().minusMillis(cleanupTimeMilliseconds)),
            listOf(MetadataFilter("PLACEHOLDER awaiting CORE-19440", Operation.Equals, true))
        )
}
