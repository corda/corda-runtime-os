package net.corda.messagebus.kafka.consumer.builder

import com.typesafe.config.Config
import net.corda.messagebus.api.configuration.ConfigProperties.Companion.TOPIC_NAME
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.consumer.builder.MessageBusConsumerBuilder
import net.corda.messagebus.kafka.consumer.CordaKafkaConsumerImpl
import net.corda.messagebus.kafka.serialization.CordaAvroDeserializerImpl
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.kafka.utils.toProperties
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.util.contextLogger
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.KafkaException
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger

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
    }

    override fun <K : Any, V : Any> createConsumer(
        consumerConfig: Config,
        kClazz: Class<K>,
        vClazz: Class<V>,
        onSerializationError: (ByteArray) -> Unit,
        listener: CordaConsumerRebalanceListener?
    ): CordaConsumer<K, V> {
        val consumer = createKafkaConsumer(consumerConfig, kClazz, vClazz, onSerializationError)
        return CordaKafkaConsumerImpl(consumerConfig, consumer, listener)
    }


    fun <K : Any, V : Any> createKafkaConsumer(
        consumerConfig: Config,
        kClazz: Class<K>,
        vClazz: Class<V>,
        onSerializationError: (ByteArray) -> Unit,
    ): KafkaConsumer<K, V> {
        val topic = consumerConfig.getString(TOPIC_NAME)
        val groupName = consumerConfig.getString(CommonClientConfigs.GROUP_ID_CONFIG)
        val contextClassLoader = Thread.currentThread().contextClassLoader

        return try {
            Thread.currentThread().contextClassLoader = null
            KafkaConsumer(
                consumerConfig.toProperties(),
                CordaAvroDeserializerImpl(avroSchemaRegistry, onSerializationError, kClazz),
                CordaAvroDeserializerImpl(avroSchemaRegistry, onSerializationError, vClazz)
            )
        } catch (ex: KafkaException) {
            val message = "MessageBusConsumerBuilder failed to create consumer for group $groupName, topic $topic."
            log.error(message, ex)
            throw CordaMessageAPIFatalException(message, ex)
        } finally {
            Thread.currentThread().contextClassLoader = contextClassLoader
        }
    }
}
