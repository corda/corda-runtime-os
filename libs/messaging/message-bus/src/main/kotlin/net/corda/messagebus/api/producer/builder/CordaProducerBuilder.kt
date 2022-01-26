package net.corda.messagebus.api.producer.builder

import com.typesafe.config.Config
import net.corda.messagebus.api.producer.CordaProducer

/**
 * Producer Builder Interface for creating Producers.
 */
interface CordaProducerBuilder {

    /**
    * Generate kafka producer with given properties.
    * @return Kafka Producer capable of publishing records of any type to any topic.
    * @throws CordaMessageAPIFatalException thrown if producer cannot be created.
    */
    fun createProducer(producerConfig: Config): CordaProducer
}
