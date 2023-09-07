package net.corda.messagebus.api.producer.factory

import net.corda.libs.configuration.SmartConfig

interface MessageProducerFactory {
    fun createProducer(
        producerConfig: SmartConfig,
        targetConfig: SmartConfig,
        onSerializationError: ((ByteArray) -> Unit)? = null
    )
}
