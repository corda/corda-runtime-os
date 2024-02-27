package net.corda.messaging.api.mediator.config

import net.corda.libs.configuration.SmartConfig
import net.corda.libs.statemanager.api.StateManager
import net.corda.messaging.api.mediator.MultiSourceEventMediator
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactory
import net.corda.messaging.api.mediator.factory.MessageRouterFactory
import net.corda.messaging.api.mediator.factory.MessagingClientFactory
import net.corda.messaging.api.processor.StateAndEventProcessor

/**
 * Builder for creating [EventMediatorConfig].
 *
 * @param K Type of event key.
 * @param S Type of event state.
 * @param E Type of event.
 */
class EventMediatorConfigBuilder<K: Any, S: Any, E: Any> {

    private var name : String? = null
    private var messagingConfig : SmartConfig? = null
    private var consumerFactories = emptyArray<MediatorConsumerFactory>()
    private var clientFactories = emptyArray<MessagingClientFactory>()
    private var messageProcessor : StateAndEventProcessor<K, S, E>? = null
    private var messageRouterFactory: MessageRouterFactory? = null
    private var threads: Int? = null
    private var threadName: String? = null
    private var stateManager: StateManager? = null
    private var minGroupSize: Int? = null

    /** Sets name for [MultiSourceEventMediator]. */
    fun name(name: String) =
        apply { this.name = name }

    /** Sets messaging related configuration for [MultiSourceEventMediator]. */
    fun messagingConfig(messagingConfig: SmartConfig) =
        apply { this.messagingConfig = messagingConfig }

    /** Sets factories for creating message consumers. */
    fun consumerFactories(vararg consumerFactories: MediatorConsumerFactory) =
        apply { this.consumerFactories = arrayOf(*consumerFactories) }

    /** Sets factories for creating messaging clients. */
    fun clientFactories(vararg clientFactories: MessagingClientFactory) =
        apply { this.clientFactories = arrayOf(*clientFactories) }

    /** Sets state and event processor for [MultiSourceEventMediator]. */
    fun messageProcessor(messageProcessor: StateAndEventProcessor<K, S, E>) =
        apply { this.messageProcessor = messageProcessor }

    /** Sets message router. */
    fun messageRouterFactory(messageRouterFactory: MessageRouterFactory) =
        apply { this.messageRouterFactory = messageRouterFactory }

    /** Sets number of threads for task manager. */
    fun threads(threads: Int) =
        apply { this.threads = threads }

    /** Sets name preix for task manager threads. */
    fun threadName(threadName: String) =
        apply { this.threadName = threadName }

    /**
     * Sets the minimum size for group of records passed to task manager for processing in a single thread. Does not block if
     * group size is not met by polled record count.
     * If the number of resulting groups is evaluated to be more than the number of threads then the number of [threads] is used to
     * calculate  [minGroupSize] instead.
     */
    fun minGroupSize(minGroupSize: Int) =
        apply { this.minGroupSize = minGroupSize }


    /** Sets state manager. */
    fun stateManager(stateManager: StateManager) =
        apply { this.stateManager = stateManager }

    /** Builds [EventMediatorConfig]. */
    fun build(): EventMediatorConfig<K, S, E> {
        check(consumerFactories.isNotEmpty()) { "At least on consumer factory has to be set" }
        check(clientFactories.isNotEmpty()) { "At least on messaging client factory has to be set" }
        return EventMediatorConfig(
            name = checkNotNull(name) { "Name not set" },
            messagingConfig = checkNotNull(messagingConfig) { "Messaging configuration not set" },
            consumerFactories.asList(),
            clientFactories.asList(),
            messageProcessor = checkNotNull(messageProcessor) { "Message processor not set" },
            messageRouterFactory = checkNotNull(messageRouterFactory) { "Message router factory not set" },
            threads = checkNotNull(threads) { "Number of threads not set" },
            threadName = checkNotNull(threadName) { "Thread name not set" },
            stateManager = checkNotNull(stateManager) { "State manager not set" },
            minGroupSize = checkNotNull(minGroupSize) { "Min group size not set" },
        )
    }
}