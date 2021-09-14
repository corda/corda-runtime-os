package net.corda.messaging.kafka.subscription.consumer.listener

import com.typesafe.config.Config
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.kafka.properties.ConfigProperties
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.EVENT_GROUP_ID
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.PRODUCER_TRANSACTIONAL_ID
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.STATE_TOPIC_NAME
import net.corda.messaging.kafka.properties.ConfigProperties.Companion.TOPIC_NAME
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.consumer.wrapper.StateAndEventConsumer
import net.corda.messaging.kafka.subscription.consumer.wrapper.StateAndEventPartitionState
import net.corda.messaging.kafka.subscription.factory.SubscriptionMapFactory
import net.corda.messaging.kafka.types.Topic
import net.corda.v5.base.util.debug
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory

class StateAndEventRebalanceListener<K : Any, S : Any, E : Any>(
    private val config: Config,
    private val mapFactory: SubscriptionMapFactory<K, Pair<Long, S>>,
    private val stateAndEventConsumer: StateAndEventConsumer<K, S, E>,
    private val partitionState: StateAndEventPartitionState<K, S>,
    private val stateAndEventListener: StateAndEventListener<K, S>? = null,
) : ConsumerRebalanceListener {

    private val log = LoggerFactory.getLogger(
        "${config.getString(EVENT_GROUP_ID)}.${config.getString(PRODUCER_TRANSACTIONAL_ID)}"
    )

    private val topicPrefix = config.getString(ConfigProperties.TOPIC_PREFIX)
    private val eventTopic = Topic(topicPrefix, config.getString(TOPIC_NAME))
    private val stateTopic = Topic(topicPrefix, config.getString(STATE_TOPIC_NAME))
    private val listenerTimeout = config.getLong(KafkaProperties.LISTENER_TIMEOUT)

    private val currentStates = partitionState.currentStates
    private val partitionsToSync = partitionState.partitionsToSync

    private val eventConsumer: CordaKafkaConsumer<K, E> = stateAndEventConsumer.eventConsumer
    private val stateConsumer: CordaKafkaConsumer<K, S> = stateAndEventConsumer.stateConsumer

    /**
     *  This rebalance is called for the event consumer, though most of the work is to ensure the state consumer
     *  keeps up
     */
    override fun onPartitionsAssigned(newEventPartitions: Collection<TopicPartition>) {
        log.debug { "Updating state partitions to match new event partitions: $newEventPartitions" }
        val newStatePartitions = newEventPartitions.toStateTopics()
        val statePartitions = stateConsumer.assignment() + newStatePartitions
        stateConsumer.assign(statePartitions)
        stateConsumer.seekToBeginning(newStatePartitions)

        // Initialise the housekeeping here but the sync and updates
        // will be handled in the normal poll cycle
        val syncablePartitions = filterSyncablePartitions(newStatePartitions)
        log.debug { "Syncing the following new state partitions: $syncablePartitions" }
        partitionsToSync.putAll(syncablePartitions)
        eventConsumer.pause(syncablePartitions.map { TopicPartition(eventTopic.topic, it.first) })

        statePartitions.forEach {
            currentStates.computeIfAbsent(it.partition()) { mapFactory.createMap() }
        }
    }

    /**
     *  This rebalance is called for the event consumer, though most of the work is to ensure the state consumer
     *  keeps up
     */
    override fun onPartitionsRevoked(removedEventPartitions: Collection<TopicPartition>) {
        log.debug { "Updating state partitions to match removed event partitions: $removedEventPartitions" }
        val removedStatePartitions = removedEventPartitions.toStateTopics()
        val statePartitions = stateConsumer.assignment() - removedStatePartitions
        stateConsumer.assign(statePartitions)
        for (topicPartition in removedStatePartitions) {
            val partitionId = topicPartition.partition()
            partitionsToSync.remove(partitionId)

            currentStates[partitionId]?.let { partitionStates ->
                stateAndEventListener?.let { listener ->
                    stateAndEventConsumer.waitForFunctionToFinish(
                        { listener.onPartitionLost(getStatesForPartition(partitionId)) },
                        listenerTimeout,
                        "StateAndEventListener timed out for onPartitionLost operation on partition $topicPartition"
                    )
                }

                mapFactory.destroyMap(partitionStates)
            }
        }
    }

    private fun filterSyncablePartitions(newStatePartitions: List<TopicPartition>): List<Pair<Int, Long>> {
        val beginningOffsets = stateConsumer.beginningOffsets(newStatePartitions)
        val endOffsets = stateConsumer.endOffsets(newStatePartitions)
        return newStatePartitions.mapNotNull {
            val beginningOffset = beginningOffsets[it] ?: 0
            val endOffset = endOffsets[it] ?: 0
            if (beginningOffset < endOffset) {
                Pair(it.partition(), endOffset)
            } else {
                null
            }
        }
    }

    private fun getStatesForPartition(partitionId: Int): Map<K, S> {
        return currentStates[partitionId]?.map { state -> Pair(state.key, state.value.second) }?.toMap() ?: mapOf()
    }

    private fun TopicPartition.toStateTopic() = TopicPartition(stateTopic.topic, partition())
    private fun Collection<TopicPartition>.toStateTopics(): List<TopicPartition> = map { it.toStateTopic() }
}
