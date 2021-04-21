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

    override fun <K, V> createPubSubSubscription(
        config: SubscriptionConfig,
        processor: PubSubProcessor<K, V>,
        executor: ExecutorService?,
        properties: Map<String, String>
    ): Subscription<K, V> {
        //TODO - replace this with a  call to OSGi ConfigService
        val defaultConfig = ConfigFactory.load()

        val consumerProperties = getConsumerProps(defaultConfig, properties)

        return KafkaPubSubSubscription(config, consumerProperties, PubSubConsumerBuilder(), processor, executor)
    }

    override fun <K, V> createDurableSubscription(
        config: SubscriptionConfig,
        processor: DurableProcessor<K, V>,
        properties: Map<String, String>
    ): Subscription<K, V> {
        TODO("Not yet implemented")
    }

    override fun <K, S, E> createStateAndEventSubscription(
        config: StateAndEventSubscriptionConfig,
        processor: StateAndEventProcessor<K, S, E>,
        properties: Map<String, String>
    ): StateAndEventSubscription<K, S, E> {
        TODO("Not yet implemented")
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