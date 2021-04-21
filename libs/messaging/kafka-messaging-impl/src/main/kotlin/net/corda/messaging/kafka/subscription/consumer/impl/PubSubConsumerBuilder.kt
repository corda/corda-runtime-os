package net.corda.messaging.kafka.subscription.consumer.impl

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.messaging.kafka.subscription.consumer.ConsumerBuilder
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.util.*


/**
 * Generate a Kafka PubSub Consumer Builder.
 */
class PubSubConsumerBuilder<K, V> : ConsumerBuilder<K, V> {

    override fun createConsumer(defaultConfig: Config, overrideProperties: Map<String, String>): Consumer<K, V> {
        val consumerProps = getConsumerProps(defaultConfig, overrideProperties)
        return KafkaConsumer(consumerProps)
    }

    /**
     * Generate consumer properties with default values from [defaultConfig] unless overridden by the given [overrideProperties].
     * @return Kafka Consumer properties
     */
    private fun getConsumerProps(defaultConfig: Config, overrideProperties: Map<String, String>): Properties {
        val properties = Properties()
        properties.putAll(overrideProperties)
        val conf: Config = ConfigFactory.parseProperties(properties).withFallback(defaultConfig)
        val consumerProps = Properties()

        //Could do something smarter here like
        //Store all kafka consumer props in typesafeConf as "kafka.consumer.props"
        //read all values from conf with a prefix of "kafka.consumer.props"
        //or store all consumer defaults in their own typesafeconfig
        consumerProps[ConsumerConfig.GROUP_ID_CONFIG] = conf.getString(ConsumerConfig.GROUP_ID_CONFIG)
        consumerProps[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = conf.getString(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG)
        consumerProps[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = conf.getString(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG)
        consumerProps[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = conf.getString(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG)
        consumerProps[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = conf.getString(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG)
        consumerProps[ConsumerConfig.ISOLATION_LEVEL_CONFIG] = conf.getString(ConsumerConfig.ISOLATION_LEVEL_CONFIG)
        consumerProps[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = conf.getString(ConsumerConfig.MAX_POLL_RECORDS_CONFIG)
        consumerProps[ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG] = conf.getString(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG)
        consumerProps[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = conf.getString(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG)
        return consumerProps
    }
}
