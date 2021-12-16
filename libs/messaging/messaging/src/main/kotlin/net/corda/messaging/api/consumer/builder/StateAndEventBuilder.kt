package net.corda.messaging.api.consumer.builder

import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messaging.api.consumer.StateAndEventConsumer
import net.corda.messaging.api.subscription.config.StateAndEventConfig
import net.corda.messaging.api.subscription.listener.StateAndEventListener

interface StateAndEventBuilder {
    fun createProducer(config: StateAndEventConfig): CordaProducer
    fun <K : Any, S : Any, E : Any> createStateEventConsumerAndRebalanceListener(
        config: StateAndEventConfig,
        kClazz: Class<K>,
        sClazz: Class<S>,
        eClazz: Class<E>,
        stateAndEventListener: StateAndEventListener<K, S>? = null,
    ): Pair<StateAndEventConsumer<K, S, E>, CordaConsumerRebalanceListener>
}
