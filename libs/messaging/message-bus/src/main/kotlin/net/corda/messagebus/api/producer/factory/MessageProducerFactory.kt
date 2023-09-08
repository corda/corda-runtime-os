package net.corda.messagebus.api.producer.factory

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.producer.MessageProducer

/**
 * Producer factory interface for creating message producers
 */
interface MessageProducerFactory {

    /**
     * Generate a message producer with given properties.
     * @param producerConfig The mandatory config for setting up producers.
     * @param targetConfig Configuration for connecting to the producer target (message bus, rpc endpoint, etc.)
     * @param onSerializationError a callback to execute when serialization fails.
     * @return [MessageProducer] capable of publishing records of any type to some external system.
     * @throws CordaMessageAPIFatalException if producer cannot be created.
     */
    fun createProducer(
        producerConfig: SmartConfig,
        targetConfig: SmartConfig,
        onSerializationError: ((ByteArray) -> Unit)? = null
    ): MessageProducer
}
