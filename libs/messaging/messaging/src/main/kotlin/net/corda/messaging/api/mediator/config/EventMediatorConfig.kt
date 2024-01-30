package net.corda.messaging.api.mediator.config

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.mediator.MultiSourceEventMediator
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactory
import net.corda.messaging.api.mediator.factory.MessageRouterFactory
import net.corda.messaging.api.mediator.factory.MessagingClientFactory
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.schema.configuration.MessagingConfig.Subscription.MEDIATOR_PROCESSING_POLL_TIMEOUT
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
 * @property threads Number of threads used by task manager.
 * @property threadName Name (prefix) for task manager threads.
 * @property stateManager State manager.
 * @property minGroupSize Minimum size for group of records passed to task manager for processing in a single thread. Does not block if
 * group size is not met by polled record count.
 */
data class EventMediatorConfig<K: Any, S: Any, E: Any>(
    val name: String,
    val messagingConfig: SmartConfig,
    val consumerFactories: Collection<MediatorConsumerFactory>,
    val clientFactories: Collection<MessagingClientFactory>,
    val messageProcessor: StateAndEventProcessor<K, S, E>,
    val messageRouterFactory: MessageRouterFactory,
    val threads: Int,
    val threadName: String,
    val stateManager: StateManager,
    val minGroupSize: Int,
) {
    /**
     * Timeout for polling consumers.
     */
    val pollTimeout: Duration
        get() = Duration.ofMillis(messagingConfig.getLong(MEDIATOR_PROCESSING_POLL_TIMEOUT))
}