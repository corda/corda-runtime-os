package net.corda.messaging.subscription.consumer.listener

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.messaging.subscription.consumer.StateAndEventConsumer
import net.corda.messaging.subscription.consumer.StateAndEventPartitionState
import net.corda.messaging.subscription.factory.MapFactory
import net.corda.schema.Schemas.getStateAndEventStateTopic
import net.corda.utilities.debug
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
internal class StateAndEventConsumerRebalanceListenerImpl<K : Any, S : Any, E : Any>(
    private val config: ResolvedSubscriptionConfig,
    private val mapFactory: MapFactory<K, Pair<Long, S>>,
    stateAndEventConsumer: StateAndEventConsumer<K, S, E>,
    private val partitionState: StateAndEventPartitionState<K, S>,
    private val stateAndEventListener: StateAndEventListener<K, S>? = null
) : StateAndEventConsumerRebalanceListener {

    private val log = LoggerFactory.getLogger(config.loggerName)

    private val currentStates = partitionState.currentStates
    private val partitionsToSync = partitionState.partitionsToSync

    private val eventConsumer = stateAndEventConsumer.eventConsumer
    private val stateConsumer = stateAndEventConsumer.stateConsumer

    /**
     *  This rebalance is called for the event consumer, though most of the work is to ensure the state consumer
     *  keeps up
     */
    override fun onPartitionsAssigned(partitions: Collection<CordaTopicPartition>) {
        val partitionIds = partitions.map{ it.partition }.joinToString(",")
        log.info("Consumer (${config.clientId}) group name ${config.group} for topic ${config.topic} partition assigned: $partitionIds.")

        val newStatePartitions = partitions.toStateTopics()
        val statePartitions = stateConsumer.assignment() + newStatePartitions
        stateConsumer.assign(statePartitions)
        stateConsumer.seekToBeginning(newStatePartitions)

        // Initialise the housekeeping here but the sync and updates
        // will be handled in the normal poll cycle
        val syncablePartitions = filterSyncablePartitions(newStatePartitions)
        log.debug { "Syncing the following new state partitions: $syncablePartitions" }
        partitionsToSync.putAll(syncablePartitions)
        eventConsumer.pause(syncablePartitions.map { CordaTopicPartition(config.topic, it.first) })

        statePartitions.forEach {
            currentStates.computeIfAbsent(it.partition) {
                mapFactory.createMap()
            }
        }

        partitionState.dirty = true
    }

    /**
     *  This rebalance is called for the event consumer, though most of the work is to ensure the state consumer
     *  keeps up
     */
    override fun onPartitionsRevoked(partitions: Collection<CordaTopicPartition>) {
        val partitionIds = partitions.map{ it.partition }.joinToString(",")
        log.info("Consumer (${config.clientId}) group name ${config.group} for topic ${config.topic} partition revoked: $partitionIds.")
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

        partitionState.dirty = true
    }


    override fun close() {
        stateAndEventListener?.let { listener ->
            currentStates.keys.forEach {
                listener.onPartitionLost(getStatesForPartition(it))
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

    private fun CordaTopicPartition.toStateTopic() =
        CordaTopicPartition(getStateAndEventStateTopic(config.topic), partition)

    private fun Collection<CordaTopicPartition>.toStateTopics(): List<CordaTopicPartition> = map { it.toStateTopic() }
}
