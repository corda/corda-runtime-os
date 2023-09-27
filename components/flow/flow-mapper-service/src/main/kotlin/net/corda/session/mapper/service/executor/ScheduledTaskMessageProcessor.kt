package net.corda.session.mapper.service.executor

import net.corda.data.flow.event.mapper.ExecuteCleanup
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.records.Record
import net.corda.session.mapper.service.state.StateMetadataKeys.FLOW_MAPPER_STATUS
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant

class ScheduledTaskMessageProcessor(
    private val stateManager: StateManager,
    private val clock: Clock,
    private val cleanupWindow: Long
) {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun process(event: ScheduledTaskTrigger) : List<Record<String, ExecuteCleanup>> {
        logger.debug { "Received a scheduled task trigger. Scheduling cleanup events for the flow mapper." }
        // Lookup expired states
        val keys = getExpiredStateIds()
        // Break up into sensible sized batches.

        // Convert to records to send
    }

    private fun getExpiredStateIds() : List<String> {
        val windowExpiry = clock.instant() - Duration.ofMillis(cleanupWindow)
        val states = stateManager.getUpdatedBetween(Instant.MIN, windowExpiry)
        // This should use the state manager lookup API combining a metadata lookup and a time window, but that doesn't
        // exist yet.
        return states.filter {
            val state = it.value
            state.metadata[FLOW_MAPPER_STATUS] == FlowMapperStateType.CLOSING.toString()
        }.map {
            it.key
        }
    }
}