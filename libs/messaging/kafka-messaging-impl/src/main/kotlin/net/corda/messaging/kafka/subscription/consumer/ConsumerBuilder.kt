package net.corda.messaging.kafka.subscription.consumer

import org.apache.kafka.clients.consumer.Consumer
import java.util.Properties

/**
 * Builder for creating Consumers.
 */
interface ConsumerBuilder<K, V> {

    /**
     * Generate a Kafka Consumer using the given [properties].
     */
    fun createConsumer(properties: Properties): Consumer<K, V>
}
