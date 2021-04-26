package net.corda.messaging.kafka.subscription.consumer.listener

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.common.TopicPartition
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class PubSubConsumerRebalanceListener<K, V> (private val consumer: Consumer<K, V>) : ConsumerRebalanceListener {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    /**
     * When a [consumer] is revoked [partitions] write to log.
     */
    override fun onPartitionsRevoked(partitions: MutableCollection<TopicPartition>) {
        log.info("partition revoked $partitions")
    }

    /**
     * When a [consumer] is assigned [partitions] set the offset to the end of the partition.
     * The consumer will not read any messages produced to the topic between the last poll and latest subscription or rebalance.
     */
    override fun onPartitionsAssigned(partitions: MutableCollection<TopicPartition>) {
        log.info("Partition assigned $partitions")
        consumer.seekToEnd(partitions)
    }
}