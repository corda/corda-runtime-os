package net.corda.components.session.mapper.service

import net.corda.components.session.mapper.ScheduledTaskState
import net.corda.data.flow.event.mapper.ExecuteCleanup
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class FlowMapperListener(
    private val scheduledTaskState: ScheduledTaskState,
    private val eventTopic: String,
) : StateAndEventListener<String, FlowMapperState> {

    private val publisher: Publisher = scheduledTaskState.publisher
    private val executorService: ScheduledExecutorService = scheduledTaskState.executorService
    private val scheduledTasks: MutableMap<String, ScheduledFuture<*>> = scheduledTaskState.tasks

    companion object {
        private val log = contextLogger()
    }

    override fun onPartitionSynced(states: Map<String, FlowMapperState>) {
        log.debug {"Synced states $states" }
        val currentTime = System.currentTimeMillis()
        for (stateEntry in states) {
            val key = stateEntry.key
            val state = stateEntry.value
            val expiryTime = state.expiryTime
            if (expiryTime != null && state.status != FlowMapperStateType.CLOSED) {
                if (currentTime > expiryTime) {
                    log.debug { "Clearing up expired state for synced key $key" }
                    publisher.publish(listOf(Record(eventTopic, key, ExecuteCleanup())))
                } else {
                    val scheduleTask = executorService.schedule(
                        {
                            log.info("Clearing up expired state for key $key")
                            publisher.publish(listOf(Record(eventTopic, key, ExecuteCleanup())))
                        },
                        expiryTime - currentTime,
                        TimeUnit.MILLISECONDS
                    )
                    scheduledTasks[key] = scheduleTask
                }
            }
        }
    }

    override fun onPartitionLost(states: Map<String, FlowMapperState>) {
        log.debug { "Lost partition states $states" }
        for (stateEntry in states) {
            scheduledTasks.remove(stateEntry.key)?.cancel(true)
        }
    }

    override fun onPostCommit(updatedStates: Map<String, FlowMapperState?>) {
        log.debug { "Committed states $updatedStates" }
    }
}
