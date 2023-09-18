package net.corda.messaging.mediator

import net.corda.messaging.api.mediator.MediatorConsumer
import net.corda.messaging.api.mediator.MediatorProducer
import net.corda.messaging.api.mediator.MessageRouter
import net.corda.messaging.api.mediator.config.MediatorConsumerConfig
import net.corda.messaging.api.mediator.config.MediatorProducerConfig
import net.corda.messaging.api.mediator.factory.MediatorConsumerFactory
import net.corda.messaging.api.mediator.factory.MediatorProducerFactory
import net.corda.messaging.api.mediator.factory.MessageRouterFactory
import net.corda.messaging.api.processor.StateAndEventProcessor

/**
 * Factory for creating various components used by [MultiSourceEventMediatorImpl]
 */
internal class MediatorComponentFactory<K : Any, S : Any, E : Any>(
    private val messageProcessor: StateAndEventProcessor<K, S, E>,
    private val consumerFactories: Collection<MediatorConsumerFactory>,
    private val producerFactories: Collection<MediatorProducerFactory>,
    private val messageRouterFactory: MessageRouterFactory,
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
        check (consumerFactories.isNotEmpty()) {
            "None consumer factory set in configuration"
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
     * Creates message producers.
     *
     * @param onSerializationError Function for handling serialization errors.
     * @return List of created [MediatorProducer]s.
     */
    fun createProducers(
        onSerializationError: (ByteArray) -> Unit
    ): List<MediatorProducer> {
        check (producerFactories.isNotEmpty()) {
            "None producer factory set in configuration"
        }
        return producerFactories.map { producerFactory ->
            producerFactory.create(
                MediatorProducerConfig(onSerializationError)
            )
        }
    }

    /**
     * Creates message router.
     *
     * @param producers Collection of [MediatorProducer]s.
     * @return Message router.
     */
    fun createRouter(
        producers: Collection<MediatorProducer>
    ): MessageRouter {
        val producersByName = producers.associateBy { it.name }
        return messageRouterFactory.create { name ->
            producersByName[name]
                ?: throw IllegalStateException("Producer with name \"$name\" not found")
        }
    }
}