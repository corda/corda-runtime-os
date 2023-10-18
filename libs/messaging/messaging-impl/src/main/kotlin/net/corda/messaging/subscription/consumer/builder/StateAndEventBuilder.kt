package net.corda.messaging.subscription.consumer.builder

import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messaging.api.subscription.listener.StateAndEventListener
import net.corda.messaging.config.ResolvedSubscriptionConfig
import net.corda.messaging.subscription.consumer.StateAndEventConsumer
import net.corda.messaging.subscription.consumer.listener.StateAndEventConsumerRebalanceListener

@Suppress("LongParameterList")
interface StateAndEventBuilder {
    /**
     * Create producer
     *
     * @param config subscription configuration for the messaging layer.
     * @param onSerializationError a lambda to run on serialization error, will run regardless of throwOnSerializationError
     * @return
     */
    fun createProducer(
        config: ResolvedSubscriptionConfig,
        onSerializationError: ((ByteArray) -> Unit)? = null
    ): CordaProducer

    fun <K : Any, S : Any, E : Any> createStateEventConsumerAndRebalanceListener(
        config: ResolvedSubscriptionConfig,
        kClazz: Class<K>,
        sClazz: Class<S>,
        eClazz: Class<E>,
        stateAndEventListener: StateAndEventListener<K, S>? = null,
        onStateError: (ByteArray, String?) -> Unit,
        onEventError: (ByteArray, String?) -> Unit,
    ): Pair<StateAndEventConsumer<K, S, E>, StateAndEventConsumerRebalanceListener>
}
