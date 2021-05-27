package net.corda.messaging.kafka.producer.builder

import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer

/**
 * Producer Builder Interface for creating Producers.
 */
interface ProducerBuilder {

    /**
    * Generate kafka producer with given properties.
    * @return Kafka Producer capable of publishing records to topics
    * @throws CordaMessageAPIFatalException thrown if producer cannot be created.
    */
    fun createProducer(): CordaKafkaProducer
}
