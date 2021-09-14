package net.corda.messaging.kafka.subscription.consumer.builder.impl

import com.typesafe.config.Config
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.kafka.producer.builder.ProducerBuilder
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.properties.KafkaProperties
import net.corda.messaging.kafka.subscription.Topic
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.builder.StateAndEventBuilder
import net.corda.messaging.kafka.subscription.consumer.wrapper.CordaKafkaConsumer
import net.corda.messaging.kafka.subscription.factory.SubscriptionMapFactory
import net.corda.messaging.kafka.subscription.consumer.wrapper.StateAndEventConsumer
import net.corda.messaging.kafka.subscription.consumer.listener.StateAndEventRebalanceListener
import net.corda.messaging.kafka.subscription.consumer.wrapper.StateAndEventPartitionState
import net.corda.messaging.kafka.subscription.consumer.wrapper.impl.StateAndEventConsumerImpl
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.debug
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.slf4j.LoggerFactory
import java.time.Duration

class StateAndEventBuilderImpl<K : Any, S : Any, E : Any>(
    private val stateConsumerBuilder: ConsumerBuilder<K, S>,
    private val eventConsumerBuilder: ConsumerBuilder<K, E>,
    private val producerBuilder: ProducerBuilder,
    private val mapFactory: SubscriptionMapFactory<K, Pair<Long, S>>,
) : StateAndEventBuilder<K, S, E> {

    private val log = LoggerFactory.getLogger(this::class.java)

    companion object {
        private const val STATE_CONSUMER = "stateConsumer"
        private const val EVENT_CONSUMER = "eventConsumer"
        private const val STATE_TOPIC_NAME = "$STATE_CONSUMER.${KafkaProperties.TOPIC_NAME}"
        private val EVENT_CONSUMER_THREAD_STOP_TIMEOUT =
            KafkaProperties.CONSUMER_THREAD_STOP_TIMEOUT.replace("consumer", "eventConsumer")
    }

    override fun createProducer(config: Config): CordaKafkaProducer =
        producerBuilder.createProducer(config.getConfig(KafkaProperties.KAFKA_PRODUCER))

    override fun createStateEventConsumerAndRebalanceListener(
        config: Config,
        kClazz: Class<K>,
        sClazz: Class<S>,
        eClazz: Class<E>,
        stateAndEventListener: StateAndEventListener<K, S>?
    ): Pair<StateAndEventConsumer<K, S, E>, ConsumerRebalanceListener> {
        val stateConsumer = stateConsumerBuilder.createCompactedConsumer(config.getConfig(STATE_CONSUMER), kClazz, sClazz)
        val eventConsumer = eventConsumerBuilder.createDurableConsumer(config.getConfig(EVENT_CONSUMER), kClazz, eClazz)
        validateConsumers(config, stateConsumer, eventConsumer)

        val partitionState = StateAndEventPartitionState(mutableMapOf<Int, MutableMap<K, Pair<Long, S>>>(), mutableMapOf())

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
        config: Config,
        stateConsumer: CordaKafkaConsumer<K, S>,
        eventConsumer: CordaKafkaConsumer<K, E>
    ) {
        val topicPrefix = config.getString(KafkaProperties.TOPIC_PREFIX)
        val eventTopic = Topic(topicPrefix, config.getString(KafkaProperties.TOPIC_NAME))
        val stateTopic = Topic(topicPrefix, config.getString(STATE_TOPIC_NAME))
        val consumerThreadStopTimeout = config.getLong(EVENT_CONSUMER_THREAD_STOP_TIMEOUT)
        val statePartitions = stateConsumer.getPartitions(stateTopic.topic, Duration.ofSeconds(consumerThreadStopTimeout))
        val eventPartitions = eventConsumer.getPartitions(eventTopic.topic, Duration.ofSeconds(consumerThreadStopTimeout))
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
