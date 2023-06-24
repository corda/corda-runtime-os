package net.corda.messaging.subscription.consumer.listener

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.messaging.subscription.consumer.StateAndEventConsumer
import net.corda.messaging.subscription.consumer.StateAndEventPartitionState
import net.corda.messaging.subscription.factory.MapFactory

internal class RedisStateAndEventConsumerRebalnceListener<K : Any, S : Any, E : Any>(
    private val config: ResolvedSubscriptionConfig,
    private val mapFactory: MapFactory<K, Pair<Long, S>>,
    private val stateAndEventConsumer: StateAndEventConsumer<K, S, E>,
    private val partitionState: StateAndEventPartitionState<K, S>,
    private val stateAndEventListener: StateAndEventListener<K, S>? = null
) : StateAndEventConsumerRebalanceListener {
    override fun close() {
        TODO("Not yet implemented")
    }

    override fun onPartitionsRevoked(partitions: Collection<CordaTopicPartition>) {
        TODO("Not yet implemented")
    }

    override fun onPartitionsAssigned(partitions: Collection<CordaTopicPartition>) {
        TODO("Not yet implemented")
    }
}