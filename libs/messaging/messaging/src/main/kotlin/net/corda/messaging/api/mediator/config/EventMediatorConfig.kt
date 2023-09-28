package net.corda.messaging.api.mediator.config

import net.corda.libs.configuration.SmartConfig
import net.corda.messaging.api.mediator.MultiSourceEventMediator
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactory
import net.corda.messaging.api.mediator.factory.MessageRouterFactory
import net.corda.messaging.api.mediator.factory.MessagingClientFactory
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.schema.configuration.MessagingConfig
import java.time.Duration

/**
 * Class to store configuration required to create a [MultiSourceEventMediator].
 *
 * @param K Type of event key.
 * @param S Type of event state.
 * @param E Type of event.
 * @property name The unique name for a multi-source event mediator.
 * @property messagingConfig Messaging related configuration.
 * @property consumerFactories Factories for creating message consumers.
 * @property clientFactories Factories for creating messaging clients.
 * @property messageProcessor State and event processor.
 * @property messageRouterFactory Message router factory.
 */
data class EventMediatorConfig<K: Any, S: Any, E: Any>(
    val name: String,
    val messagingConfig : SmartConfig,
    val consumerFactories: Collection<MediatorConsumerFactory>,
    val clientFactories: Collection<MessagingClientFactory>,
    val messageProcessor : StateAndEventProcessor<K, S, E>,
    val messageRouterFactory: MessageRouterFactory,
) {
    /**
     * Timeout for polling consumers.
     */
    val pollTimeout: Duration
        get() = Duration.ofMillis(messagingConfig.getLong(MessagingConfig.Subscription.POLL_TIMEOUT))

    /**
     * Maximal number of event processing retries.
     */
    val processorRetries: Int
        get() = messagingConfig.getInt(MessagingConfig.Subscription.PROCESSOR_RETRIES)
}