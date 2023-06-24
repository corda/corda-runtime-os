package net.corda.messaging.subscription.consumer.builder

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.messagebus.api.configuration.ConsumerConfig
import net.corda.messagebus.api.configuration.ProducerConfig
import net.corda.messagebus.api.constants.ConsumerRoles
import net.corda.messagebus.api.constants.ProducerRoles
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messagebus.api.consumer.NoOpCordaConsumer
import net.corda.messagebus.api.consumer.builder.CordaConsumerBuilder
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.builder.CordaProducerBuilder
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.messaging.subscription.consumer.RedisStateAndEventConsumer
import net.corda.messaging.subscription.consumer.StateAndEventConsumer
import net.corda.messaging.subscription.consumer.StateAndEventPartitionState
import net.corda.messaging.subscription.consumer.listener.StateAndEventConsumerRebalanceListener
import net.corda.messaging.subscription.consumer.listener.StateAndEventConsumerRebalanceListenerImpl
import net.corda.messaging.subscription.factory.MapFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

@Component(service = [StateAndEventBuilder::class])
class StateAndEventBuilderImpl @Activate constructor(
    @Reference(service = CordaConsumerBuilder::class)
    private val cordaConsumerBuilder: CordaConsumerBuilder,
    @Reference(service = CordaProducerBuilder::class)
    private val cordaProducerBuilder: CordaProducerBuilder,
) : StateAndEventBuilder {

    private val log = LoggerFactory.getLogger(this::class.java)

    override fun createProducer(
        config: ResolvedSubscriptionConfig,
        onSerializationError: ((ByteArray) -> Unit)?
    ): CordaProducer {
        val producerConfig = ProducerConfig(config.clientId, config.instanceId, true, ProducerRoles.SAE_PRODUCER, false)
        return cordaProducerBuilder.createProducer(
            producerConfig,
            config.messageBusConfig,
            onSerializationError
        )
    }

    override fun <K : Any, S : Any, E : Any> createStateEventConsumerAndRebalanceListener(
        config: ResolvedSubscriptionConfig,
        kClazz: Class<K>,
        sClazz: Class<S>,
        eClazz: Class<E>,
        stateAndEventListener: StateAndEventListener<K, S>?,
        onStateError: (ByteArray) -> Unit,
        onEventError: (ByteArray) -> Unit,
        serializer: CordaAvroSerializer<Any>,
        deserializer: CordaAvroDeserializer<Any>
    ): Pair<StateAndEventConsumer<K, S, E>, StateAndEventConsumerRebalanceListener> {
        val eventConsumerConfig =
            ConsumerConfig(config.group, "${config.clientId}-eventConsumer", ConsumerRoles.SAE_EVENT)
        val eventConsumer = cordaConsumerBuilder.createConsumer(
            eventConsumerConfig,
            config.messageBusConfig,
            kClazz,
            eClazz,
            onEventError
        )

        val partitionState =
            StateAndEventPartitionState(
                mutableMapOf<Int, MutableMap<K, Pair<Long, S>>>()
            )

        val mapFactory = object : MapFactory<K, Pair<Long, S>> {
            override fun createMap(): MutableMap<K, Pair<Long, S>> = ConcurrentHashMap()
            override fun destroyMap(map: MutableMap<K, Pair<Long, S>>) = map.clear()
        }

        val stateAndEventConsumer =
            RedisStateAndEventConsumer(config, eventConsumer, NoOpCordaConsumer(), partitionState, stateAndEventListener, serializer, deserializer)
        val rebalanceListener = StateAndEventConsumerRebalanceListenerImpl(
            config,
            mapFactory,
            stateAndEventConsumer,
            partitionState,
            stateAndEventListener
        )
        return Pair(stateAndEventConsumer, rebalanceListener)
    }
}
