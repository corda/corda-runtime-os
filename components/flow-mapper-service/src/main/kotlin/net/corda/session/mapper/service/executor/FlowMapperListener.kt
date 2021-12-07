package net.corda.session.mapper.service.executor

import net.corda.data.flow.event.mapper.ExecuteCleanup
import net.corda.data.flow.event.mapper.FlowMapperEvent
import net.corda.data.flow.event.mapper.MessageDirection
import net.corda.data.flow.state.mapper.FlowMapperState
import net.corda.data.flow.state.mapper.FlowMapperStateType
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import java.util.concurrent.TimeUnit

class FlowMapperListener(
    private val scheduledTaskState: ScheduledTaskState,
    private val eventTopic: String,
) : StateAndEventListener<String, FlowMapperState> {
    private val publisher = scheduledTaskState.publisher
    private val executorService = scheduledTaskState.executorService
    private val scheduledTasks = scheduledTaskState.tasks

    companion object {
        private val log = contextLogger()
    }

    override fun onPartitionSynced(states: Map<String, FlowMapperState>) {
        log.trace { "Synced states $states" }
        val currentTime = System.currentTimeMillis()
        for (stateEntry in states) {
            val key = stateEntry.key
            val state = stateEntry.value
            val expiryTime = state.expiryTime
            if (expiryTime != null && state.status != FlowMapperStateType.CLOSING) {
                if (currentTime > expiryTime) {
                    log.debug { "Clearing up expired state for synced key $key" }
                    publisher?.publish(
                        listOf(
                            Record(
                                eventTopic, key, FlowMapperEvent(
                                    MessageDirection.INBOUND,
                                    ExecuteCleanup()
                                )
                            )
                        )
                    )
                } else {
                    setupCleanupTimer(key, expiryTime)
                }
            }
        }
    }

    override fun onPartitionLost(states: Map<String, FlowMapperState>) {
        log.trace { "Lost partition states $states" }
        for (stateEntry in states) {
            scheduledTasks.remove(stateEntry.key)?.cancel(true)
        }
    }

    override fun onPostCommit(updatedStates: Map<String, FlowMapperState?>) {
        log.trace { "Committed states $updatedStates" }
        updatedStates.forEach { state ->
            if (state.value?.status == FlowMapperStateType.CLOSING) {
                val expiryTime = state.value?.expiryTime
                    ?: throw CordaRuntimeException("Expiry time not set for FlowMapperState with status of CLOSING on key ${state.key}")
                setupCleanupTimer(state.key, expiryTime)
            }
        }
    }

    /**
     * Set up a cleanup timer for this key
     */
    private fun setupCleanupTimer(eventKey: String, expiryTime: Long) {
        scheduledTasks[eventKey] = executorService.schedule(
            {
                log.debug { "Clearing up mapper state for key $eventKey" }
                publisher?.publish(listOf(Record(eventTopic, eventKey, ExecuteCleanup())))
            },
            expiryTime - System.currentTimeMillis(),
            TimeUnit.MILLISECONDS
        )
    }
}
