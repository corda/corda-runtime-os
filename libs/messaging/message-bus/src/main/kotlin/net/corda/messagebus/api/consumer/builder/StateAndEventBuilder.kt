package net.corda.messagebus.api.consumer.builder

import net.corda.messagebus.api.configuration.StateAndEventConfig
import net.corda.messagebus.api.consumer.ConsumerRebalanceListener
import net.corda.messagebus.api.consumer.StateAndEventConsumer
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messaging.api.subscription.listener.StateAndEventListener

interface StateAndEventBuilder {
    fun createProducer(config: StateAndEventConfig): CordaProducer
    fun <K : Any, S : Any, E : Any> createStateEventConsumerAndRebalanceListener(
        config: StateAndEventConfig,
        kClazz: Class<K>,
        sClazz: Class<S>,
        eClazz: Class<E>,
        stateAndEventListener: StateAndEventListener<K, S>? = null
    ): Pair<StateAndEventConsumer<K, S, E>, ConsumerRebalanceListener>
}
