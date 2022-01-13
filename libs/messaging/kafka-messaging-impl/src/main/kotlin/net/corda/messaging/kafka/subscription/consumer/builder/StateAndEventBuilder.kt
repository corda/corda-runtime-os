package net.corda.messaging.kafka.subscription.consumer.builder

import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.kafka.producer.wrapper.CordaKafkaProducer
import net.corda.messaging.kafka.subscription.consumer.wrapper.StateAndEventConsumer
import net.corda.messaging.kafka.types.StateAndEventConfig
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener

@Suppress("LongParameterList")
interface StateAndEventBuilder<K : Any, S : Any, E : Any> {
    fun createProducer(config: StateAndEventConfig): CordaKafkaProducer
    fun createStateEventConsumerAndRebalanceListener(
        config: StateAndEventConfig,
        kClazz: Class<K>,
        sClazz: Class<S>,
        eClazz: Class<E>,
        stateAndEventListener: StateAndEventListener<K, S>? = null,
        onStateError: (String, ByteArray) -> Unit,
        onEventError: (String, ByteArray) -> Unit
    ): Pair<StateAndEventConsumer<K, S,E>, ConsumerRebalanceListener>
}
