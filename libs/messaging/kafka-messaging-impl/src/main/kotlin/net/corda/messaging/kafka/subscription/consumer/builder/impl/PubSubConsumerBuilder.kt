package net.corda.messaging.kafka.subscription.consumer.builder.impl

import com.typesafe.config.Config
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.consumer.listener.PubSubConsumerRebalanceListener
import net.corda.messaging.kafka.subscription.consumer.wrapper.impl.CordaKafkaConsumerImpl
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.KafkaException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Properties

/**
 * Generate a Kafka PubSub Consumer Builder.
 */
class PubSubConsumerBuilder<K, V> (private val kafkaConfig: Config, private val consumerProperties: Properties) :
    ConsumerBuilder<K, V> {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override fun createConsumer(subscriptionConfig : SubscriptionConfig): CordaKafkaConsumer<K, V> {
        val topic = subscriptionConfig.eventTopic
        val groupName = subscriptionConfig.groupName
        val consumer = try {
            KafkaConsumer<K, V>(consumerProperties)
        } catch (ex: KafkaException) {
            val message = "PubSubConsumerBuilder failed to create and subscribe consumer for group $groupName, topic $topic."
            log.error(message, ex)
            throw CordaMessageAPIFatalException(message, ex)
        }
        val listener = PubSubConsumerRebalanceListener(subscriptionConfig, consumer)
        return CordaKafkaConsumerImpl(kafkaConfig, subscriptionConfig, consumer, listener)
    }
}
