package net.corda.messagebus.kafka.processor.builder

import io.confluent.parallelconsumer.ParallelConsumerOptions
import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.configuration.ConsumerConfig
import net.corda.messagebus.api.configuration.ProducerConfig
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.processor.CordaProcessor
import net.corda.messagebus.api.processor.builder.CordaProcessorBuilder
import net.corda.messagebus.kafka.config.MessageBusConfigResolver
import net.corda.messagebus.kafka.processor.CordaKafkaParallelProcessor
import net.corda.messagebus.kafka.serialization.CordaAvroDeserializerImpl
import net.corda.messagebus.kafka.utils.KafkaRetryUtils
import net.corda.messagebus.kafka.utils.createKafkaConsumer
import net.corda.messagebus.kafka.utils.createKafkaProducer
import net.corda.schema.registry.AvroSchemaRegistry
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component(service = [CordaProcessorBuilder::class])
class CordaKafkaParallelProcessorBuilder @Activate constructor(
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry,
) : CordaProcessorBuilder {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun <K : Any, V : Any> createProcessor(
        consumerConfig: ConsumerConfig,
        messageBusConfig: SmartConfig,
        kClazz: Class<K>,
        vClazz: Class<V>,
        processingOrder: CordaProcessorBuilder.ProcessingOrder,
        onSerializationError: (ByteArray) -> Unit,
        listener: CordaConsumerRebalanceListener?
    ) = createProcessor(
        consumerConfig,
        producerConfig = null,
        messageBusConfig,
        kClazz,
        vClazz,
        processingOrder,
        onSerializationError,
        listener
    )

    override fun <K : Any, V : Any> createProcessor(
        consumerConfig: ConsumerConfig,
        producerConfig: ProducerConfig?,
        messageBusConfig: SmartConfig,
        kClazz: Class<K>,
        vClazz: Class<V>,
        processingOrder: CordaProcessorBuilder.ProcessingOrder,
        onSerializationError: (ByteArray) -> Unit,
        listener: CordaConsumerRebalanceListener?,
    ): CordaProcessor<K, V> {

        val resolver = MessageBusConfigResolver(messageBusConfig.factory)
        val (resolvedConsumerConfig, consumerKafkaProperties) = resolver.resolve(messageBusConfig, consumerConfig)

        val consumer = KafkaRetryUtils.executeKafkaActionWithRetry(
            action = {
                val keyDeserializer = CordaAvroDeserializerImpl(avroSchemaRegistry, onSerializationError, kClazz)
                val valueDeserializer = CordaAvroDeserializerImpl(avroSchemaRegistry, onSerializationError, vClazz)
                createKafkaConsumer(consumerKafkaProperties, keyDeserializer, valueDeserializer)
            },
            errorMessage = {
                "CordaKafkaParallelProcessorBuilder failed to create consumer for group ${consumerConfig.group}, " +
                        "with configuration: $messageBusConfig"
            },
            log = log
        )

        val producer = producerConfig?.let {
            val (_, producerKafkaProperties) = resolver.resolve(messageBusConfig, producerConfig)
            KafkaRetryUtils.executeKafkaActionWithRetry(
                action = {
                    createKafkaProducer(producerKafkaProperties, onSerializationError, avroSchemaRegistry)
                },
                errorMessage = {
                    "CordaKafkaParallelProcessorBuilder failed to producer with clientId ${producerConfig.clientId}, " +
                            "with configuration: $messageBusConfig"
                },
                log = log
            )
        }

        val options = ParallelConsumerOptions.builder<Any, Any>()
            .ordering(when (processingOrder) {
                CordaProcessorBuilder.ProcessingOrder.KEY -> ParallelConsumerOptions.ProcessingOrder.KEY
                CordaProcessorBuilder.ProcessingOrder.PARTITION -> ParallelConsumerOptions.ProcessingOrder.PARTITION
            })
            .commitMode(when {
                producerConfig != null -> ParallelConsumerOptions.CommitMode.PERIODIC_TRANSACTIONAL_PRODUCER
                else -> ParallelConsumerOptions.CommitMode.PERIODIC_CONSUMER_SYNC
            })
            .maxConcurrency(3)
            .consumer(consumer)
            .producer(producer)
            .build()

        return CordaKafkaParallelProcessor(resolvedConsumerConfig, consumer, options)
    }
}
