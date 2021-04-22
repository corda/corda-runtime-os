package net.corda.messaging.kafka.subscription.factory

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.api.processor.DurableProcessor
import net.corda.messaging.api.processor.PubSubProcessor
import net.corda.messaging.api.subscription.StateAndEventSubscription
import net.corda.messaging.api.subscription.Subscription
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.kafka.subscription.subscriptions.pubsub.KafkaPubSubSubscription
import net.corda.messaging.api.subscription.factory.config.StateAndEventSubscriptionConfig
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_CONF_PREFIX
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_POLL_TIMEOUT
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.CONSUMER_THREAD_STOP_TIMEOUT
import net.corda.messaging.kafka.properties.KafkaProperties.Companion.MAX_RETRIES_CONFIG
import net.corda.messaging.kafka.subscription.consumer.impl.PubSubConsumerBuilder
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.osgi.service.component.annotations.Component
import java.util.Properties
import java.util.concurrent.ExecutorService

/**
 * Kafka implementation of the Subscription Factory.
 */
@Component
class KafkaSubscriptionFactory : SubscriptionFactory {

    companion object {
        private const val ISOLATION_LEVEL_READ_COMMITTED = "read_committed"
        private const val AUTO_OFFSET_RESET_LATEST = "latest"
        private const val FALSE = "false"
    }

    override fun <K, V> createPubSubSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: PubSubProcessor<K, V>,
        executor: ExecutorService?,
        properties: Map<String, String>
    ): Subscription<K, V> {

        //pattern specific properties
        val overrideProperties = properties.toMutableMap()
        overrideProperties[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = FALSE
        overrideProperties[ConsumerConfig.ISOLATION_LEVEL_CONFIG] = ISOLATION_LEVEL_READ_COMMITTED
        overrideProperties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = AUTO_OFFSET_RESET_LATEST

        //TODO - replace this with a  call to OSGi ConfigService, possibly multiple configs required
        val defaultKafkaConfig = ConfigFactory.load("tmpKafkaDefaults")

        val consumerProperties = getConsumerProps(subscriptionConfig, defaultKafkaConfig, overrideProperties)
        return KafkaPubSubSubscription(subscriptionConfig, consumerProperties, PubSubConsumerBuilder(), processor, executor)
    }

    override fun <K, V> createDurableSubscription(
        subscriptionConfig: SubscriptionConfig,
        processor: DurableProcessor<K, V>,
        properties: Map<String, String>
    ): Subscription<K, V> {
        TODO("Not yet implemented")
    }

    override fun <K, S, E> createStateAndEventSubscription(
        subscriptionConfig: StateAndEventSubscriptionConfig,
        processor: StateAndEventProcessor<K, S, E>,
        properties: Map<String, String>
    ): StateAndEventSubscription<K, S, E> {
        TODO("Not yet implemented")
    }

    /**
     * Generate consumer properties with default values from [subscriptionConfig] and [defaultKafkaConfig]
     * unless overridden by the given [overrideProperties].
     * @return Kafka Consumer properties
     */
    private fun getConsumerProps(subscriptionConfig: SubscriptionConfig, defaultKafkaConfig: Config,
                                 overrideProperties: Map<String, String>): Properties {
        val properties = Properties()
        properties.putAll(overrideProperties)
        val conf: Config = ConfigFactory.parseProperties(properties).withFallback(defaultKafkaConfig)
        val consumerProps = Properties()
        

        consumerProps[ConsumerConfig.GROUP_ID_CONFIG] = subscriptionConfig.groupName
        consumerProps[MAX_RETRIES_CONFIG] = conf.getInt(MAX_RETRIES_CONFIG)
        consumerProps[CONSUMER_POLL_TIMEOUT] = conf.getLong(CONSUMER_POLL_TIMEOUT)
        consumerProps[CONSUMER_THREAD_STOP_TIMEOUT] = conf.getLong(CONSUMER_THREAD_STOP_TIMEOUT)

        //Could do something smarter here like
        //Store all kafka consumer props in typesafeConf as "kafka.consumer.props."
        //read all values from conf with a prefix of "kafka.consumer.props"
        //or store all consumer defaults in their own typesafeconfig
        consumerProps[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] =
            conf.getString(CONSUMER_CONF_PREFIX + ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG)
        consumerProps[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] =
            conf.getString(CONSUMER_CONF_PREFIX + ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG)
        consumerProps[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] =
            conf.getString(CONSUMER_CONF_PREFIX + ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG)
        consumerProps[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] =
            conf.getString(CONSUMER_CONF_PREFIX + ConsumerConfig.MAX_POLL_RECORDS_CONFIG)
        consumerProps[ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG] =
            conf.getString(CONSUMER_CONF_PREFIX + ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG)
        return consumerProps
    }
}
