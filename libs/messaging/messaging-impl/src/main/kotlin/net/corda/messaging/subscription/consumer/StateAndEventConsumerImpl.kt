package net.corda.messaging.subscription.consumer

import io.micrometer.core.instrument.Gauge
import net.corda.lifecycle.Resource
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import net.corda.messagebus.api.consumer.CordaOffsetResetStrategy
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.messaging.constants.MetricsConstants
import net.corda.messaging.utils.tryGetResult
import net.corda.metrics.CordaMetrics
import net.corda.schema.Schemas.getStateAndEventStateTopic
import net.corda.tracing.wrapWithTracingExecutor
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

@Suppress("LongParameterList", "TooManyFunctions")
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

        // Event poll timeout
        private val EVENT_POLL_TIMEOUT = Duration.ofMillis(100)

        private const val STATE_TOPIC_SUFFIX = ".state"
    }

    //single threaded executor per state and event consumer
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        val thread = Thread(runnable)
        thread.isDaemon = true
        thread
    }

    private val log = LoggerFactory.getLogger("${this.javaClass.name}-${config.clientId}")

    private val maxPollInterval = config.processorTimeout.toMillis()
    private val initialProcessorTimeout = maxPollInterval / 4

    private val currentStates = partitionState.currentStates
    private val partitionsToSync = ConcurrentHashMap.newKeySet<CordaTopicPartition>()
    private val inSyncPartitions = ConcurrentHashMap.newKeySet<CordaTopicPartition>()

    // a map of gauges, each one recording the number of states currently in memory for each partition
    private val inMemoryStatesPerPartitionGaugeCache = ConcurrentHashMap<Int, Gauge>()
    private fun createGaugesForInMemoryStates(partitions: List<Int>) {
        partitions.forEach { partition ->
            inMemoryStatesPerPartitionGaugeCache.computeIfAbsent(partition) {
                CordaMetrics.Metric.Messaging.PartitionedConsumerInMemoryStore { currentStates[partition]?.size ?: 0 }
                    .builder()
                    .withTag(CordaMetrics.Tag.MessagePatternType, MetricsConstants.STATE_AND_EVENT_PATTERN_TYPE)
                    .withTag(CordaMetrics.Tag.MessagePatternClientId, config.clientId)
                    .withTag(CordaMetrics.Tag.Partition, "$partition")
                    .build()
            }
        }
    }

    private var pollIntervalCutoff = 0L

    override fun onPartitionsAssigned(partitions: Set<CordaTopicPartition>) {
        log.info("Assigning partitions: $partitions. Current in sync: $inSyncPartitions, current to be synced: $partitionsToSync")
        // Split these partitions into those that need synchronizing (i.e. those with states on) and those that do not
        val statePartitions = partitions.map { it.toStatePartition() }
        updateStateConsumerAssignment(partitions, StatePartitionOperation.ADD)
        val beginningOffsets = stateConsumer.beginningOffsets(statePartitions)
        val endOffsets = stateConsumer.endOffsets(statePartitions)
        val (needsSync, inSync) = partitions.partition {
            val statePartition = it.toStatePartition()
            val beginning = beginningOffsets[statePartition] ?: 0L
            val end = endOffsets[statePartition] ?: 0L
            beginning < end
        }

        log.info("The following partitions need to sync: $needsSync. The following partitions are in sync: $inSync")

        // Out of sync partitions need assigning to the state consumer to bring us into sync.
        partitionsToSync.addAll(needsSync)
        eventConsumer.pause(needsSync)
        stateConsumer.seekToBeginning(needsSync.map { it.toStatePartition() })

        // Mark all those already in sync as such.
        inSyncPartitions.addAll(inSync)
        updateStateConsumerAssignment(inSync, StatePartitionOperation.REMOVE)
        stateAndEventListener?.let { listener ->
            for (partition in inSync) {
                listener.onPartitionSynced(getStatesForPartition(partition.partition))
            }
        }
        createGaugesForInMemoryStates(partitions.map { it.partition })
    }

    private fun onPartitionsSynchronized(partitions: Set<CordaTopicPartition>) {
        // Remove the assignment from the state consumer. There's no need to read back from the state topic, as from
        // now on the pattern will rely on the in-memory state.
        log.info(
            "$partitions are now in sync. Resuming event feed. " +
                    "Current in sync: $inSyncPartitions, current to be synced: $partitionsToSync"
        )
        updateStateConsumerAssignment(partitions, StatePartitionOperation.REMOVE)

        eventConsumer.resume(partitions)
        partitionsToSync.removeAll(partitions)
        inSyncPartitions.addAll(partitions)

        stateAndEventListener?.let { listener ->
            for (partition in partitions) {
                listener.onPartitionSynced(getStatesForPartition(partition.partition))
            }
        }
    }

    override fun onPartitionsRevoked(partitions: Set<CordaTopicPartition>) {
        log.info(
            "Removing partitions: $partitions. " +
                    "Current in sync: $inSyncPartitions, current to be synced: $partitionsToSync"
        )
        // Remove any assignments and clear state from tracked partitions.
        updateStateConsumerAssignment(partitions, StatePartitionOperation.REMOVE)
        partitionsToSync.removeAll(partitions)
        inSyncPartitions.removeAll(partitions)
    }

    private enum class StatePartitionOperation {
        ADD,
        REMOVE
    }

    private fun updateStateConsumerAssignment(
        partitions: Collection<CordaTopicPartition>,
        operation: StatePartitionOperation
    ) {
        val statePartitions = partitions.map {
            it.toStatePartition()
        }.toSet()
        val oldAssignment = stateConsumer.assignment()
        val newAssignment = when (operation) {
            StatePartitionOperation.ADD -> oldAssignment + statePartitions
            StatePartitionOperation.REMOVE -> oldAssignment - statePartitions
        }
        log.info("Assigning partitions $newAssignment to the state consumer")
        stateConsumer.assign(newAssignment)
    }


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
            return
        }

        val states = stateConsumer.poll(STATE_POLL_TIMEOUT)
        states.forEach { state ->
            log.info("Processing state: $state")
            // This condition should always be true. This can however guard against a potential race where the partition
            // is revoked while states are being processed, resulting in the partition no longer being required to sync.
            val partition = CordaTopicPartition(state.topic.removeSuffix(STATE_TOPIC_SUFFIX), state.partition)
            if (partition in partitionsToSync) {
                updateInMemoryState(state)
            }
        }

        if (syncPartitions && partitionsToSync.isNotEmpty()) {
            log.info("State consumer in group ${config.group} is syncing partitions: $partitionsToSync")
            val syncedPartitions = removeAndReturnSyncedPartitions()
            if (syncedPartitions.isNotEmpty()) {
                onPartitionsSynchronized(syncedPartitions)
            }
        }
    }

    override fun close() {
        eventConsumer.close()
        stateConsumer.close()
        executor.shutdown()
        inMemoryStatesPerPartitionGaugeCache.values.forEach { CordaMetrics.registry.remove(it) }
    }

    private fun removeAndReturnSyncedPartitions(): Set<CordaTopicPartition> {
        val statePartitions = partitionsToSync.map { it.toStatePartition() }
        val endOffsets = stateConsumer.endOffsets(statePartitions)
        return partitionsToSync.filter {
            val statePartition = it.toStatePartition()
            val position = stateConsumer.position(statePartition)
            val endOffset = endOffsets[statePartition] ?: 0L
            position >= endOffset
        }.toSet()
    }

    override fun pollEvents(): List<CordaConsumerRecord<K, E>> {
        return when {
            inSyncPartitions.isNotEmpty() -> {
                eventConsumer.poll(EVENT_POLL_TIMEOUT).also {
                    log.info("Received ${it.size} events on keys ${it.joinToString { it.key.toString() }}")
                }
            }
            partitionsToSync.isEmpty() -> {
                // Call poll more frequently to trigger a rebalance of the event consumer.
                eventConsumer.poll(EVENT_POLL_TIMEOUT).also {
                    if (inSyncPartitions.isEmpty() && it.isNotEmpty()) {
                        // This shouldn't happen - it implies events have been returned from partitions that haven't
                        // been synced yet.
                        log.warn(
                            "${it.size} events on keys ${it.joinToString { it.key.toString() }} " +
                                    "were returned from non-synced partitions."
                        )
                    }
                }
            }
            System.currentTimeMillis() > pollIntervalCutoff -> {
                // Poll here to keep us in the consumer group.
                pollIntervalCutoff = getNextPollIntervalCutoff()
                eventConsumer.poll(Duration.ZERO).also {
                    if (inSyncPartitions.isEmpty() && it.isNotEmpty()) {
                        // This shouldn't happen - it implies events have been returned from partitions that haven't
                        // been synced yet.
                        log.warn(
                            "${it.size} events on keys ${it.joinToString { it.key.toString() }} " +
                                    "were returned from non-synced partitions."
                        )
                    }
                }
            }
            else -> {
                listOf()
            }
        }
    }

    override fun resetEventOffsetPosition() {
        log.info("Last committed offset position reset for the event consumer.")
        eventConsumer.resetToLastCommittedPositions(CordaOffsetResetStrategy.EARLIEST)
    }

    override fun waitForFunctionToFinish(function: () -> Any, maxTimeout: Long, timeoutErrorMessage: String): CompletableFuture<Any> {
        val future: CompletableFuture<Any> = CompletableFuture.supplyAsync(
            function,
            wrapWithTracingExecutor(executor))
        future.tryGetResult(getInitialConsumerTimeout())

        if (!future.isDone) {
            pauseEventConsumerAndWaitForFutureToFinish(future, maxTimeout)
        }

        if (!future.isDone) {
            future.cancel(true)
            log.warn(timeoutErrorMessage)
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
                .filter { !partitionsToSync.contains(it) }

            log.info("Rebalance occurred while polling from paused consumer, resuming partitions: $partitionsToResume")
            eventConsumer.resume(partitionsToResume)

            throw StateAndEventConsumer.RebalanceInProgressException(message)
        }
    }

    private fun pauseEventConsumerAndWaitForFutureToFinish(future: CompletableFuture<*>, timeout: Long) {
        val pausePartitions = eventConsumer.assignment() - eventConsumer.paused()
        log.info("Pause partitions and wait for future to finish. Assignment: $pausePartitions")
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
        log.info("Resume partitions. Finished wait for future[completed=${future.isDone}]. Assignment: $partitionsToResume")
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

            log.info("Resetting poll interval. Pausing event consumer partitions: $eventConsumerAssignmentToPause")
            eventConsumer.pause(eventConsumerAssignmentToPause)
            log.info("Resetting poll interval. Pausing state consumer partitions: $stateConsumerAssignmentToPause")
            stateConsumer.pause(stateConsumerAssignmentToPause)

            pollWithCleanUpAndExceptionOnRebalance("Rebalance occurred while resetting poll interval", eventConsumerAssignmentToPause) {}

            if (partitionsToSync.isNotEmpty()) {
                stateConsumer.poll(PAUSED_POLL_TIMEOUT).forEach { state ->
                    log.warn(
                        "Polling from paused stateConsumer has lost state with key: ${state.key}, this will likely cause " +
                                "execution problems for states with this id"
                    )
                }
            }

            // Resume only those partitions currently assigned and previously paused.
            val eventConsumerAssignmentToResume = eventConsumer.assignment().intersect(eventConsumerAssignmentToPause)
            val stateConsumerAssignmentToResume = stateConsumer.assignment().intersect(stateConsumerAssignmentToPause)

            log.info("Reset of event consumer poll interval complete. Resuming event assignment: $eventConsumerAssignmentToResume")
            eventConsumer.resume(eventConsumerAssignmentToResume)
            log.info("Reset of state consumer poll interval complete. Resuming state assignment: $stateConsumerAssignmentToResume")
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

    private fun CordaTopicPartition.toStatePartition() : CordaTopicPartition {
        return CordaTopicPartition(getStateAndEventStateTopic(this.topic), this.partition)
    }
}
