package net.corda.processors.rest

import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.utilities.trace
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

class FlowStatusCleanupProcessor (
    private val stateManager: StateManager,
    private val now: () -> Instant = Instant::now,
    private val batchSize: Int = BATCH_SIZE,
    config: SmartConfig
) : DurableProcessor<String, ScheduledTaskTrigger> {
    companion object {
        private val logger = LoggerFactory.getLogger(FlowStatusCleanupProcessor::class.java)
        private const val BATCH_SIZE = 200
    }

    override val keyClass: Class<String> = String::class.java
    override val valueClass: Class<ScheduledTaskTrigger> = ScheduledTaskTrigger::class.java
    private val cleanupTimeMilliseconds = 604800000

    override fun onNext(events: List<Record<String, ScheduledTaskTrigger>>): List<Record<*, *>> {
        return events.lastOrNull {
            it.key == "flow-status-cleanup" // replace with const CORE-19490
        }?.value?.let { trigger ->
            logger.trace { "Processing trigger scheduled at ${trigger.timestamp}" }
            val statesToCleanup = staleFlowStatuses().map{ it.key }

            if (statesToCleanup.isEmpty()) {
                logger.trace { "No FlowStatus records to clean up" }
                emptyList()
            } else {
                logger.trace { "Triggering cleanup of ${statesToCleanup.size} FlowStatus records" }

                statesToCleanup.chunked(batchSize) { batch ->
                    Record("rest.flow.status.cleanup", UUID.randomUUID(), batch) // replace topic with const CORE-19490
                }
            }
        } ?: emptyList()
    }

    private fun staleFlowStatuses() =
        stateManager.findUpdatedBetweenWithMetadataFilter(
            IntervalFilter(Instant.EPOCH, now().minusMillis(cleanupTimeMilliseconds.toLong())),
            MetadataFilter("PLACEHOLDER awaiting CORE-19440", Operation.Equals, true)
        )
}