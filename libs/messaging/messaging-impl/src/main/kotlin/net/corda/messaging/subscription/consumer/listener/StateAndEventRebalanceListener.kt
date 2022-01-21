package net.corda.messaging.subscription.consumer.listener

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.subscription.config.StateAndEventConfig
import net.corda.messaging.subscription.consumer.StateAndEventConsumer
import net.corda.messaging.subscription.consumer.StateAndEventPartitionState
import net.corda.messaging.subscription.factory.MapFactory
import net.corda.v5.base.util.debug
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
class StateAndEventRebalanceListener<K : Any, S : Any, E : Any>(
    private val config: StateAndEventConfig,
    private val mapFactory: MapFactory<K, Pair<Long, S>>,
    private val stateAndEventConsumer: StateAndEventConsumer<K, S, E>,
    private val partitionState: StateAndEventPartitionState<K, S>,
    private val stateAndEventListener: StateAndEventListener<K, S>? = null
) : CordaConsumerRebalanceListener {

    private val log = LoggerFactory.getLogger(config.loggerName)

    private val eventTopic = config.eventTopic
    private val stateTopic = config.stateTopic

    private val currentStates = partitionState.currentStates
    private val partitionsToSync = partitionState.partitionsToSync

    private val eventConsumer: CordaConsumer<K, E> = stateAndEventConsumer.eventConsumer
    private val stateConsumer: CordaConsumer<K, S> = stateAndEventConsumer.stateConsumer

    /**
     *  This rebalance is called for the event consumer, though most of the work is to ensure the state consumer
     *  keeps up
     */
    override fun onPartitionsAssigned(partitions: Collection<CordaTopicPartition>) {
        log.debug { "Updating state partitions to match new event partitions: $partitions" }
        val newStatePartitions = partitions.toStateTopics()
        val statePartitions = stateConsumer.assignment() + newStatePartitions
        stateConsumer.assign(statePartitions)
        stateConsumer.seekToBeginning(newStatePartitions)

        // Initialise the housekeeping here but the sync and updates
        // will be handled in the normal poll cycle
        val syncablePartitions = filterSyncablePartitions(newStatePartitions)
        log.debug { "Syncing the following new state partitions: $syncablePartitions" }
        partitionsToSync.putAll(syncablePartitions)
        eventConsumer.pause(syncablePartitions.map { CordaTopicPartition(eventTopic, it.first) })

        statePartitions.forEach {
            currentStates.computeIfAbsent(it.partition) {
                mapFactory.createMap()
            }
        }
    }

    /**
     *  This rebalance is called for the event consumer, though most of the work is to ensure the state consumer
     *  keeps up
     */
    override fun onPartitionsRevoked(partitions: Collection<CordaTopicPartition>) {
        log.debug { "Updating state partitions to match removed event partitions: $partitions" }
        val removedStatePartitions = partitions.toStateTopics()
        val statePartitions = stateConsumer.assignment() - removedStatePartitions.toSet()
        stateConsumer.assign(statePartitions)
        for (topicPartition in removedStatePartitions) {
            val partitionId = topicPartition.partition
            partitionsToSync.remove(partitionId)

            stateAndEventListener?.let { listener ->
                listener.onPartitionLost(getStatesForPartition(partitionId))
            }

            currentStates[partitionId]?.let { partitionStates ->
                mapFactory.destroyMap(partitionStates)
            }
        }
    }

    private fun filterSyncablePartitions(newStatePartitions: List<CordaTopicPartition>): List<Pair<Int, Long>> {
        val beginningOffsets = stateConsumer.beginningOffsets(newStatePartitions)
        val endOffsets = stateConsumer.endOffsets(newStatePartitions)
        return newStatePartitions.mapNotNull {
            val beginningOffset = beginningOffsets[it] ?: 0
            val endOffset = endOffsets[it] ?: 0
            if (beginningOffset < endOffset) {
                Pair(it.partition, endOffset)
            } else {
                null
            }
        }
    }

    private fun getStatesForPartition(partitionId: Int): Map<K, S> {
        return currentStates[partitionId]?.map { state -> Pair(state.key, state.value.second) }?.toMap() ?: mapOf()
    }

    private fun CordaTopicPartition.toStateTopic() = CordaTopicPartition(stateTopic, partition)
    private fun Collection<CordaTopicPartition>.toStateTopics(): List<CordaTopicPartition> = map { it.toStateTopic() }
}
