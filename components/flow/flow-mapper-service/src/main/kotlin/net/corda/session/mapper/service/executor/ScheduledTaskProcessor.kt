package net.corda.session.mapper.service.executor

import net.corda.data.flow.event.mapper.ExecuteCleanup
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.session.mapper.service.state.StateMetadataKeys.FLOW_MAPPER_STATUS
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

class ScheduledTaskProcessor(
    private val stateManager: StateManager,
    private val clock: Clock,
    private val cleanupWindow: Long,
    private val batchSize: Int = ID_BATCH_SIZE
): DurableProcessor<String, ScheduledTaskTrigger> {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val ID_BATCH_SIZE = 200
    }

    override fun onNext(events: List<Record<String, ScheduledTaskTrigger>>): List<Record<*, *>> {
        return if (events.any { it.value?.name == Schemas.ScheduledTask.SCHEDULED_TASK_NAME_MAPPER_CLEANUP }) {
            process().map {
                Record(Schemas.Flow.FLOW_MAPPER_CLEANUP_TOPIC, UUID.randomUUID().toString(), it)
            }
        } else {
            listOf()
        }
    }

    private fun process() : List<ExecuteCleanup> {
        logger.debug { "Received a scheduled task trigger. Scheduling cleanup events for the flow mapper." }
        val keys = getExpiredStateIds()
        val batches = batchIds(keys)
        return batches.map {
            ExecuteCleanup(it)
        }
    }

    private fun getExpiredStateIds() : List<String> {
        val windowExpiry = clock.instant() - Duration.ofMillis(cleanupWindow)
        val states = stateManager.findUpdatedBetweenWithMetadataFilter(
            IntervalFilter(Instant.EPOCH, windowExpiry),
            MetadataFilter(FLOW_MAPPER_STATUS, Operation.Equals, FlowMapperStateType.CLOSING.toString())
        )
        return states.map {
            it.key
        }.also {
            logger.debug { "Found ${states.size} states eligible for cleanup" }
        }
    }

    private fun batchIds(ids: List<String>) : List<List<String>> {
        return ids.chunked(batchSize)
    }


    override val keyClass = String::class.java
    override val valueClass = ScheduledTaskTrigger::class.java
}