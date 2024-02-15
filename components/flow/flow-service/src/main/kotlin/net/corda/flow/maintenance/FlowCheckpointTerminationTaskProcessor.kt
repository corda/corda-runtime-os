package net.corda.flow.maintenance

import net.corda.data.flow.FlowCheckpointTermination
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.flow.state.impl.CheckpointMetadataKeys.STATE_META_CHECKPOINT_TERMINATED_KEY
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.STATE_TYPE
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.Schemas.ScheduledTask
import net.corda.schema.configuration.FlowConfig.PROCESSING_FLOW_CHECKPOINT_CLEANUP_TIME
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID


/**
 * This processor is used to get checkpoints that have reached their terminal state within the flow engine.
 * Checkpoints are held onto for a configurable amount of time to allow for duplicates, and replayed inputs to be passed the flow engine
 * and for the flow engine to replay the outputs for each input.
 * This task will batch the checkpoints to be deleted and pass it to another topic
 * to be deleted by [FlowCheckpointTerminationCleanupProcessor]
 */
class FlowCheckpointTerminationTaskProcessor(
    private val stateManager: StateManager,
    config: SmartConfig,
    private val clock: Clock,
    private val batchSize: Int = ID_BATCH_SIZE
) : DurableProcessor<String, ScheduledTaskTrigger> {
    companion object {
        private val logger = LoggerFactory.getLogger(FlowCheckpointTerminationTaskProcessor::class.java)
        private const val ID_BATCH_SIZE = 200
    }

    private val checkpointTerminationTimeMilliseconds = config.getLong(PROCESSING_FLOW_CHECKPOINT_CLEANUP_TIME)

    override fun onNext(events: List<Record<String, ScheduledTaskTrigger>>): List<Record<*, *>> {
        // Filter to the task that this processor cares about. There can be other tasks on this topic.
        return if (events.any { it.value?.name == ScheduledTask.SCHEDULED_TASK_NAME_FLOW_CHECKPOINT_TERMINATION }) {
            process().map {
                Record(Schemas.Flow.FLOW_CHECKPOINT_TERMINATION, UUID.randomUUID().toString(), it)
            }
        } else {
            listOf()
        }
    }

    private fun process(): List<FlowCheckpointTermination> {
        logger.debug { "Received a scheduled task trigger. Scheduling checkpoint termination events for the flow engine." }
        val keys = getExpiredStateIds()
        val batches = batchIds(keys)
        return batches.map {
            FlowCheckpointTermination(it)
        }
    }

    private fun getExpiredStateIds(): List<String> {
        val windowExpiry = clock.instant() - Duration.ofMillis(checkpointTerminationTimeMilliseconds)
        val states = stateManager.findUpdatedBetweenWithMetadataMatchingAll(
            IntervalFilter(Instant.EPOCH, windowExpiry),
            listOf(
                MetadataFilter(STATE_TYPE, Operation.Equals, Checkpoint::class.java.name),
                MetadataFilter(STATE_META_CHECKPOINT_TERMINATED_KEY, Operation.Equals, true),
            )
        )

        return states.map {
            it.key
        }.also {
            logger.debug { "Found ${states.size} terminated checkpoint states eligible for deletion" }
        }
    }

    private fun batchIds(ids: List<String>): List<List<String>> {
        return ids.chunked(batchSize)
    }

    override val keyClass = String::class.java
    override val valueClass = ScheduledTaskTrigger::class.java
}
