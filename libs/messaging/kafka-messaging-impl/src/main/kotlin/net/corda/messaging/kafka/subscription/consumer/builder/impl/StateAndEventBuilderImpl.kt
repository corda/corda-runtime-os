package net.corda.messaging.kafka.subscription.consumer.builder.impl

import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.kafka.producer.builder.ProducerBuilder
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.builder.StateAndEventBuilder
import net.corda.messaging.kafka.subscription.consumer.listener.StateAndEventRebalanceListener
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.consumer.wrapper.StateAndEventConsumer
import net.corda.messaging.kafka.subscription.consumer.wrapper.StateAndEventPartitionState
import net.corda.messaging.kafka.subscription.consumer.wrapper.impl.StateAndEventConsumerImpl
import net.corda.messaging.kafka.subscription.factory.SubscriptionMapFactory
import net.corda.messaging.kafka.types.StateAndEventConfig
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.debug
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.slf4j.LoggerFactory
import java.time.Duration

class StateAndEventBuilderImpl<K : Any, S : Any, E : Any>(
    private val stateConsumerBuilder: ConsumerBuilder<K, S>,
    private val eventConsumerBuilder: ConsumerBuilder<K, E>,
    private val producerBuilder: ProducerBuilder,
    private val mapFactory: SubscriptionMapFactory<K, Pair<Long, S>>
) : StateAndEventBuilder<K, S, E> {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun createProducer(config: StateAndEventConfig): CordaKafkaProducer =
        producerBuilder.createProducer(config.producerConfig)

    override fun createStateEventConsumerAndRebalanceListener(
        config: StateAndEventConfig,
        kClazz: Class<K>,
        sClazz: Class<S>,
        eClazz: Class<E>,
        stateAndEventListener: StateAndEventListener<K, S>?,
        onStateError: (String, ByteArray) -> Unit,
        onEventError: (String, ByteArray) -> Unit
    ): Pair<StateAndEventConsumer<K, S, E>, ConsumerRebalanceListener> {
        val stateConsumer =
            stateConsumerBuilder.createCompactedConsumer(config.stateConsumerConfig, kClazz, sClazz, onStateError)
        val eventConsumer =
            eventConsumerBuilder.createDurableConsumer(config.eventConsumerConfig, kClazz, eClazz, onEventError)
        validateConsumers(config, stateConsumer, eventConsumer)

        val partitionState =
            StateAndEventPartitionState(mutableMapOf<Int, MutableMap<K, Pair<Long, S>>>(), mutableMapOf())

        val stateAndEventConsumer =
            StateAndEventConsumerImpl(config, eventConsumer, stateConsumer, partitionState, stateAndEventListener)
        val rebalanceListener = StateAndEventRebalanceListener(
            config,
            mapFactory,
            stateAndEventConsumer,
            partitionState,
            stateAndEventListener
        )
        return Pair(stateAndEventConsumer, rebalanceListener)
    }

    private fun validateConsumers(
        config: StateAndEventConfig,
        stateConsumer: CordaKafkaConsumer<K, S>,
        eventConsumer: CordaKafkaConsumer<K, E>
    ) {
        val consumerThreadStopTimeout = config.consumerThreadStopTimeout
        val statePartitions =
            stateConsumer.getPartitions(config.stateTopic, Duration.ofSeconds(consumerThreadStopTimeout))
        val eventPartitions =
            eventConsumer.getPartitions(config.eventTopic, Duration.ofSeconds(consumerThreadStopTimeout))
        if (statePartitions.size != eventPartitions.size) {
            val errorMsg = "Mismatch between state and event partitions."
            log.debug {
                errorMsg + "\n" +
                        "state: ${statePartitions.joinToString()}\n" +
                        "event: ${eventPartitions.joinToString()}"
            }
            throw CordaRuntimeException(errorMsg)
        }
    }
}
