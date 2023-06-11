package net.corda.messagebus.kafka.processor.builder

import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.configuration.ConsumerConfig
import net.corda.messagebus.api.configuration.ProducerConfig
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.processor.CordaProcessor
import net.corda.messagebus.api.processor.builder.CordaProcessorBuilder
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component

@Component(service = [CordaProcessorBuilder::class])
class DBCordaParallelProcessorBuilder @Activate constructor(
) : CordaProcessorBuilder {
    override fun <K : Any, V : Any> createProcessor(
        consumerConfig: ConsumerConfig,
        messageBusConfig: SmartConfig,
        kClazz: Class<K>,
        vClazz: Class<V>,
        processingOrder: CordaProcessorBuilder.ProcessingOrder,
        onSerializationError: (ByteArray) -> Unit,
        listener: CordaConsumerRebalanceListener?
    ): CordaProcessor<K, V> {
        TODO("Not yet implemented")
    }

    override fun <K : Any, V : Any> createProcessor(
        consumerConfig: ConsumerConfig,
        producerConfig: ProducerConfig?,
        messageBusConfig: SmartConfig,
        kClazz: Class<K>,
        vClazz: Class<V>,
        processingOrder: CordaProcessorBuilder.ProcessingOrder,
        onSerializationError: (ByteArray) -> Unit,
        listener: CordaConsumerRebalanceListener?
    ): CordaProcessor<K, V> {
        TODO("Not yet implemented")
    }
}
