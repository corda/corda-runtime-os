package net.corda.messaging.kafka.subscription.consumer.builder.impl

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.TOPIC_NAME
import net.corda.messaging.kafka.subscription.CordaAvroDeserializer
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.listener.DurableConsumerRebalanceListener
import net.corda.messaging.kafka.subscription.consumer.listener.PubSubConsumerRebalanceListener
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.consumer.wrapper.impl.CordaKafkaConsumerImpl
import net.corda.messaging.kafka.toProperties
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.internal.uncheckedCast
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Generate a Kafka Consumer.
 */
class CordaKafkaConsumerBuilderImpl<K : Any, V : Any>(
    private val avroSchemaRegistry: AvroSchemaRegistry,
) : ConsumerBuilder<K, V> {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun createPubSubConsumer(
        consumerConfig: Config,
        onError: (String, ByteArray) -> Unit
    ): CordaKafkaConsumer<K, V> {
        val consumer = createKafkaConsumer(consumerConfig, onError)
        val listener = PubSubConsumerRebalanceListener(
            consumerConfig.getString(TOPIC_NAME),
            consumerConfig.getString(CommonClientConfigs.GROUP_ID_CONFIG),
            consumer
        )
        return CordaKafkaConsumerImpl(consumerConfig, consumer, listener)
    }

    override fun createDurableConsumer(
        consumerConfig: Config,
        onError: (String, ByteArray) -> Unit,
        consumerRebalanceListener: ConsumerRebalanceListener?,
    ): CordaKafkaConsumer<K, V> {
        val consumer = createKafkaConsumer(consumerConfig, onError)
        val listener = consumerRebalanceListener ?: DurableConsumerRebalanceListener(
            consumerConfig.getString(TOPIC_NAME),
            consumerConfig.getString(CommonClientConfigs.GROUP_ID_CONFIG),
            consumer,
        )
        return CordaKafkaConsumerImpl(consumerConfig, consumer, listener)
    }

    /**
     * Create a [KafkaConsumer] based on the [subscriptionConfig].
     * @throws CordaMessageAPIFatalException fatal error.
     */
    override fun createCompactedConsumer(
        consumerConfig: Config,
        onError: (String, ByteArray) -> Unit,
    ): CordaKafkaConsumer<K, V> {
        val consumer = createKafkaConsumer(consumerConfig, onError)
        return CordaKafkaConsumerImpl(consumerConfig, consumer, null)
    }

    private fun createKafkaConsumer(
        consumerConfig: Config,
        onError: (String, ByteArray) -> Unit
    ): KafkaConsumer<K, V> {
        val topic = consumerConfig.getString(TOPIC_NAME)
        val groupName = consumerConfig.getString(CommonClientConfigs.GROUP_ID_CONFIG)
        val contextClassLoader = Thread.currentThread().contextClassLoader

        return try {
            Thread.currentThread().contextClassLoader = null
            KafkaConsumer(
                consumerConfig.toProperties(),
                uncheckedCast(StringDeserializer()),
                CordaAvroDeserializer<V>(avroSchemaRegistry, onError)
            )
        } catch (ex: KafkaException) {
            val message = "ConsumerBuilder failed to create and consumer for group $groupName, topic $topic."
            log.error(message, ex)
            throw CordaMessageAPIFatalException(message, ex)
        } finally {
            Thread.currentThread().contextClassLoader = contextClassLoader
        }
    }
}

