package net.corda.messaging.kafka.subscription.consumer.builder

import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener

interface StateAndEventConsumerBuilder<K: Any, S : Any, E : Any> {
    fun createStateConsumer(): CordaKafkaConsumer<K, S>
    fun createEventConsumer(listener: ConsumerRebalanceListener): CordaKafkaConsumer<K, E>
}
