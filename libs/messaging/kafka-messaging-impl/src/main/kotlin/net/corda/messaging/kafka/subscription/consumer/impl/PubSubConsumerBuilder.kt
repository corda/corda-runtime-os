package net.corda.messaging.kafka.subscription.consumer.impl

import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.subscription.consumer.ConsumerBuilder
import net.corda.messaging.kafka.utils.setKafkaProperties
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.*

/**
 * Generate a Kafka PubSub Consumer Builder.
 */
class PubSubConsumerBuilder<K, V> : ConsumerBuilder<K, V> {

    override fun createConsumer(config: SubscriptionConfig, properties: Map<String, String>): Consumer<K, V> {
        val consumerProps = getConsumerProps(config, properties)
        return KafkaConsumer(consumerProps)
    }

    /**
     * Generate consumer properties with default values unless overridden by the given [config] and [properties]
     */
    private fun getConsumerProps(config: SubscriptionConfig, properties: Map<String, String>): Properties {
        //TODO - get config values from a configService which will be initialized on startup from a compacted log topic
        val consumerProps = Properties()
        consumerProps[ConsumerConfig.GROUP_ID_CONFIG] = config.groupName
        setKafkaProperties(
            consumerProps,
            properties,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer::class.java.name
        )
        setKafkaProperties(
            consumerProps,
            properties,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            ByteArrayDeserializer::class.java.name
        )
        setKafkaProperties(consumerProps, properties, ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,"localhost:9093")
        setKafkaProperties(consumerProps, properties, ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
        setKafkaProperties(consumerProps, properties, ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed")
        setKafkaProperties(consumerProps, properties, ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100)
        setKafkaProperties(consumerProps, properties, ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "15000")
        setKafkaProperties(consumerProps, properties, ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
        return consumerProps
    }
}
