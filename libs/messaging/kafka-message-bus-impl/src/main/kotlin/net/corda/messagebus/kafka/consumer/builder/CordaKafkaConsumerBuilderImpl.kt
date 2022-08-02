package net.corda.messagebus.kafka.consumer.builder

import com.typesafe.config.ConfigRenderOptions
import net.corda.libs.configuration.SmartConfig
import net.corda.messagebus.api.configuration.ConsumerConfig
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.consumer.builder.CordaConsumerBuilder
import net.corda.messagebus.kafka.config.MessageBusConfigResolver
import net.corda.messagebus.kafka.consumer.CordaKafkaConsumerImpl
import net.corda.messagebus.kafka.serialization.CordaAvroDeserializerImpl
import net.corda.messagebus.kafka.utils.KafkaRetryUtils.executeKafkaActionWithRetry
import net.corda.messagebus.kafka.utils.OsgiDelegatedClassLoader
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.util.contextLogger
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import java.util.*

/**
 * Generate a Kafka Consumer.
 */
@Component(service = [CordaConsumerBuilder::class])
class CordaKafkaConsumerBuilderImpl @Activate constructor(
    @Reference(service = AvroSchemaRegistry::class)
    private val avroSchemaRegistry: AvroSchemaRegistry,
) : CordaConsumerBuilder {

    companion object {
        private val log: Logger = contextLogger()
    }

    override fun <K : Any, V : Any> createConsumer(
        consumerConfig: ConsumerConfig,
        messageBusConfig: SmartConfig,
        kClazz: Class<K>,
        vClazz: Class<V>,
        onSerializationError: (ByteArray) -> Unit,
        listener: CordaConsumerRebalanceListener?
    ): CordaConsumer<K, V> {
        val resolver = MessageBusConfigResolver(messageBusConfig.factory)
        val (resolvedConfig, kafkaProperties) = resolver.resolve(messageBusConfig, consumerConfig)
        println("QQQ in createConsumer A consumerConfig -> $consumerConfig")
        println("QQQ in createConsumer B messageBusConfig -> ${messageBusConfig.root().render(ConfigRenderOptions.concise())}")
        println("QQQ in createConsumer C kafkaProperties -> ${kafkaProperties}")
        println("QQQ in createConsumer D resolvedConfig -> ${resolvedConfig}")
        log.warn("QQQ in createConsumer 1 consumerConfig -> $consumerConfig")
        log.warn("QQQ in createConsumer 2 messageBusConfig -> ${messageBusConfig.root().render(ConfigRenderOptions.concise())}")
        log.warn("QQQ in createConsumer 3 kafkaProperties -> ${kafkaProperties}")
        log.warn("QQQ in createConsumer 4 resolvedConfig -> ${resolvedConfig}")

        return executeKafkaActionWithRetry(
            action = {
                val consumer = createKafkaConsumer(kafkaProperties, kClazz, vClazz, onSerializationError)
                CordaKafkaConsumerImpl(resolvedConfig, consumer, listener)
            },
            errorMessage = { "MessageBusConsumerBuilder failed to create consumer for group ${consumerConfig.group}, " +
                    "with configuration: $messageBusConfig" },
            log = log
        )
    }

    private fun <K : Any, V : Any> createKafkaConsumer(
        kafkaProperties: Properties,
        kClazz: Class<K>,
        vClazz: Class<V>,
        onSerializationError: (ByteArray) -> Unit,
    ): KafkaConsumer<K, V> {
        val contextClassLoader = Thread.currentThread().contextClassLoader

        val currentBundle = FrameworkUtil.getBundle(KafkaProducer::class.java)

        return try {
            if (currentBundle != null) {
                Thread.currentThread().contextClassLoader = OsgiDelegatedClassLoader(currentBundle)
            }
            KafkaConsumer(
                kafkaProperties,
                CordaAvroDeserializerImpl(avroSchemaRegistry, onSerializationError, kClazz),
                CordaAvroDeserializerImpl(avroSchemaRegistry, onSerializationError, vClazz)
            )
        } finally {
            Thread.currentThread().contextClassLoader = contextClassLoader
        }
    }
}
