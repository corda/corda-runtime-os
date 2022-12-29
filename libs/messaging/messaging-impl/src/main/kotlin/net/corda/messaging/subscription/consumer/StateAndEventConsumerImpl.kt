package net.corda.messaging.subscription.consumer

import java.time.Clock
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import net.corda.lifecycle.Resource
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.messaging.utils.tryGetResult
import net.corda.schema.Schemas.Companion.getStateAndEventStateTopic
import net.corda.v5.base.util.debug
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
internal class StateAndEventConsumerImpl<K : Any, S : Any, E : Any>(
    private val config: ResolvedSubscriptionConfig,
    override val eventConsumer: CordaConsumer<K, E>,
    override val stateConsumer: CordaConsumer<K, S>,
    private val partitionState: StateAndEventPartitionState<K, S>,
    private val stateAndEventListener: StateAndEventListener<K, S>?
) : StateAndEventConsumer<K, S, E>, Resource {

    companion object {
        //short timeout for poll of paused partitions when waiting for processor to finish
        private val PAUSED_POLL_TIMEOUT = Duration.ofMillis(100)
        //short timeout for state polling so as to not starve the event poller
        private val STATE_POLL_TIMEOUT = Duration.ofMillis(100)
    }

    //single threaded executor per state and event consumer
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        val thread = Thread(runnable)
        thread.isDaemon = true
        thread
    }

    private val log = LoggerFactory.getLogger(config.loggerName)

    private val maxPollInterval = config.processorTimeout.toMillis()
    private val initialProcessorTimeout = maxPollInterval / 4

    private val currentStates = partitionState.currentStates
    private val partitionsToSync = partitionState.partitionsToSync

    private var pollIntervalCutoff = 0L

    override fun getInMemoryStateValue(key: K): S? {
        currentStates.forEach {
            val state = it.value[key]
            if (state != null) {
                return state.second
            }
        }

        return null
    }

    override fun pollAndUpdateStates(syncPartitions: Boolean) {
        if (stateConsumer.assignment().isEmpty()) {
            log.debug { "State consumer has no partitions assigned." }
            return
        }

        stateConsumer.poll(STATE_POLL_TIMEOUT).forEach { state ->
            log.debug { "Updating state: $state" }
            updateInMemoryState(state)
        }

        if (syncPartitions && partitionsToSync.isNotEmpty()) {
            log.info("State consumer in group ${config.group} is syncing partitions: $partitionsToSync")
            resumeConsumerAndExecuteListener(removeAndReturnSyncedPartitions())
        }
    }

    override fun close() {
        eventConsumer.close()
        stateConsumer.close()
        executor.shutdown()
    }

    private fun removeAndReturnSyncedPartitions(): Set<CordaTopicPartition> {
        val partitionsSynced = mutableSetOf<CordaTopicPartition>()
        val statePartitionsToSync = partitionsToSync.toMap()
        for (partition in statePartitionsToSync) {
            val partitionId = partition.key
            val stateTopic = getStateAndEventStateTopic(config.topic)
            val stateTopicPartition = CordaTopicPartition(stateTopic, partitionId)
            val stateConsumerPollPosition = stateConsumer.position(stateTopicPartition)
            val endOffset = partition.value
            if (stateConsumerPollPosition >= endOffset) {
                log.info(
                    "State partition $stateTopicPartition is now up to date for consumer in group ${config.group}. " +
                            "Poll position $stateConsumerPollPosition, recorded end offset $endOffset"
                )
                partitionsToSync.remove(partitionId)
                partitionsSynced.add(CordaTopicPartition(config.topic, partitionId))
            }
        }

        return partitionsSynced
    }

    private fun resumeConsumerAndExecuteListener(partitionsSynced: Set<CordaTopicPartition>) {
        if (partitionsSynced.isEmpty()) {
            return
        }

        log.info("State consumer in group ${config.group} is up to date for $partitionsSynced.  Resuming event feed.")
        eventConsumer.resume(partitionsSynced)

        stateAndEventListener?.let { listener ->
            for (partition in partitionsSynced) {
                listener.onPartitionSynced(getStatesForPartition(partition.partition))
            }
        }
    }

    override fun waitForFunctionToFinish(function: () -> Any, maxTimeout: Long, timeoutErrorMessage: String): CompletableFuture<Any> {
        val future: CompletableFuture<Any> = CompletableFuture.supplyAsync({ function() }, executor)
        future.tryGetResult(getInitialConsumerTimeout())

        if (!future.isDone) {
            pauseEventConsumerAndWaitForFutureToFinish(future, maxTimeout)
        }

        if (!future.isDone) {
            future.cancel(true)
            log.error(timeoutErrorMessage)
        }

        return future
    }

    private fun pauseEventConsumerAndWaitForFutureToFinish(future: CompletableFuture<*>, timeout: Long) {
        val assignment = eventConsumer.assignment() - eventConsumer.paused()
        log.debug { "Pause partitions and wait for future to finish. Assignment: $assignment"}
        eventConsumer.pause(assignment)
        val maxWaitTime = System.currentTimeMillis() + timeout
        var done = future.isDone

        while (!done && (maxWaitTime > System.currentTimeMillis())) {
            eventConsumer.poll(PAUSED_POLL_TIMEOUT)
            pollIntervalCutoff = getNextPollIntervalCutoff()
            pollAndUpdateStates(false)
            done = future.isDone
        }

        log.debug { "Resume partitions. Finished wait for future[completed=${future.isDone}]. Assignment: $assignment"}
        eventConsumer.resume(assignment)
    }

    private fun updateInMemoryState(state: CordaConsumerRecord<K, S>) {
        currentStates[state.partition]?.compute(state.key) { _, currentState ->
            if (currentState == null || currentState.first <= state.timestamp) {
                val value = state.value
                if (value == null) {
                    // Removes this state from the map
                    null
                } else {
                    // Replaces/adds the new state
                    Pair(state.timestamp, value)
                }
            } else {
                // Keeps the old state
                currentState
            }
        }
    }

    override fun updateInMemoryStatePostCommit(updatedStates: MutableMap<Int, MutableMap<K, S?>>, clock: Clock) {
        val updatedStatesByKey = mutableMapOf<K, S?>()
        updatedStates.forEach { (partitionId, states) ->
            for (entry in states) {
                val key = entry.key
                val value = entry.value
                val stateTopic = getStateAndEventStateTopic(config.topic)
                //will never be null, created on assignment in rebalance listener
                val currentStatesByPartition = currentStates[partitionId]
                    ?: throw CordaMessageAPIFatalException("Current State map for " +
                            "group ${config.group} on topic $stateTopic[$partitionId] is null.")
                updatedStatesByKey[key] = value
                if (value != null) {
                    currentStatesByPartition[key] = Pair(clock.instant().toEpochMilli(), value)
                } else {
                    currentStatesByPartition.remove(key)
                }
            }
        }

        stateAndEventListener?.onPostCommit(updatedStatesByKey)
    }

    override fun resetPollInterval(): Boolean {
        if (System.currentTimeMillis() > pollIntervalCutoff) {
            // Here we pause each consumer in order to mark a poll to avoid a Kafka timeout without actually consuming
            // any records.
            val eventConsumerPausePartitions = eventConsumer.assignment() - eventConsumer.paused()
            val stateConsumerPausePartitions = stateConsumer.assignment() - stateConsumer.paused()

            log.debug { "Resetting poll interval. Pausing event consumer partitions: $eventConsumerPausePartitions"}
            eventConsumer.pause(eventConsumerPausePartitions)
            log.debug { "Resetting poll interval. Pausing state consumer partitions: $stateConsumerPausePartitions"}
            stateConsumer.pause(stateConsumerPausePartitions)

            partitionState.dirty = false
            val eventRecords = eventConsumer.poll(PAUSED_POLL_TIMEOUT)
            eventRecords.forEach { event ->
                log.warn("Resetting polling interval has lost event with key: ${event.key}, this will likely cause execution" +
                        " problems with the Flow with this id")
            }
            val stateRecords = stateConsumer.poll(PAUSED_POLL_TIMEOUT)
            stateRecords.forEach { state ->
                log.warn("Resetting polling interval has lost state with key: ${state.key}, this will likely cause execution" +
                        " problems with the Flow with this id")
            }

            eventConsumer.resume(eventConsumerPausePartitions)
            log.debug { "Reset of event consumer poll interval complete. Resuming event assignment: $eventConsumerPausePartitions" }
            stateConsumer.resume(stateConsumerPausePartitions)
            log.debug { "Reset of state consumer poll interval complete. Resuming state assignment: $stateConsumerPausePartitions" }

            pollIntervalCutoff = getNextPollIntervalCutoff()

            if (partitionState.dirty) {
                partitionState.dirty = false
                return true
            }
        }
        return false
    }

    /**
     * Don't allow initial timeout to go past the poll interval cutoff point
     */
    private fun getInitialConsumerTimeout(): Long {
        return if ((System.currentTimeMillis() + initialProcessorTimeout) > pollIntervalCutoff) {
            pollIntervalCutoff - System.currentTimeMillis()
        } else {
            initialProcessorTimeout
        }
    }

    private fun getNextPollIntervalCutoff(): Long {
        return System.currentTimeMillis() + (maxPollInterval / 2)
    }

    private fun getStatesForPartition(partitionId: Int): Map<K, S> {
        return currentStates[partitionId]?.map { state -> Pair(state.key, state.value.second) }?.toMap() ?: mapOf()
    }
}
