package net.corda.messaging.kafka.subscription.consumer.builder.impl

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.listener.DurableConsumerRebalanceListener
import net.corda.messaging.kafka.subscription.consumer.listener.PubSubConsumerRebalanceListener
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.consumer.wrapper.impl.CordaKafkaConsumerImpl
import net.corda.schema.registry.AvroSchemaRegistry
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.KafkaException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.Properties

/**
 * Generate a Kafka Consumer.
 */
class CordaKafkaConsumerBuilderImpl<K : Any, V : Any>(
    private val kafkaConfig: Config,
    private val consumerProperties: Properties,
    private val avroSchemaRegistry: AvroSchemaRegistry,
) : ConsumerBuilder<K, V> {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun createPubSubConsumer(subscriptionConfig: SubscriptionConfig): CordaKafkaConsumer<K, V> {
        val consumer = createKafkaConsumer(subscriptionConfig)
        val listener = PubSubConsumerRebalanceListener(subscriptionConfig, consumer)
        return CordaKafkaConsumerImpl(kafkaConfig, subscriptionConfig, consumer, listener, avroSchemaRegistry)
    }

    override fun createDurableConsumer(
        subscriptionConfig: SubscriptionConfig): CordaKafkaConsumer<K, V> {
        val consumer = createKafkaConsumer(subscriptionConfig)
        val listener = DurableConsumerRebalanceListener(subscriptionConfig, consumer)
        return CordaKafkaConsumerImpl(kafkaConfig, subscriptionConfig, consumer, listener, avroSchemaRegistry)
    }

    /**
     * Create a [KafkaConsumer] based on the [subscriptionConfig].
     * @throws CordaMessageAPIFatalException fatal error.
     */
    private fun createKafkaConsumer(subscriptionConfig: SubscriptionConfig): Consumer<K, ByteBuffer> {
        val topic = subscriptionConfig.eventTopic
        val groupName = subscriptionConfig.groupName
        val contextClassLoader = Thread.currentThread().contextClassLoader

        return try {
            Thread.currentThread().contextClassLoader = null
            KafkaConsumer(consumerProperties)
        } catch (ex: KafkaException) {
            val message = "ConsumerBuilder failed to create and consumer for group $groupName, topic $topic."
            log.error(message, ex)
            throw CordaMessageAPIFatalException(message, ex)
        } finally {
            Thread.currentThread().contextClassLoader = contextClassLoader
        }
    }
}
