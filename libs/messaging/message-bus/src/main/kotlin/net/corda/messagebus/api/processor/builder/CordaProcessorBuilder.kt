package net.corda.messagebus.api.processor.builder

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.configuration.ConsumerConfig
import net.corda.messagebus.api.configuration.ProducerConfig
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.processor.CordaProcessor

interface CordaProcessorBuilder {
    enum class ProcessingOrder {
        KEY, PARTITION
    }

    @Suppress("LongParameterList")
    fun <K : Any, V : Any> createProcessor(
        consumerConfig: ConsumerConfig,
        messageBusConfig: SmartConfig,
        kClazz: Class<K>,
        vClazz: Class<V>,
        processingOrder: ProcessingOrder,
        onSerializationError: (ByteArray) -> Unit = {_ ->},
        listener: CordaConsumerRebalanceListener? = null
    ) : CordaProcessor<K, V>

    @Suppress("LongParameterList")
    fun <K : Any, V : Any> createProcessor(
        consumerConfig: ConsumerConfig,
        producerConfig: ProducerConfig?,
        messageBusConfig: SmartConfig,
        kClazz: Class<K>,
        vClazz: Class<V>,
        processingOrder: ProcessingOrder,
        onSerializationError: (ByteArray) -> Unit = {_ ->},
        listener: CordaConsumerRebalanceListener? = null
    ) : CordaProcessor<K, V>
}
