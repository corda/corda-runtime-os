package net.corda.messaging.kafka.subscription.consumer.listener

import com.typesafe.config.Config
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.kafka.properties.KafkaProperties
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.PRODUCER_TRANSACTIONAL_ID
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.TOPIC_NAME
import net.corda.messaging.kafka.subscription.Topic
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.consumer.wrapper.StateAndEventPartitionState
import net.corda.messaging.kafka.subscription.factory.SubscriptionMapFactory
import net.corda.v5.base.util.debug
import org.apache.kafka.clients.CommonClientConfigs.GROUP_ID_CONFIG
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
class StateAndEventRebalanceListener<K : Any, S : Any, E : Any>(
    private val config: Config,
    private val mapFactory: SubscriptionMapFactory<K, Pair<Long, S>>,
    private val eventConsumer: CordaKafkaConsumer<K, E>,
    private val stateConsumer: CordaKafkaConsumer<K, S>,
    private val partitionState: StateAndEventPartitionState<K, S>,
    private val stateAndEventListener: StateAndEventListener<K, S>? = null,
) : ConsumerRebalanceListener {

    companion object {
        private const val STATE_CONSUMER = "stateConsumer"
        private const val EVENT_CONSUMER = "eventConsumer"
        private const val STATE_TOPIC_NAME = "$STATE_CONSUMER.$TOPIC_NAME"
        private const val EVENT_GROUP_ID = "$EVENT_CONSUMER.${GROUP_ID_CONFIG}"
    }

    private val log = LoggerFactory.getLogger(
        "${config.getString(EVENT_GROUP_ID)}.${config.getString(PRODUCER_TRANSACTIONAL_ID)}"
    )

    private val topicPrefix = config.getString(KafkaProperties.TOPIC_PREFIX)
    private val eventTopic = Topic(topicPrefix, config.getString(TOPIC_NAME))
    private val stateTopic = Topic(topicPrefix, config.getString(STATE_TOPIC_NAME))

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
        partitionState.partitionsToSync.putAll(syncablePartitions)
        eventConsumer.pause(syncablePartitions.map { TopicPartition(eventTopic.topic, it.first) })

        statePartitions.forEach {
            partitionState.currentStates.computeIfAbsent(it.partition()) { mapFactory.createMap() }
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
            partitionState.partitionsToSync.remove(partitionId)

            partitionState.currentStates[partitionId]?.let { partitionStates ->
                stateAndEventListener?.onPartitionLost(getStatesForPartition(partitionId))
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
        return partitionState.currentStates[partitionId]?.map { state -> Pair(state.key, state.value.second) }?.toMap() ?: mapOf()
    }

    private fun TopicPartition.toStateTopic() = TopicPartition(stateTopic.topic, partition())
    private fun Collection<TopicPartition>.toStateTopics(): List<TopicPartition> = map { it.toStateTopic() }
}
