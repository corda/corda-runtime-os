package net.corda.messaging.subscription.consumer.builder

import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.consumer.StateAndEventConsumer
import net.corda.messaging.api.consumer.builder.CordaConsumerBuilder
import net.corda.messaging.api.consumer.builder.StateAndEventBuilder
import net.corda.messaging.api.subscription.config.StateAndEventConfig
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.subscription.consumer.StateAndEventConsumerImpl
import net.corda.messaging.subscription.consumer.StateAndEventPartitionState
import net.corda.messaging.subscription.consumer.listener.StateAndEventRebalanceListener
import net.corda.messaging.subscription.factory.MapFactory
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.util.debug
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.time.Duration

@Component(service = [StateAndEventBuilder::class])
class StateAndEventBuilderImpl @Activate constructor(
    @Reference(service = CordaConsumerBuilder::class)
    private val cordaConsumerBuilder: CordaConsumerBuilder,
    @Reference(service = CordaProducerBuilder::class)
    private val cordaProducerBuilder: CordaProducerBuilder,
) : StateAndEventBuilder {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun createProducer(config: StateAndEventConfig): CordaProducer =
        cordaProducerBuilder.createProducer(config.producerConfig)

    override fun <K : Any, S : Any, E : Any> createStateEventConsumerAndRebalanceListener(
        config: StateAndEventConfig,
        kClazz: Class<K>,
        sClazz: Class<S>,
        eClazz: Class<E>,
        stateAndEventListener: StateAndEventListener<K, S>?
    ): Pair<StateAndEventConsumer<K, S, E>, CordaConsumerRebalanceListener> {
        val stateConsumer = cordaConsumerBuilder.createCompactedConsumer(config.stateConsumerConfig, kClazz, sClazz)
        val eventConsumer = cordaConsumerBuilder.createDurableConsumer(config.eventConsumerConfig, kClazz, eClazz)
        validateConsumers(config, stateConsumer, eventConsumer)

        val partitionState =
            StateAndEventPartitionState(
                mutableMapOf<Int, MutableMap<K, Pair<Long, S>>>(),
                mutableMapOf()
            )

        val mapFactory = object : MapFactory<K, Pair<Long, S>> {
            override fun createMap(): MutableMap<K, Pair<Long, S>> = createMap()
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
        config: StateAndEventConfig,
        stateConsumer: CordaConsumer<K, S>,
        eventConsumer: CordaConsumer<K, E>
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
