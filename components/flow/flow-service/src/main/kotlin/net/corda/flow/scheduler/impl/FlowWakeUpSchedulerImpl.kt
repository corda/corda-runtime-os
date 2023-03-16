package net.corda.flow.scheduler.impl

import net.corda.data.flow.event.Wakeup
import net.corda.data.flow.state.checkpoint.Checkpoint
import net.corda.flow.pipeline.factory.FlowRecordFactory
import net.corda.flow.scheduler.FlowWakeUpScheduler
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Component(service = [FlowWakeUpScheduler::class])
class FlowWakeUpSchedulerImpl constructor(
    private val publisherFactory: PublisherFactory,
    private val flowRecordFactory: FlowRecordFactory,
    private val scheduledExecutorService: ScheduledExecutorService
) : FlowWakeUpScheduler {

    @Activate
    constructor(
        @Reference(service = PublisherFactory::class)
        publisherFactory: PublisherFactory,
        @Reference(service = FlowRecordFactory::class)
        flowRecordFactory: FlowRecordFactory,
    ) : this(publisherFactory, flowRecordFactory, Executors.newSingleThreadScheduledExecutor())

    private val scheduledWakeUps = ConcurrentHashMap<String, ScheduledFuture<*>>()
    private var publisher: Publisher? = null

    override fun onConfigChange(config: Map<String, SmartConfig>) {
        publisher?.close()
        publisher = publisherFactory.createPublisher(PublisherConfig("FlowWakeUpRestResource"), config.getConfig(MESSAGING_CONFIG))
    }

    override fun onPartitionSynced(states: Map<String, Checkpoint>) {
        scheduleTasks(states.values)
    }

    override fun onPartitionLost(states: Map<String, Checkpoint>) {
        cancelScheduledWakeUps(states.keys)
    }

    override fun onPostCommit(updatedStates: Map<String, Checkpoint?>) {
        val updates = updatedStates.filter { it.value != null }.map { it.value!! }
        val deletes = updatedStates.filter { it.value == null }

        scheduleTasks(updates)
        cancelScheduledWakeUps(deletes.keys)
    }

    private fun scheduleTasks(checkpoints: Collection<Checkpoint>) {
        checkpoints.forEach {
            val id = it.flowId
            val scheduledWakeUp = scheduledExecutorService.schedule(
                { publishWakeUp(id) },
                it.pipelineState.maxFlowSleepDuration.toLong(),
                TimeUnit.MILLISECONDS
            )

            val existingWakeUp = scheduledWakeUps.put(id, scheduledWakeUp)
            existingWakeUp?.cancel(false)
        }
    }

    private fun cancelScheduledWakeUps(flowIds:Collection<String>){
        flowIds.forEach {
            scheduledWakeUps.remove(it)?.cancel(false)
        }
    }

    private fun publishWakeUp(flowId: String) {
        publisher?.publish(listOf(flowRecordFactory.createFlowEventRecord(flowId, Wakeup())))
    }
}