package net.corda.messaging.api.mediator.config

import net.corda.libs.configuration.SmartConfig
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

    /** Builds [EventMediatorConfig]. */
    fun build(): EventMediatorConfig<K, S, E> {
        check(name != null) { "Name not set" }
        check(messagingConfig != null) { "Messaging configuration not set" }
        check(consumerFactories.isNotEmpty()) { "At least on consumer factory has to be set" }
        check(clientFactories.isNotEmpty()) { "At least on messaging client factory has to be set" }
        check(messageProcessor != null) { "Message processor not set" }
        check(messageRouterFactory != null) { "Message router factory not set" }
        return EventMediatorConfig(
            name!!,
            messagingConfig!!,
            consumerFactories.asList(),
            clientFactories.asList(),
            messageProcessor!!,
            messageRouterFactory!!
        )
    }
}