package net.corda.messaging.subscription.consumer.listener

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PubSubConsumerRebalanceListener<K : Any, V : Any>(
    topic: String,
    groupName: String,
    val consumer: CordaConsumer<K, V>
) : LoggingConsumerRebalanceListener(topic, groupName) {

    override val log: Logger = LoggerFactory.getLogger("${this.javaClass.name}-$topic-$groupName")

    /**
     * When assigned [partitions] set the consumer offset to the end of the partition.
     * The consumer will not read any messages produced to the topic between the last poll and latest
     * subscription or rebalance.
     */
    override fun onPartitionsAssigned(partitions: Collection<CordaTopicPartition>) {
        super.onPartitionsAssigned(partitions)
        consumer.seekToEnd(partitions)
    }
}
