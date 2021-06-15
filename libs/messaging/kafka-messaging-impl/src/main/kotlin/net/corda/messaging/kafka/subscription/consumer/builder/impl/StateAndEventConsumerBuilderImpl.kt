package net.corda.messaging.kafka.subscription.consumer.builder.impl

import net.corda.messaging.api.subscription.factory.config.StateAndEventSubscriptionConfig
import net.corda.messaging.kafka.subscription.asEventSubscriptionConfig
import net.corda.messaging.kafka.subscription.asStateSubscriptionConfig
import net.corda.messaging.kafka.subscription.consumer.builder.ConsumerBuilder
import net.corda.messaging.kafka.subscription.consumer.builder.StateAndEventConsumerBuilder
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener

class StateAndEventConsumerBuilderImpl<K : Any, S : Any, E : Any>(
    private val stateConsumerBuilder: ConsumerBuilder<K, S>,
    private val eventConsumerBuilder: ConsumerBuilder<K, E>,
    private val subscriptionConfig: StateAndEventSubscriptionConfig,
) : StateAndEventConsumerBuilder<K, S, E> {
    private val stateSubscriptionConfig get() = subscriptionConfig.asStateSubscriptionConfig()
    private val eventSubscriptionConfig get() = subscriptionConfig.asEventSubscriptionConfig()

    override fun createStateConsumer() = stateConsumerBuilder.createCompactedConsumer(stateSubscriptionConfig)
    override fun createEventConsumer(listener: ConsumerRebalanceListener) = eventConsumerBuilder.createDurableConsumer(
        eventSubscriptionConfig,
        consumerRebalanceListener = listener
    )
}
