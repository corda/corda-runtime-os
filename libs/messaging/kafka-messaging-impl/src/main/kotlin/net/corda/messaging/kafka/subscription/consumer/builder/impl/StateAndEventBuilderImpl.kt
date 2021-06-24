package net.corda.messaging.kafka.subscription.consumer.builder.impl

import com.typesafe.config.Config
import net.corda.messaging.kafka.producer.builder.ProducerBuilder
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.builder.StateAndEventBuilder
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener

class StateAndEventBuilderImpl<K : Any, S : Any, E : Any>(
    private val stateConsumerBuilder: ConsumerBuilder<K, S>,
    private val eventConsumerBuilder: ConsumerBuilder<K, E>,
    private val producerBuilder: ProducerBuilder,
    ) : StateAndEventBuilder<K, S, E> {

    override fun createProducer(producerConfig: Config): CordaKafkaProducer =
        producerBuilder.createProducer(producerConfig)
    override fun createStateConsumer(stateConsumerConfig: Config) =
        stateConsumerBuilder.createCompactedConsumer(stateConsumerConfig)
    override fun createEventConsumer(eventConsumerConfig: Config, listener: ConsumerRebalanceListener) =
        eventConsumerBuilder.createDurableConsumer(
            eventConsumerConfig,
            consumerRebalanceListener = listener
        )
}
