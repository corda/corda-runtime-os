package net.corda.messaging.subscription.consumer.builder

import net.corda.messagebus.api.consumer.CordaConsumerRebalanceListener
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.messaging.subscription.consumer.StateAndEventConsumer

@Suppress("LongParameterList")
interface StateAndEventBuilder {
    fun createProducer(config: ResolvedSubscriptionConfig): CordaProducer
    fun <K : Any, S : Any, E : Any> createStateEventConsumerAndRebalanceListener(
        config: ResolvedSubscriptionConfig,
        kClazz: Class<K>,
        sClazz: Class<S>,
        eClazz: Class<E>,
        stateAndEventListener: StateAndEventListener<K, S>? = null,
        onStateError: (ByteArray) -> Unit,
        onEventError: (ByteArray) -> Unit,
    ): Pair<StateAndEventConsumer<K, S, E>, CordaConsumerRebalanceListener>
}
