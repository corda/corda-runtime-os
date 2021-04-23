package net.corda.messaging.kafka.producer.builder

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.publisher.config.PublisherConfig
import org.apache.kafka.clients.producer.Producer
import java.util.Properties

/**
 * Producer Builder Interface for creating Producers.
 */
interface ProducerBuilder<K, V> {

    /**
    * Generate kafka producer with given properties. Initialises the producer for transactions.
    * @param config config.
    * @param properties kafka properties to assign to producer.
    * @param publisherConfig config used to build a producer.
    * @return Kafka Producer capable of publishing records to a topic.
     * @throws CordaMessageAPIFatalException thrown if producer cannot be created.
    */
    fun createProducer(config: Config, properties: Properties, publisherConfig: PublisherConfig): Producer<K, V>

}
