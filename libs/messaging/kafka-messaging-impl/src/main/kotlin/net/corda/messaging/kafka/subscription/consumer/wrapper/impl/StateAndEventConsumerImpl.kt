package net.corda.messaging.kafka.subscription.consumer.wrapper.impl

import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.consumer.wrapper.StateAndEventConsumer
import net.corda.messaging.kafka.subscription.consumer.wrapper.StateAndEventPartitionState
import net.corda.messaging.kafka.types.StateAndEventConfig
import net.corda.messaging.kafka.utils.tryGetResult
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

@Suppress("LongParameterList")
class StateAndEventConsumerImpl<K : Any, S : Any, E : Any>(
    private val config: StateAndEventConfig,
    override val eventConsumer: CordaKafkaConsumer<K, E>,
    override val stateConsumer: CordaKafkaConsumer<K, S>,
    private val partitionState: StateAndEventPartitionState<K, S>,
    private val stateAndEventListener: StateAndEventListener<K, S>?
) : StateAndEventConsumer<K, S, E>, AutoCloseable {

    companion object {
        //short timeout for poll of paused partitions when waiting for processor to finish
        private val PAUSED_POLL_TIMEOUT = Duration.ofMillis(100)
    }

    //single threaded executor per state and event consumer
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        val thread = Thread(runnable)
        thread.isDaemon = true
        thread
    }

    private val log = LoggerFactory.getLogger(config.loggerName)

    private val consumerCloseTimeout = config.consumerCloseTimeout
    private val maxPollInterval = config.maxPollInterval
    private val initialProcessorTimeout = maxPollInterval / 4

    private val eventTopic = config.eventTopic
    private val stateTopic = config.stateTopic
    private val groupName = config.eventGroupName

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

        val partitionsSynced = mutableSetOf<TopicPartition>()
        val states = stateConsumer.poll()
        for (state in states) {
            log.debug { "Updating state: $state" }
            updateInMemoryState(state)
            partitionsSynced.addAll(getSyncedEventPartitions())
        }

        if (syncPartitions && partitionsSynced.isNotEmpty()) {
            resumeConsumerAndExecuteListener(partitionsSynced)
        }
    }

    override fun close() {
        eventConsumer.close(consumerCloseTimeout)
        stateConsumer.close(consumerCloseTimeout)
        executor.shutdown()
    }

    private fun getSyncedEventPartitions(): Set<TopicPartition> {
        val partitionsSynced = mutableSetOf<TopicPartition>()
        val statePartitionsToSync = partitionsToSync.toMap()
        for (partition in statePartitionsToSync) {
            val partitionId = partition.key
            val stateTopicPartition = TopicPartition(stateTopic, partitionId)
            val stateConsumerPollPosition = stateConsumer.position(stateTopicPartition)
            val endOffset = partition.value
            if (stateConsumerPollPosition >= endOffset) {
                log.trace {
                    "State partition $stateTopicPartition is now up to date. Poll position $stateConsumerPollPosition, recorded " +
                            "end offset $endOffset"
                }
                partitionsToSync.remove(partitionId)
                partitionsSynced.add(TopicPartition(eventTopic, partitionId))
            }
        }

        return partitionsSynced
    }

    private fun resumeConsumerAndExecuteListener(partitionsSynced: Set<TopicPartition>) {
        log.debug { "State consumer is up to date for $partitionsSynced.  Resuming event feed." }
        eventConsumer.resume(partitionsSynced)

        stateAndEventListener?.let { listener ->
            for (partition in partitionsSynced) {
                listener.onPartitionSynced(getStatesForPartition(partition.partition()))
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

    private fun updateInMemoryState(state: ConsumerRecord<K, S>) {
        currentStates[state.partition()]?.compute(state.key()) { _, currentState ->
            if (currentState == null || currentState.first <= state.timestamp()) {
                if (state.value() == null) {
                    // Removes this state from the map
                    null
                } else {
                    // Replaces/adds the new state
                    Pair(state.timestamp(), state.value())
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
                //will never be null, created on assignment in rebalance listener
                val currentStatesByPartition = currentStates[partitionId]
                    ?: throw CordaMessageAPIFatalException("Current State map for " +
                            "group $groupName on topic $stateTopic[$partitionId] is null.")
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
            val assignment = eventConsumer.assignment() - eventConsumer.paused()
            log.debug { "Resetting poll interval. Pausing assignment: $assignment"}
            eventConsumer.pause(assignment)
            eventConsumer.poll(PAUSED_POLL_TIMEOUT)
            stateConsumer.poll(PAUSED_POLL_TIMEOUT)
            pollIntervalCutoff = getNextPollIntervalCutoff()
            log.debug { "Reset of poll interval complete. Resuming assignment: $assignment"}
            eventConsumer.resume(assignment)
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
