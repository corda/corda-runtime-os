package net.corda.messaging.kafka.subscription.consumer.builder

import com.typesafe.config.Config
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener

interface StateAndEventBuilder<K : Any, S : Any, E : Any> {
    fun createProducer(producerConfig: Config): CordaKafkaProducer
    fun createStateConsumer(stateConsumerConfig: Config, kClazz: Class<K>, sClazz: Class<S>): CordaKafkaConsumer<K, S>
    fun createEventConsumer(eventConsumerConfig: Config, kClazz: Class<K>, eClazz: Class<E>, listener: ConsumerRebalanceListener):
            CordaKafkaConsumer<K, E>
}
