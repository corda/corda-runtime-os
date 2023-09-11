package net.corda.messagebus.kafka.producer.factory

import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics
import java.util.Properties
import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.configuration.ProducerConfig
import net.corda.messagebus.api.constants.ProducerRoles
import net.corda.messagebus.api.producer.MessageProducer
import net.corda.messagebus.api.producer.factory.MessageProducerFactory
import net.corda.messagebus.kafka.config.MessageBusConfigResolver
import net.corda.messagebus.kafka.producer.KafkaMessageProducerImpl
import net.corda.messagebus.kafka.serialization.CordaAvroSerializerImpl
import net.corda.messagebus.kafka.utils.KafkaRetryUtils.executeKafkaActionWithRetry
import net.corda.messaging.api.chunking.MessagingChunkFactory
import net.corda.schema.configuration.MessagingConfig
import net.corda.schema.registry.AvroSchemaRegistry
import org.apache.kafka.clients.producer.KafkaProducer
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.wiring.BundleWiring
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component(service = [MessageProducerFactory::class])
class KafkaMessageProducerFactoryImpl(
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry,
    @Reference(service = MessagingChunkFactory::class)
    private val messagingChunkFactory: MessagingChunkFactory,
) : MessageProducerFactory {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun createProducer(
        producerConfig: SmartConfig,
        targetConfig: SmartConfig,
        onSerializationError: ((ByteArray) -> Unit)?
    ): MessageProducer {
        val cordaProducerConfig = producerConfig.toProducerConfig()
        val configResolver = MessageBusConfigResolver(targetConfig.factory)
        val (resolvedConfig, kafkaProperties) = configResolver.resolve(targetConfig, cordaProducerConfig)

        return executeKafkaActionWithRetry(
            action = {
                val producer = createKafkaProducer(kafkaProperties, onSerializationError)
                val maxAllowedMessageSize = targetConfig.getLong(MessagingConfig.MAX_ALLOWED_MSG_SIZE)
                val producerChunkService = messagingChunkFactory.createChunkSerializerService(maxAllowedMessageSize)
                KafkaMessageProducerImpl(
                    resolvedConfig,
                    producer,
                    producerChunkService,
                    KafkaClientMetrics(producer)
                )
            },
            errorMessage = {
                "KafkaMessageProducerFactoryImpl failed to producer with clientId ${cordaProducerConfig.clientId}, " +
                        "with configuration: $targetConfig"
            },
            log = log
        )
    }

    private fun createKafkaProducer(
        kafkaProperties: Properties,
        onSerializationError: ((ByteArray) -> Unit)?
    ) : KafkaProducer<Any, Any> {
        val contextClassLoader = Thread.currentThread().contextClassLoader
        val currentBundle = FrameworkUtil.getBundle(KafkaProducer::class.java)

        return try {
            if (currentBundle != null) {
                Thread.currentThread().contextClassLoader = currentBundle.adapt(BundleWiring::class.java).classLoader
            }
            KafkaProducer(
                kafkaProperties,
                CordaAvroSerializerImpl(avroSchemaRegistry, onSerializationError),
                CordaAvroSerializerImpl(avroSchemaRegistry, onSerializationError)
            )
        } finally {
            Thread.currentThread().contextClassLoader = contextClassLoader
        }
    }

    private fun SmartConfig.toProducerConfig(): ProducerConfig {
        return ProducerConfig(
            clientId = getString("clientId"),
            instanceId = getInt("instanceId"),
            transactional = getBoolean("transactional"),
            role = ProducerRoles.valueOf(getString("role")),
            throwOnSerializationError =
                if (hasPath("throwOnSerializationError")) getBoolean("throwOnSerializationError") else true
        )
    }
}
