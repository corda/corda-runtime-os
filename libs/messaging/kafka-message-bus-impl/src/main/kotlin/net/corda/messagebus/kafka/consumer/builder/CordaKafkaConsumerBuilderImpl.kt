package net.corda.messagebus.kafka.consumer.builder

import java.util.Properties
import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.configuration.ConsumerConfig
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.consumer.builder.CordaConsumerBuilder
import net.corda.messagebus.kafka.config.MessageBusConfigResolver
import net.corda.messagebus.kafka.consumer.CordaKafkaConsumerImpl
import net.corda.messagebus.kafka.serialization.CordaAvroDeserializerImpl
import net.corda.messagebus.kafka.utils.KafkaRetryUtils.executeKafkaActionWithRetry
import net.corda.messaging.api.chunking.MessagingChunkFactory
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.utilities.classload.OsgiDelegatedClassLoader
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Generate a Corda Kafka Consumer.
 * Consumer uses deserializers that make use of the [avroSchemaRegistry]
 * Consumer may read records as chunks and will make use of a [ConsumerChunkDeserializerService] built using the
 * [messagingChunkFactory]
 */
@Component(service = [CordaConsumerBuilder::class])
class CordaKafkaConsumerBuilderImpl @Activate constructor(
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry,
    @Reference(service = MessagingChunkFactory::class)
    private val messagingChunkFactory: MessagingChunkFactory,
) : CordaConsumerBuilder {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun <K : Any, V : Any> createConsumer(
        consumerConfig: ConsumerConfig,
        messageBusConfig: SmartConfig,
        kClazz: Class<K>,
        vClazz: Class<V>,
        onSerializationError: (ByteArray) -> Unit,
        listener: CordaConsumerRebalanceListener?,
    ): CordaConsumer<K, V> {
        val resolver = MessageBusConfigResolver(messageBusConfig.factory)
        val (resolvedConfig, kafkaProperties) = resolver.resolve(messageBusConfig, consumerConfig)

        return executeKafkaActionWithRetry(
            action = {
                val keyDeserializer = CordaAvroDeserializerImpl(avroSchemaRegistry, onSerializationError, kClazz)
                val valueDeserializer = CordaAvroDeserializerImpl(avroSchemaRegistry, onSerializationError, vClazz)
                val consumerChunkDeserializerService =
                    messagingChunkFactory.createConsumerChunkDeserializerService(keyDeserializer, valueDeserializer, onSerializationError)
                val consumer = createKafkaConsumer(kafkaProperties, keyDeserializer, valueDeserializer)
                CordaKafkaConsumerImpl(resolvedConfig, consumer, listener, consumerChunkDeserializerService)
            },
            errorMessage = {
                "MessageBusConsumerBuilder failed to create consumer for group ${consumerConfig.group}, " +
                        "with configuration: $messageBusConfig"
            },
            log = log
        )
    }

    private fun <K : Any, V : Any> createKafkaConsumer(
        kafkaProperties: Properties,
        keyDeserializer: CordaAvroDeserializerImpl<K>,
        valueDeserializer: CordaAvroDeserializerImpl<V>,
    ): KafkaConsumer<Any, Any> {
        val contextClassLoader = Thread.currentThread().contextClassLoader

        val currentBundle = FrameworkUtil.getBundle(KafkaProducer::class.java)

        return try {
            if (currentBundle != null) {
                Thread.currentThread().contextClassLoader = OsgiDelegatedClassLoader(currentBundle)
            }
            KafkaConsumer(
                kafkaProperties,
                keyDeserializer,
                valueDeserializer
            )
        } finally {
            Thread.currentThread().contextClassLoader = contextClassLoader
        }
    }
}
