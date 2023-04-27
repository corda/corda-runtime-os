package net.corda.messagebus.api.producer.builder

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.configuration.ProducerConfig
import net.corda.messagebus.api.producer.CordaProducer

/**
 * Producer Builder Interface for creating Producers.
 */
interface CordaProducerBuilder {

    /**
     * Generate kafka producer with given properties.
     * @param producerConfig The mandatory config for setting up producers
     * @param messageBusConfig Configuration for connecting to the message bus and controlling its behaviour.
     * @param onSerializationError a callback to receive messages that fail to serialize.  In the producer feed
     *        these will show up as records with a null value, which means they should be removed from any maps.
     * @return Producer capable of publishing records of any type to any topic.
     * @throws CordaMessageAPIFatalException thrown if producer cannot be created.
     */
    fun createProducer(
        producerConfig: ProducerConfig,
        messageBusConfig: SmartConfig,
        onSerializationError: ((ByteArray) -> Unit)? = null
    ): CordaProducer
}
