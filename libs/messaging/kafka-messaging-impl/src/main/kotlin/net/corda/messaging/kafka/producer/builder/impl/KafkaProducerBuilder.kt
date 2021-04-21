package net.corda.messaging.kafka.producer.builder.impl

import net.corda.messaging.kafka.producer.builder.ProducerBuilder
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import java.util.Properties

/**
 * Builder for a Kafka Producer.
 */
class KafkaProducerBuilder<K, V> : ProducerBuilder<K, V> {

    override fun createProducer(properties: Properties): Producer<K, V> {
        return KafkaProducer(properties)
    }
}
