package net.corda.messaging.subscription.consumer.listener

import net.corda.messagebus.api.CordaTopicPartition

internal class RedisStateAndEventConsumerRebalnceListener : StateAndEventConsumerRebalanceListener {
    override fun close() {
        // Let's do nothing here
    }

    override fun onPartitionsRevoked(partitions: Collection<CordaTopicPartition>) {
        // Let's do nothing here
    }

    override fun onPartitionsAssigned(partitions: Collection<CordaTopicPartition>) {
        // Let's do nothing here
    }
}