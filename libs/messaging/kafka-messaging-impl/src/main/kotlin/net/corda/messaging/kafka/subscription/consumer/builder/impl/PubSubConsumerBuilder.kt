package net.corda.messaging.kafka.subscription.consumer.builder.impl

import com.typesafe.config.Config
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.consumer.listener.PubSubConsumerRebalanceListener
import net.corda.messaging.kafka.subscription.consumer.wrapper.impl.CordaKafkaConsumerImpl
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.util.Properties

/**
 * Generate a Kafka PubSub Consumer Builder.
 */
class PubSubConsumerBuilder<K, V> (private val kafkaConfig: Config, private val consumerProperties: Properties) :
    ConsumerBuilder<K, V> {

    override fun createConsumerAndSubscribe(subscriptionConfig : SubscriptionConfig): CordaKafkaConsumer<K, V> {
        val consumer = KafkaConsumer<K, V>(consumerProperties)
        val listener = PubSubConsumerRebalanceListener(consumer)
        val cordaKafkaConsumer = CordaKafkaConsumerImpl(kafkaConfig, subscriptionConfig, consumer, listener)
        cordaKafkaConsumer.subscribeToTopic()
        return cordaKafkaConsumer
    }
}
