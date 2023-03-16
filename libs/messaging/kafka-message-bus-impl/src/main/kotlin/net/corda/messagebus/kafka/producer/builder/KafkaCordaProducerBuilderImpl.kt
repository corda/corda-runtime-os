package net.corda.messagebus.kafka.producer.builder

import java.util.Properties
import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.configuration.ProducerConfig
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messagebus.kafka.config.MessageBusConfigResolver
import net.corda.messagebus.kafka.producer.CordaKafkaProducerImpl
import net.corda.messagebus.kafka.serialization.CordaAvroSerializerImpl
import net.corda.messagebus.kafka.utils.KafkaRetryUtils.executeKafkaActionWithRetry
import net.corda.messaging.api.chunking.MessagingChunkFactory
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.schema.configuration.MessagingConfig
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.utilities.classload.OsgiDelegatedClassLoader
import org.apache.kafka.clients.producer.KafkaProducer
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Builder for a Kafka Producer.
 * Initialises producer for transactions if publisherConfig contains an instanceId.
 * Producer uses avro for serialization.
 * Producer may send records in chunks if the record value exceeds a configured max size.
 * The chunking service is built via the [messagingChunkFactory]
 * If fatal exception is thrown in the construction of a KafKaProducer
 * then it is closed and exception is thrown as [CordaMessageAPIFatalException].
 */
@Component(service = [CordaProducerBuilder::class])
class KafkaCordaProducerBuilderImpl @Activate constructor(
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry,
    @Reference(service = MessagingChunkFactory::class)
    private val messagingChunkFactory: MessagingChunkFactory,
) : CordaProducerBuilder {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun createProducer(producerConfig: ProducerConfig, messageBusConfig: SmartConfig): CordaProducer {
        val configResolver = MessageBusConfigResolver(messageBusConfig.factory)
        val (resolvedConfig, kafkaProperties) = configResolver.resolve(messageBusConfig, producerConfig)

        return executeKafkaActionWithRetry(
            action = {
                val producer = createKafkaProducer(kafkaProperties)
                val maxAllowedMessageSize = messageBusConfig.getLong(MessagingConfig.MAX_ALLOWED_MSG_SIZE)
                val producerChunkService = messagingChunkFactory.createChunkSerializerService(maxAllowedMessageSize)
                CordaKafkaProducerImpl(resolvedConfig, producer, producerChunkService)
            },
            errorMessage = { "SubscriptionProducerBuilderImpl failed to producer with clientId ${producerConfig.clientId}, " +
                    "with configuration: $messageBusConfig" },
            log = log
        )
    }

    private fun createKafkaProducer(kafkaProperties: Properties): KafkaProducer<Any, Any> {
        val contextClassLoader = Thread.currentThread().contextClassLoader
        val currentBundle = FrameworkUtil.getBundle(KafkaProducer::class.java)

        return try {
            if (currentBundle != null) {
                Thread.currentThread().contextClassLoader = OsgiDelegatedClassLoader(currentBundle)
            }
            KafkaProducer(
                kafkaProperties,
                CordaAvroSerializerImpl(avroSchemaRegistry),
                CordaAvroSerializerImpl(avroSchemaRegistry)
            )
        } finally {
            Thread.currentThread().contextClassLoader = contextClassLoader
        }
    }
}
