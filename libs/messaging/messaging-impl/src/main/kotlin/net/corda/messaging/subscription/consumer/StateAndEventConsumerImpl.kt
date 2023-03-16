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
import net.corda.schema.Schemas.getStateAndEventStateTopic
import net.corda.utilities.debug
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

        //short timeout for state polling to not starve the event poller
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
            log.debug { "Processing state: $state" }
            // Do not update in memory state unless we are syncing as we are already guaranteed to have the latest state
            if (partitionsToSync.containsKey(state.partition)) {
                updateInMemoryState(state)
            }
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

        log.info("State consumer in group ${config.group} is up to date for $partitionsSynced. Resuming event feed.")
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

    /**
     * Helper method to poll events from a paused [eventConsumer], should only be used to prevent it from being kicked
     * out of the consumer group and only when all partitions are paused as to not lose any events.
     *
     * If a rebalance occurs during the poll, the [cleanUp] function is invoked and only [eventConsumer]'s partitions
     * matching the following conditions are resumed:
     *  - Not currently being synced.
     *  - Previously paused by the caller ([pausedPartitions]).
     *  If a partition not matching the above rules is wrongly resumed, events might be processed twice or for states
     *  not yet in sync.
     */
    private fun pollWithCleanUpAndExceptionOnRebalance(
        message: String,
        pausedPartitions: Set<CordaTopicPartition>,
        cleanUp: () -> Unit
    ) {
        partitionState.dirty = false
        eventConsumer.poll(PAUSED_POLL_TIMEOUT).forEach { event ->
            // Should not happen, the warning is left in place for easier troubleshooting in case it does.
            log.warn("Polling from paused eventConsumer has lost event with key: ${event.key}, this will likely " +
                    "cause execution problems for events with this id")
        }

        // Rebalance occurred: give up, nothing can be assumed at this point.
        if (partitionState.dirty) {
            partitionState.dirty = false
            cleanUp()

            // If we don't own the paused partitions anymore, they'll start as resumed on the new assigned consumer.
            // If we still own the paused partitions, resume them if and only if they are not currently being synced.
            val partitionsToResume = eventConsumer.assignment()
                // Only partitions previously paused by the caller
                .intersect(pausedPartitions)
                // Only those partitions that are not actively being synced.
                .filter { !partitionsToSync.containsKey(it.partition) }

            log.debug { "Rebalance occurred while polling from paused consumer, resuming partitions: $partitionsToResume" }
            eventConsumer.resume(partitionsToResume)

            throw StateAndEventConsumer.RebalanceInProgressException(message)
        }
    }

    private fun pauseEventConsumerAndWaitForFutureToFinish(future: CompletableFuture<*>, timeout: Long) {
        val pausePartitions = eventConsumer.assignment() - eventConsumer.paused()
        log.debug { "Pause partitions and wait for future to finish. Assignment: $pausePartitions" }
        eventConsumer.pause(pausePartitions)
        val maxWaitTime = System.currentTimeMillis() + timeout
        var done = future.isDone

        while (!done && (maxWaitTime > System.currentTimeMillis())) {
            pollWithCleanUpAndExceptionOnRebalance("Rebalance occurred while waiting for future to finish", pausePartitions) {
                future.cancel(true)
            }

            pollIntervalCutoff = getNextPollIntervalCutoff()
            pollAndUpdateStates(false)
            done = future.isDone
        }

        // Resume only those partitions currently assigned and previously paused.
        val partitionsToResume = eventConsumer.assignment().intersect(pausePartitions)
        log.debug { "Resume partitions. Finished wait for future[completed=${future.isDone}]. Assignment: $partitionsToResume" }
        eventConsumer.resume(partitionsToResume)
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

    override fun resetPollInterval() {
        if (System.currentTimeMillis() > pollIntervalCutoff) {
            // Here we pause each consumer in order to mark a poll to avoid a Kafka timeout without actually consuming
            // any records.
            val eventConsumerAssignmentToPause = eventConsumer.assignment() - eventConsumer.paused()
            val stateConsumerAssignmentToPause = stateConsumer.assignment() - stateConsumer.paused()

            log.debug { "Resetting poll interval. Pausing event consumer partitions: $eventConsumerAssignmentToPause" }
            eventConsumer.pause(eventConsumerAssignmentToPause)
            log.debug { "Resetting poll interval. Pausing state consumer partitions: $stateConsumerAssignmentToPause" }
            stateConsumer.pause(stateConsumerAssignmentToPause)

            pollWithCleanUpAndExceptionOnRebalance("Rebalance occurred while resetting poll interval", eventConsumerAssignmentToPause) {}

            stateConsumer.poll(PAUSED_POLL_TIMEOUT).forEach { state ->
                log.warn("Polling from paused stateConsumer has lost state with key: ${state.key}, this will likely cause " +
                        "execution problems for states with this id")
            }

            // Resume only those partitions currently assigned and previously paused.
            val eventConsumerAssignmentToResume = eventConsumer.assignment().intersect(eventConsumerAssignmentToPause)
            val stateConsumerAssignmentToResume = stateConsumer.assignment().intersect(stateConsumerAssignmentToPause)

            log.debug { "Reset of event consumer poll interval complete. Resuming event assignment: $eventConsumerAssignmentToResume" }
            eventConsumer.resume(eventConsumerAssignmentToResume)
            log.debug { "Reset of state consumer poll interval complete. Resuming state assignment: $stateConsumerAssignmentToResume" }
            stateConsumer.resume(stateConsumerAssignmentToResume)

            pollIntervalCutoff = getNextPollIntervalCutoff()
        }
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
