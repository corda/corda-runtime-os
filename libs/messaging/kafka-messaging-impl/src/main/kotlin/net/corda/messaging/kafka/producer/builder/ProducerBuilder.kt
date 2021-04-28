package net.corda.messaging.kafka.producer.builder

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import org.apache.kafka.clients.producer.Producer
import java.util.Properties

/**
 * Producer Builder Interface for creating Producers.
 */
interface ProducerBuilder<K, V> {

    /**
    * Generate kafka producer with given properties.
    * @param config configuration for this publisher and default kafka publisher configurations
    * @param properties additional kafka properties to assign to producer.
    * @return Kafka Producer capable of publishing records to a topic.
     * @throws CordaMessageAPIFatalException thrown if producer cannot be created.
    */
    fun createProducer(config: Config, properties: Properties): Producer<K, V>
}
