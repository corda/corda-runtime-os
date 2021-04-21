package net.corda.messaging.kafka.subscription.consumer.impl

import net.corda.messaging.kafka.subscription.consumer.ConsumerBuilder
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.util.Properties

/**
 * Generate a Kafka PubSub Consumer Builder.
 */
class PubSubConsumerBuilder<K, V> : ConsumerBuilder<K, V> {

    override fun createConsumer(properties: Properties): Consumer<K, V> {
        return KafkaConsumer(properties)
    }
}
