package net.corda.messaging.mediator.factory

import net.corda.messaging.api.mediator.MediatorConsumer
import net.corda.messaging.api.mediator.MediatorInputService
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.config.MediatorConsumerConfig
import net.corda.messaging.api.mediator.config.MessagingClientConfig
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactory
import net.corda.messaging.api.mediator.factory.MessageRouterFactory
import net.corda.messaging.api.mediator.factory.MessagingClientFactory
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.mediator.GroupAllocator
import net.corda.messaging.mediator.MediatorSubscriptionState
import net.corda.messaging.mediator.StateManagerHelper
import net.corda.messaging.mediator.processor.CachingStateManagerWrapper
import net.corda.messaging.mediator.processor.ConsumerProcessor
import net.corda.messaging.mediator.processor.EventProcessor
import net.corda.taskmanager.TaskManager

/**
 * Factory for creating various components used by Multi-Source Event Mediator.
 */
@Suppress("LongParameterList")
class MediatorComponentFactory<K : Any, S : Any, E : Any>(
    private val messageProcessor: StateAndEventProcessor<K, S, E>,
    private val consumerFactories: Collection<MediatorConsumerFactory>,
    private val clientFactories: Collection<MessagingClientFactory>,
    private val messageRouterFactory: MessageRouterFactory,
    private val groupAllocator: GroupAllocator,
    private val stateManagerHelper: StateManagerHelper<S>,
    private val mediatorInputService: MediatorInputService,
) {

    /**
     * Creates message consumers.
     *
     * @param onSerializationError Function for handling serialization errors.
     * @return List of created [MediatorConsumer]s.
     */
    fun createConsumers(
        onSerializationError: (ByteArray) -> Unit
    ): List<MediatorConsumer<K, E>> {
        check(consumerFactories.isNotEmpty()) {
            "No consumer factory set in configuration"
        }
        return consumerFactories.map { consumerFactory ->
            consumerFactory.create(
                MediatorConsumerConfig(
                    messageProcessor.keyClass,
                    messageProcessor.eventValueClass,
                    onSerializationError
                )
            )
        }
    }

    /**
     * Creates messaging clients.
     *
     * @param onSerializationError Function for handling serialization errors.
     * @return List of created [MessagingClient]s.
     */
    fun createClients(
        onSerializationError: (ByteArray) -> Unit
    ): List<MessagingClient> {
        check(clientFactories.isNotEmpty()) {
            "No client factory set in configuration"
        }
        return clientFactories.map { clientFactory ->
            clientFactory.create(
                MessagingClientConfig(onSerializationError)
            )
        }
    }

    /**
     * Creates message router.
     *
     * @param clients Collection of [MessagingClient]s.
     * @return Message router.
     */
    fun createRouter(
        clients: Collection<MessagingClient>
    ): MessageRouter {
        val clientsById = clients.associateBy { it.id }
        return messageRouterFactory.create { id ->
            clientsById[id]
                ?: throw IllegalStateException("Messaging client with ID \"$id\" not found")
        }
    }

    /**
     * Create a processor that will create a consumer and beging processing a topic.
     * Event processing will be delegated to an [EventProcessor].
     * [EventProcessor]s will process groups of flows concurrently via the [taskManager].
     * @param eventMediatorConfig contains details of the mediators config
     * @param taskManager used to launch concurrent tasks
     * @param messageRouter Required by messaging clients to route records to the correct destination
     * @param mediatorSubscriptionState shared state to track the mediators processing status
     * @return A consumer processor
     */
    fun createConsumerProcessor(
        eventMediatorConfig: EventMediatorConfig<K, S, E>,
        taskManager: TaskManager,
        messageRouter: MessageRouter,
        mediatorSubscriptionState: MediatorSubscriptionState,
        stateManagerWrapper: CachingStateManagerWrapper
    ): ConsumerProcessor<K, S, E> {
        val eventProcessor = EventProcessor(eventMediatorConfig, stateManagerHelper, messageRouter, mediatorInputService)
        return ConsumerProcessor(
            eventMediatorConfig,
            groupAllocator,
            taskManager,
            messageRouter,
            mediatorSubscriptionState,
            eventProcessor,
            stateManagerWrapper
        )
    }
}