package net.corda.messaging.subscription.consumer.builder

import net.corda.messagebus.api.configuration.ConsumerConfig
import net.corda.messagebus.api.configuration.ProducerConfig
import net.corda.messagebus.api.constants.ConsumerRoles
import net.corda.messagebus.api.constants.ProducerRoles
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.consumer.builder.CordaConsumerBuilder
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.messaging.subscription.consumer.StateAndEventConsumer
import net.corda.messaging.subscription.consumer.StateAndEventConsumerImpl
import net.corda.messaging.subscription.consumer.StateAndEventPartitionState
import net.corda.messaging.subscription.consumer.listener.StateAndEventRebalanceListener
import net.corda.messaging.subscription.factory.MapFactory
import net.corda.schema.Schemas.Companion.getStateAndEventStateTopic
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

@Component(service = [StateAndEventBuilder::class])
internal class StateAndEventBuilderImpl @Activate constructor(
    @Reference(service = CordaConsumerBuilder::class)
    private val cordaConsumerBuilder: CordaConsumerBuilder,
    @Reference(service = CordaProducerBuilder::class)
    private val cordaProducerBuilder: CordaProducerBuilder,
) : StateAndEventBuilder {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun createProducer(config: ResolvedSubscriptionConfig): CordaProducer {
        val producerConfig = ProducerConfig(config.clientId, config.instanceId, true, ProducerRoles.SAE_PRODUCER)
        return cordaProducerBuilder.createProducer(producerConfig, config.messageBusConfig)
    }

    override fun <K : Any, S : Any, E : Any> createStateEventConsumerAndRebalanceListener(
        config: ResolvedSubscriptionConfig,
        kClazz: Class<K>,
        sClazz: Class<S>,
        eClazz: Class<E>,
        stateAndEventListener: StateAndEventListener<K, S>?,
        onStateError: (ByteArray) -> Unit,
        onEventError: (ByteArray) -> Unit,
    ): Pair<StateAndEventConsumer<K, S, E>, CordaConsumerRebalanceListener> {
        val stateConsumerConfig = ConsumerConfig(config.group, config.clientId, ConsumerRoles.SAE_STATE)
        val stateConsumer = cordaConsumerBuilder.createConsumer(stateConsumerConfig, config.messageBusConfig, kClazz, sClazz, onStateError)
        val eventConsumerConfig = ConsumerConfig(config.group, config.clientId, ConsumerRoles.SAE_EVENT)
        val eventConsumer = cordaConsumerBuilder.createConsumer(eventConsumerConfig, config.messageBusConfig, kClazz, eClazz, onEventError)
        validateConsumers(config, stateConsumer, eventConsumer)

        val partitionState =
            StateAndEventPartitionState(
                mutableMapOf<Int, MutableMap<K, Pair<Long, S>>>(),
                mutableMapOf()
            )

        val mapFactory = object : MapFactory<K, Pair<Long, S>> {
            override fun createMap(): MutableMap<K, Pair<Long, S>> = ConcurrentHashMap()
            override fun destroyMap(map: MutableMap<K, Pair<Long, S>>) = map.clear()
        }

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

    private fun <K : Any, S : Any, E : Any> validateConsumers(
        config: ResolvedSubscriptionConfig,
        stateConsumer: CordaConsumer<K, S>,
        eventConsumer: CordaConsumer<K, E>
    ) {
        val consumerTimeout = config.pollTimeout
        val statePartitions =
            stateConsumer.getPartitions(getStateAndEventStateTopic(config.topic), consumerTimeout)
        val eventPartitions =
            eventConsumer.getPartitions(config.topic, consumerTimeout)
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
