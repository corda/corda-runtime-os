import net.corda.data.messaging.mediator.MediatorStates
import net.corda.data.scheduler.ScheduledTaskTrigger
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.IntervalFilter
import net.corda.libs.statemanager.api.MetadataFilter
import net.corda.libs.statemanager.api.Operation
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.records.Record
import net.corda.schema.Schemas
import net.corda.schema.Schemas.Messaging.MEDIATOR_CLEANUP_TOPIC
import net.corda.schema.configuration.MessagingConfig.Subscription
import net.corda.utilities.debug
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

class MediatorStateCleanupTask (
    private val stateManager: StateManager,
    config: SmartConfig,
    private val now: () -> Instant = Instant::now,
    private val batchSize: Int = ID_BATCH_SIZE
) : DurableProcessor<String, ScheduledTaskTrigger> {
    companion object {
        const val IS_TERMINATED = "isTerminated"
        private val logger = LoggerFactory.getLogger(MediatorStateCleanupTask::class.java)
        private const val ID_BATCH_SIZE = 200
    }

    override val keyClass = String::class.java
    override val valueClass = ScheduledTaskTrigger::class.java
    private val cleanupTimeMilliseconds = config.getLong(Subscription.MEDIATOR_STATE_CLEANUP)

    override fun onNext(events: List<Record<String, ScheduledTaskTrigger>>): List<Record<*, *>> {
        return events.lastOrNull {
            it.key == Schemas.ScheduledTask.SCHEDULED_TASK_MEDIATOR_STATE_CLEANUP
        }?.value?.let { trigger ->
            logger.trace("Processing trigger scheduled at {}", trigger.timestamp)
            val statesToCleanup = terminatedStates().map { it.key }

            if (statesToCleanup.isEmpty()) {
                logger.trace("No mediator states to cleanup")
                emptyList()
            } else {
                logger.debug { "Trigger cleanup of $statesToCleanup" }
                batchIds(statesToCleanup).map { batch ->
                    Record(MEDIATOR_CLEANUP_TOPIC, UUID.randomUUID().toString(), MediatorStates(batch))
                }
            }
        } ?: emptyList()
    }

    private fun terminatedStates() =
        // Flows that have not been updated in at least [maxIdleTimeMilliseconds]
        stateManager.findUpdatedBetweenWithMetadataMatchingAll(
            IntervalFilter(
                Instant.EPOCH,
                now().minusMillis(cleanupTimeMilliseconds)
            ),
            listOf(
                MetadataFilter(IS_TERMINATED, Operation.Equals, true),
            )
        )

    private fun batchIds(ids: List<String>) : List<List<String>> {
        return ids.chunked(batchSize)
    }
}
