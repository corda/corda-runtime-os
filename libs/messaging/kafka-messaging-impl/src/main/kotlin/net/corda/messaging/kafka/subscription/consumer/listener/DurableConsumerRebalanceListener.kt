package net.corda.messaging.kafka.subscription.consumer.listener

import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.common.TopicPartition
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DurableConsumerRebalanceListener<K, V>(
    subscriptionConfig: SubscriptionConfig,
    private val consumer: Consumer<K, V>,
) : ConsumerRebalanceListener {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    private val topic = subscriptionConfig.eventTopic
    private val groupName = subscriptionConfig.groupName

    /**
     * When a [consumer] is revoked [partitions] write to log.
     */
    override fun onPartitionsRevoked(partitions: MutableCollection<TopicPartition>) {
        val partitionIds = partitions.map{it.partition()}.joinToString(",")
        log.info("Consumer group name $groupName for topic $topic partition revoked: $partitionIds.")
    }

    /**
     * When a [consumer] is assigned [partitions].
     */
    override fun onPartitionsAssigned(partitions: MutableCollection<TopicPartition>) {
        val partitionIds = partitions.map{it.partition()}.joinToString(",")
        log.info("Consumer group name $groupName for topic $topic partition assigned: $partitionIds.")
    }
}
