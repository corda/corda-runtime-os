package net.corda.messaging.mediator.slim

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.consumer.CordaConsumerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

class SlimMessageBusConsumer<K : Any, V : Any>(
    private val topic: String,
    private val consumer: CordaConsumer<K, V>,
    private val getOffsetOrDefault: (String, List<Int>) -> List<TopicOffset>,
) {
    private companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    fun subscribe() =
        consumer.subscribe(topic, SlimMessageBusConsumerRebalanceListener(topic, consumer, getOffsetOrDefault))

    fun close() = consumer.close()

    fun poll(timeout: Duration): List<CordaConsumerRecord<K, V>> {
        TODO("Not yet implemented")
    }

    private class SlimMessageBusConsumerRebalanceListener<K : Any, V : Any>(
        private val topic: String,
        private val consumer: CordaConsumer<K, V>,
        private val getOffsetOrDefault: (String, List<Int>) -> List<TopicOffset>,
    ) : CordaConsumerRebalanceListener {
        override fun onPartitionsRevoked(partitions: Collection<CordaTopicPartition>) {
            log.info("Partitions revoked '$topic' ('${partitions.joinToString(", ") { "${it.partition}" }}')")
        }

        override fun onPartitionsAssigned(partitions: Collection<CordaTopicPartition>) {
            log.info("Partitions assigned '$topic' ('${partitions.joinToString(", ") { "${it.partition}" }}')")

            // Load the offsets from state
            getOffsetOrDefault(topic, partitions.map { it.partition })
                .forEach { consumer.seek(it.topicPartition, it.offset) }
        }
    }
}

