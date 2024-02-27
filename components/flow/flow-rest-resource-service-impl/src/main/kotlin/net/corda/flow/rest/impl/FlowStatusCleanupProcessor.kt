package net.corda.flow.rest.impl

import net.corda.data.flow.output.FlowStates
import net.corda.data.rest.ExecuteFlowStatusCleanup
import net.corda.data.rest.FlowStatusRecord
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

/**
 * A processor for cleaning up flow status records that are in terminal states and have not been updated within a specified time frame.
 *
 * When this processor receives a [ScheduledTaskTrigger] of type [SCHEDULE_TASK_NAME_FLOW_STATUS_CLEANUP], it will poll the StateManager
 * for all FlowStatus records with a status of [FlowStates.COMPLETED], [FlowStates.FAILED], or [FlowStates.KILLED] which have
 * not been updated within a configurable time window, specified by [REST_FLOW_STATUS_CLEANUP_TIME_MS] in the REST config.
 *
 * The retrieved stale records, if any, are split into batches determined by [batchSize] and sent downstream to the
 * [FlowStatusDeletionExecutor] for deletion.
 *
 * @property config The [SmartConfig] instance used for configuration settings, including the cleanup time.
 * @property stateManager The [StateManager] instance used for accessing and modifying flow status records.
 * @property now A lambda function that returns the current [Instant]; this is used to assess FlowStatus staleness.
 * @property batchSize The number of records per batch that are sent to the downstream [FlowStatusDeletionExecutor].
 */
class FlowStatusCleanupProcessor(
    config: SmartConfig,
    private val stateManager: StateManager,
    private val now: () -> Instant = Instant::now,
    private val batchSize: Int = BATCH_SIZE,
) : DurableProcessor<String, ScheduledTaskTrigger> {
    companion object {
        private val logger = LoggerFactory.getLogger(FlowStatusCleanupProcessor::class.java)
        private val TERMINAL_STATES = setOf(FlowStates.COMPLETED, FlowStates.FAILED, FlowStates.KILLED)
        private const val FLOW_STATUS_METADATA_KEY = "flowStatus"
        private const val BATCH_SIZE = 500
    }

    override val keyClass: Class<String> = String::class.java
    override val valueClass: Class<ScheduledTaskTrigger> = ScheduledTaskTrigger::class.java
    private val cleanupTimeMilliseconds = config.getLong(REST_FLOW_STATUS_CLEANUP_TIME_MS)

    override fun onNext(events: List<Record<String, ScheduledTaskTrigger>>): List<Record<*, *>> {
        return events.lastOrNull { it.key == SCHEDULE_TASK_NAME_FLOW_STATUS_CLEANUP }?.value?.let { trigger ->
            logger.trace { "Processing flow status cleanup trigger scheduled at ${trigger.timestamp}" }

            getStaleFlowStatuses()
                .map { FlowStatusRecord(it.key, it.value.version) }
                .chunked(batchSize)
                .map { Record(REST_FLOW_STATUS_CLEANUP_TOPIC, UUID.randomUUID().toString(), ExecuteFlowStatusCleanup(it)) }
        } ?: emptyList()
    }

    private fun getStaleFlowStatuses() =
        stateManager.findUpdatedBetweenWithMetadataMatchingAny(
            IntervalFilter(Instant.EPOCH, now().minusMillis(cleanupTimeMilliseconds)),
            TERMINAL_STATES.map { MetadataFilter(FLOW_STATUS_METADATA_KEY, Operation.Equals, it.name) }
        )
}
