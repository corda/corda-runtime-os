package net.corda.messaging.kafka.subscription.consumer.wrapper.impl

import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.kafka.subscription.consumer.wrapper.ConsumerRecordAndMeta
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.consumer.wrapper.StateAndEventConsumer
import net.corda.messaging.kafka.subscription.consumer.wrapper.StateAndEventPartitionState
import net.corda.messaging.kafka.subscription.factory.SubscriptionMapFactory
import net.corda.messaging.kafka.types.StateAndEventConfig
import net.corda.messaging.kafka.types.Topic
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.trace
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import java.time.Clock

@Suppress("LongParameterList")
class StateAndEventConsumerImpl<K : Any, S : Any, E : Any>(
    private val config: StateAndEventConfig,
    private val mapFactory: SubscriptionMapFactory<K, Pair<Long, S>>,
    override val eventConsumer: CordaKafkaConsumer<K, E>,
    override val stateConsumer: CordaKafkaConsumer<K, S>,
    private val partitionState: StateAndEventPartitionState<K, S>,
    private val stateAndEventListener: StateAndEventListener<K, S>?,
) : StateAndEventConsumer<K, S, E> {

    private val log = LoggerFactory.getLogger(config.loggerName)

    private val consumerCloseTimeout = config.consumerCloseTimeout
    private val topicPrefix = config.topicPrefix
    private val eventTopic = Topic(topicPrefix, config.eventTopic)
    private val stateTopic = Topic(topicPrefix, config.stateTopic)

    private val currentStates = partitionState.currentStates
    private val partitionsToSync = partitionState.partitionsToSync

    override fun getValue(key: K): S? {
        currentStates.forEach {
            val state = it.value[key]
            if (state != null) {
                return state.second
            }
        }

        return null
    }

    override fun updateStatesAndSynchronizePartitions() {
        if (stateConsumer.assignment().isEmpty()) {
            log.trace { "State consumer has no partitions assigned." }
            return
        }

        val partitionsSynced = mutableSetOf<TopicPartition>()
        val states = stateConsumer.poll()
        for (state in states) {
            log.trace { "Updating state: $state" }
            updateInMemoryState(state)
            partitionsSynced.addAll(getSyncedEventPartitions())
        }

        if (partitionsSynced.isNotEmpty()) {
            resumeConsumerAndExecuteListener(partitionsSynced)
        }
    }

    override fun close() {
        eventConsumer.close(consumerCloseTimeout)
        stateConsumer.close(consumerCloseTimeout)
    }

    private fun getSyncedEventPartitions() : Set<TopicPartition> {
        val partitionsSynced = mutableSetOf<TopicPartition>()
        val statePartitionsToSync = partitionsToSync
        for (partition in statePartitionsToSync) {
            val partitionId = partition.key
            val stateTopicPartition = TopicPartition(stateTopic.topic, partitionId)
            val stateConsumerPollPosition = stateConsumer.position(stateTopicPartition)
            val endOffset = partition.value
            if (stateConsumerPollPosition >= endOffset) {
                log.trace {
                    "State partition $stateTopicPartition is now up to date. Poll position $stateConsumerPollPosition, recorded " +
                            "end offset $endOffset"
                }
                statePartitionsToSync.remove(partitionId)
                partitionsSynced.add(TopicPartition(eventTopic.topic, partitionId))
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

    private fun updateInMemoryState(state: ConsumerRecordAndMeta<K, S>) {
        currentStates[state.record.partition()]?.compute(state.record.key()) { _, currentState ->
            if (currentState == null || currentState.first <= state.record.timestamp()) {
                if (state.record.value() == null) {
                    // Removes this state from the map
                    null
                } else {
                    // Replaces/adds the new state
                    Pair(state.record.timestamp(), state.record.value())
                }
            } else {
                // Keeps the old state
                currentState
            }
        }
    }

    override fun onProcessorStateUpdated(updatedStates: MutableMap<Int, MutableMap<K, S?>>, clock: Clock) {
        val updatedStatesByKey = mutableMapOf<K, S?>()
        updatedStates.forEach { (partitionId, states) ->
            for (entry in states) {
                val key = entry.key
                val value = entry.value
                val currentStatesByPartition = currentStates.computeIfAbsent(partitionId) { mapFactory.createMap() }
                if (value != null) {
                    updatedStatesByKey[key] = value
                    currentStatesByPartition[key] = Pair(clock.instant().toEpochMilli(), value)
                } else {
                    updatedStatesByKey[key] = null
                    currentStatesByPartition.remove(key)
                }
            }
        }

        stateAndEventListener?.onPostCommit(updatedStatesByKey)
    }

    private fun getStatesForPartition(partitionId: Int): Map<K, S> {
        return currentStates[partitionId]?.map { state -> Pair(state.key, state.value.second) }?.toMap() ?: mapOf()
    }
}
