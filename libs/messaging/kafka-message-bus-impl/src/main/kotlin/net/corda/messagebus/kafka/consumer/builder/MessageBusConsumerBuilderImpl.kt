package net.corda.messagebus.kafka.consumer.builder

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.messagebus.api.configuration.ConsumerConfig
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.consumer.builder.MessageBusConsumerBuilder
import net.corda.messagebus.kafka.config.ConfigResolverImpl
import net.corda.messagebus.kafka.consumer.CordaKafkaConsumerImpl
import net.corda.messagebus.kafka.serialization.CordaAvroDeserializerImpl
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.util.contextLogger
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.KafkaException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.util.Properties

/**
 * Generate a Kafka Consumer.
 */
@Component(service = [MessageBusConsumerBuilder::class])
class MessageBusConsumerBuilderImpl @Activate constructor(
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry,
) : MessageBusConsumerBuilder {

    companion object {
        private val log: Logger = contextLogger()

        private const val GROUP_PATH = "group"
        private const val CLIENT_ID_PATH = "clientId"
        private const val TOPIC_PREFIX_PATH = "topicPrefix"
    }

    override fun <K : Any, V : Any> createConsumer(
        consumerConfig: ConsumerConfig,
        busConfig: SmartConfig,
        kClazz: Class<K>,
        vClazz: Class<V>,
        onSerializationError: (ByteArray) -> Unit,
        listener: CordaConsumerRebalanceListener?
    ): CordaConsumer<K, V> {
        val resolver = ConfigResolverImpl(busConfig.factory)
        val kafkaProperties = resolver.resolve(busConfig, consumerConfig.role, consumerConfig.toSmartConfig(busConfig.factory))
        return try {
            val consumer = createKafkaConsumer(kafkaProperties, kClazz, vClazz, onSerializationError)
            return CordaKafkaConsumerImpl(consumerConfig, consumer, listener)
        } catch (ex: KafkaException) {
            val message = "MessageBusConsumerBuilder failed to create consumer for group ${consumerConfig.group}"
            log.error(message, ex)
            throw CordaMessageAPIFatalException(message, ex)
        }
    }


    private fun <K : Any, V : Any> createKafkaConsumer(
        kafkaProperties: Properties,
        kClazz: Class<K>,
        vClazz: Class<V>,
        onSerializationError: (ByteArray) -> Unit,
    ): KafkaConsumer<K, V> {
        val contextClassLoader = Thread.currentThread().contextClassLoader

        return try {
            Thread.currentThread().contextClassLoader = null
            KafkaConsumer(
                kafkaProperties,
                CordaAvroDeserializerImpl(avroSchemaRegistry, onSerializationError, kClazz),
                CordaAvroDeserializerImpl(avroSchemaRegistry, onSerializationError, vClazz)
            )
        } finally {
            Thread.currentThread().contextClassLoader = contextClassLoader
        }
    }

    private fun ConsumerConfig.toSmartConfig(smartConfigFactory: SmartConfigFactory): SmartConfig {
        return smartConfigFactory.create(ConfigFactory.parseMap(mapOf(
           GROUP_PATH to group,
           CLIENT_ID_PATH to clientId,
           TOPIC_PREFIX_PATH to topicPrefix
        )))
    }
}
