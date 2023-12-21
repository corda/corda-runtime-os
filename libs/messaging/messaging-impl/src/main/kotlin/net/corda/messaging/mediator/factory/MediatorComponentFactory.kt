package net.corda.messaging.mediator.factory

import net.corda.messaging.api.mediator.MediatorConsumer
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.MessagingClient
import net.corda.messaging.api.mediator.config.EventMediatorConfig
import net.corda.messaging.api.mediator.config.MediatorConsumerConfig
import net.corda.messaging.api.mediator.config.MessagingClientConfig
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactory
import net.corda.messaging.api.mediator.factory.MessageRouterFactory
import net.corda.messaging.api.mediator.factory.MessagingClientFactory
import net.corda.messaging.api.processor.StateAndEventProcessor
import net.corda.messaging.mediator.ConsumerProcessorState
import net.corda.messaging.mediator.GroupAllocator
import net.corda.messaging.mediator.MediatorState
import net.corda.messaging.mediator.StateManagerHelper
import net.corda.messaging.mediator.processor.ConsumerProcessor
import net.corda.messaging.mediator.processor.EventProcessor
import net.corda.taskmanager.TaskManager
import java.util.concurrent.atomic.AtomicBoolean

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
    private val stateManagerHelper: StateManagerHelper<K, S, E>
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

    fun createConsumerProcessor(
        eventMediatorConfig: EventMediatorConfig<K, S, E>,
        taskManager: TaskManager,
        messageRouter: MessageRouter,
        mediatorState: MediatorState,
    ): ConsumerProcessor<K, S, E> {
        val consumerProcessorState = ConsumerProcessorState()
        val eventProcessor = EventProcessor(eventMediatorConfig, stateManagerHelper, messageRouter, consumerProcessorState)
        return ConsumerProcessor(eventMediatorConfig, groupAllocator, taskManager, messageRouter, mediatorState, consumerProcessorState,
            eventProcessor)
    }

    fun createMediatorState(): MediatorState {
        return MediatorState(
            AtomicBoolean(false),
            AtomicBoolean(false)
        )
    }
}