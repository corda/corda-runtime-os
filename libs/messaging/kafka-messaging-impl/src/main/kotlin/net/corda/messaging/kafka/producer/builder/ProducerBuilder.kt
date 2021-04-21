package net.corda.messaging.kafka.producer.builder

import org.apache.kafka.clients.producer.Producer
import java.util.Properties

/**
 * Producer Builder Interface for creating Producers.
 */
interface ProducerBuilder<K, V> {

    /**
    * Generate producer with given properties.
    * @param properties properties to assign to producer.
    * @return Kafka Producer capable of publishing records to a topic.
    */
    fun createProducer(properties: Properties): Producer<K, V>
}
