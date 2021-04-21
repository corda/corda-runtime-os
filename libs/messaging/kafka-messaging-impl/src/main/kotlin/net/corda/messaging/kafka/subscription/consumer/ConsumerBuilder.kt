package net.corda.messaging.kafka.subscription.consumer

import com.typesafe.config.Config
import org.apache.kafka.clients.consumer.Consumer

/**
 * Builder for creating Consumers.
 */
interface ConsumerBuilder<K, V> {

    /**
     * Generate a Kafka Consumer using the  [defaultConfig] overriden by any [overrideProperties].
     */
    fun createConsumer(defaultConfig: Config, overrideProperties: Map<String, String>): Consumer<K, V>
}
